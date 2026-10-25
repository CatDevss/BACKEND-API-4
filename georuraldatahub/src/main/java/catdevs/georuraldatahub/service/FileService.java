package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.collection.CollectedData;
import catdevs.georuraldatahub.collection.DownloadedMultipartFile;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Quarantine;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.DataIngestionType;
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.IngestionRepository;
import catdevs.georuraldatahub.repository.QuarantineRepository;
import catdevs.georuraldatahub.repository.VersionRepository;
import catdevs.georuraldatahub.validation.FileValidationReport;
import catdevs.georuraldatahub.validation.FileValidationService;
import catdevs.georuraldatahub.validation.RecordValidationResult;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final VersionRepository versionRepository;
    private final IngestionRepository ingestionRepository;

    private final DatasetService datasetService;
    private final UserService userService;
    private final DictionaryService dictionaryService;
    private final BucketService bucketService;
    private final FileValidationService validationService;
    private final QuarantineRepository quarantineRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileService(
            FileRepository fileRepository,
            VersionRepository versionRepository,
            IngestionRepository ingestionRepository,
            DatasetService datasetService,
            UserService userService,
            DictionaryService dictionaryService,
            BucketService bucketService,
            FileValidationService validationService,
            QuarantineRepository quarantineRepository) {

        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.ingestionRepository = ingestionRepository;
        this.datasetService = datasetService;
        this.userService = userService;
        this.dictionaryService = dictionaryService;
        this.bucketService = bucketService;
        this.validationService = validationService;
        this.quarantineRepository = quarantineRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadToSource(MultipartFile file, Long sourceId, Long userId, Long dictionaryId) throws Exception {
        return ingest(file, null, userId, sourceId, null, dictionaryId, DataIngestionType.UPLOAD, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadFile(MultipartFile multipartFile, Long datasetId, Long userId, Long sourceId,
            Long dictionaryId) throws Exception {
        return ingest(multipartFile, datasetId, userId, sourceId, null, dictionaryId, DataIngestionType.UPLOAD, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadCollectedFile(CollectedData collected,
            Long datasetId, Long userId, Long sourceId, Long dictionaryId, String connector) throws Exception {
        var file = new DownloadedMultipartFile(collected.filename(),
                collected.format().equals("csv") ? "text/csv" : "application/geo+json", collected.content());
        return ingest(file, datasetId, userId, sourceId, collected, dictionaryId, DataIngestionType.API, connector);
    }

    private FileResponseDTO ingest(MultipartFile multipartFile, Long datasetId, Long userId, Long sourceId,
            CollectedData collected, Long dictionaryId, DataIngestionType ingestionType, String connector) throws Exception {
        String hash = calculateHash(multipartFile);

        User user = userService.findEntityById(userId);
        // O bloqueio da fonte mantém a checagem de duplicidade e a gravação na mesma transação.
        Dictionary dictionary = dictionaryService.acquireForValidation(sourceId, dictionaryId);
        if (fileRepository.existsByHashAndIngestion_Dictionary_Source_Id(hash, sourceId)) {
            throw new DuplicateFileException("Este conteúdo já foi enviado para esta fonte. Selecione o arquivo existente para compor o conjunto.");
        }
        // A ingestão pertence à fonte; o conjunto é uma associação opcional, também disponível depois do upload.
        Dataset dataset = datasetId == null ? null : datasetService.findLockedEntityById(datasetId);

        String sanitizedFilename = sanitizeFilename(multipartFile.getOriginalFilename());
        String format = extractFormat(sanitizedFilename);
        if (sanitizedFilename.length() > 200) throw new IllegalArgumentException("Nome do arquivo excede 200 caracteres.");
        if (format == null || format.isBlank() || format.length() > 10)
            throw new IllegalArgumentException("Informe um arquivo com extensão de até 10 caracteres.");

        var rules = dictionaryService.rules(dictionary);
        List<RecordValidationResult> records;
        try (InputStream input = multipartFile.getInputStream()) {
            records = validationService.validate(input, format, rules);
        }
        var report = FileValidationReport.of(
                dictionary.getId(), dictionary.getVersion(), records);
        String reportJson = mapper.writeValueAsString(report);
        byte[] accepted;
        try (InputStream input = multipartFile.getInputStream()) {
            accepted = validationService.acceptedContent(input, format, records);
        }
        String location = bucketService.uploadToSourceRawZone(multipartFile, sourceId, hash, sanitizedFilename);

        List<String> uploaded = new ArrayList<>();
        uploaded.add(location);
        // O OCI não participa da transação JPA; em rollback, tentamos remover os objetos enviados.
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) for (String key : uploaded) {
                        try { bucketService.deleteObject(key); }
                        catch (RuntimeException cleanupError) {
                            LoggerFactory.getLogger(FileService.class).error("Falha ao remover objeto {}", key, cleanupError);
                        }
                    }
                }
            });
        String reportLocation = bucketService.uploadValidationReport(location, reportJson);
        uploaded.add(reportLocation);

        // A conversão do conector não substitui os arquivos oficiais: guardamos ambos e a proveniência.
        if (collected != null) {
            int index = 0;
            for (var original : collected.originals()) {
                String key = location + ".originals/" + String.format(java.util.Locale.ROOT, "%05d_", index++)
                        + sanitizeFilename(original.filename());
                uploaded.add(bucketService.uploadCollectionArtifact(key, original.content(), original.contentType()));
            }
            uploaded.add(bucketService.uploadCollectionArtifact(location + ".collection.json",
                    mapper.writeValueAsBytes(collected.metadata()), "application/json"));
        }

        DataIngestion ingestion = new DataIngestion();
        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);
        ingestion.setType(ingestionType);
        ingestion.setConnector(connector);
        ingestion.setIngStatus(report.status().equals("REJEITADO") ? IngestionStatus.ERRO : IngestionStatus.CONCLUIDO);
        ingestion = ingestionRepository.save(ingestion);

        File file = new File();
        file.setIngestion(ingestion);
        file.setName(sanitizedFilename);
        file.setFormatFile(format);
        file.setSize(multipartFile.getSize());
        file.setHash(hash);
        file.setLocation(location);
        file = fileRepository.save(file);

        Version version = new Version();
        version.setFile(file);
        version.setVersionNumber(1);
        version.setStatus(VersionStatus.BRUTA);
        version.setLocation(location);
        versionRepository.save(version);

        if (!report.status().equals("VALIDO")) {
            var quarantine = new Quarantine();
            quarantine.setIngestion(ingestion);
            quarantine.setFile(file);
            quarantine.setStatus(report.status().equals("REJEITADO") ? "ARQUIVO_REJEITADO" : "REGISTROS_REJEITADOS");
            quarantine.setReason(quarantineSummary(report));
            quarantineRepository.save(quarantine);
        }

        if (accepted.length > 0) {
            String acceptedLocation = bucketService.uploadSourceValidatedContent(accepted, sourceId, hash, sanitizedFilename);
            uploaded.add(acceptedLocation);
            Version validated = new Version();
            validated.setFile(file);
            validated.setVersionNumber(2);
            // Aprovação na validação ainda não significa tratamento ou publicação.
            validated.setStatus(VersionStatus.BRUTA);
            validated.setLocation(acceptedLocation);
            versionRepository.save(validated);
        }

        if (dataset != null) dataset.getFiles().add(file);

        return new FileResponseDTO(
                file.getId(),
                file.getName(),
                file.getFormatFile(),
                file.getSize(),
                file.getHash(),
                file.getLocation(),
                ingestion.getId(),
                file.getReceivedDate(),
                user.getId(), report);
    }

    String calculateHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = file.getInputStream();
                DigestInputStream dis = new DigestInputStream(is, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
            }

        }

        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private String extractFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "arquivo_sem_nome";
        }
        String semAcento = Normalizer.normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String quarantineSummary(FileValidationReport report) {
        String codes = report.records().stream().flatMap(r -> r.errors().stream())
                .map(error -> error.split(":", 2)[0].replaceAll("[^A-Z_]", ""))
                .filter(code -> !code.isEmpty()).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(","));
        String summary = report.status() + "; validos=" + report.validRecords()
                + "; rejeitados=" + report.rejectedRecords() + "; codigos=" + codes;
        return summary.substring(0, Math.min(summary.length(), 500));
    }

    @Transactional(readOnly = true)
    public FileValidationReport validationReport(Long fileId) throws Exception {
        File file = fileRepository.findById(fileId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        return mapper.readValue(bucketService.readValidationReport(file.getLocation()), FileValidationReport.class);
    }

    @Transactional(readOnly = true)
    public CollectedData.Metadata collectionMetadata(Long fileId) throws Exception {
        File file = fileRepository.findById(fileId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        return mapper.readValue(bucketService.readCollectionMetadata(file.getLocation()),
                CollectedData.Metadata.class);
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findBySourceId(Long sourceId) {
        return fileRepository.findByIngestion_Dictionary_Source_IdOrderByIdDesc(sourceId).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findByDatasetId(Long datasetId) {
        return datasetService.findEntityById(datasetId).getFiles().stream()
                .map(this::response)
                .toList();
    }

    private FileResponseDTO response(File file) {
        var ingestion = file.getIngestion();
        return new FileResponseDTO(file.getId(), file.getName(), file.getFormatFile(),
                file.getSize(), file.getHash(), file.getLocation(), ingestion.getId(),
                file.getReceivedDate(), ingestion.getUser().getId());
    }
}
