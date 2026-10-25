package catdevs.georuraldatahub.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.collection.CollectedData;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Quarantine;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Version;
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

    private static final int MAX_FILENAME_BYTES = 200;
    private static final int MAX_FORMAT_BYTES = 10;
    private static final int MAX_QUARANTINE_REASON_BYTES = 500;

    private final FileRepository fileRepository;
    private final VersionRepository versionRepository;
    private final IngestionRepository ingestionRepository;
    private final DatasetService datasetService;
    private final UserService userService;
    private final DictionaryService dictionaryService;
    private final BucketService bucketService;
    private final FileValidationService fileValidationService;
    private final QuarantineRepository quarantineRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileService(
            FileRepository fileRepository,
            VersionRepository versionRepository,
            IngestionRepository ingestionRepository,
            DatasetService datasetService,
            UserService userService,
            DictionaryService dictionaryService,
            BucketService bucketService,
            FileValidationService fileValidationService,
            QuarantineRepository quarantineRepository) {

        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.ingestionRepository = ingestionRepository;
        this.datasetService = datasetService;
        this.userService = userService;
        this.dictionaryService = dictionaryService;
        this.bucketService = bucketService;
        this.fileValidationService = fileValidationService;
        this.quarantineRepository = quarantineRepository;
    }

    @Transactional
    public FileResponseDTO uploadFile(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId,
            Long sourceId) throws Exception {

        validateMultipartFile(multipartFile);

        String filename = sanitizeAndValidateFilename(
                multipartFile.getOriginalFilename());

        Dictionary dictionary =
                dictionaryService.acquireForValidation(sourceId);

        String hash = calculateHash(multipartFile);

        if (fileRepository.existsByHashAndIngestion_Dictionary_Source_Id(
                    hash, sourceId)
                || fileRepository.existsByHashInDataset(datasetId, hash)) {

            throw new DuplicateFileException(
                    "Este arquivo já foi enviado anteriormente para esta fonte ou conjunto.");
        }

        Dataset dataset = datasetService.findLockedEntityById(datasetId);
        User user = userService.findEntityById(userId);

        String rawLocation = bucketService.uploadToSourceRawZone(
                multipartFile, sourceId, hash, filename);

        List<String> artifacts = new ArrayList<>();
        artifacts.add(rawLocation);
        artifacts.add(rawLocation + ".validation.json");

        registerRollbackCleanup(artifacts);

        try {
            byte[] original = multipartFile.getBytes();

            FileValidationReport report = validateAndStoreReport(
                    rawLocation, original, filename, dictionary);

            String validatedLocation = null;

            if (report.validRecords() > 0
                    && report.records().stream()
                            .noneMatch(r -> r.recordIndex() < 0)) {

                byte[] accepted = fileValidationService.acceptedContent(
                        new ByteArrayInputStream(original),
                        formatOf(filename),
                        report.records());

                if (accepted.length > 0) {
                    validatedLocation =
                            bucketService.uploadSourceValidatedContent(
                                    accepted, sourceId, hash, filename);

                    artifacts.add(validatedLocation);
                }
            }

            IngestionStatus status =
                    report.status().equals("REJEITADO")
                            ? IngestionStatus.ERRO
                            : IngestionStatus.CONCLUIDO;

            DataIngestion ingestion =
                    createIngestion(dictionary, user, status);

            File file = createFile(
                    ingestion,
                    filename,
                    multipartFile.getSize(),
                    hash,
                    rawLocation);

            saveVersion(file, rawLocation, 1);

            if (validatedLocation != null) {
                saveVersion(file, validatedLocation, 1);
            }

            if (dataset.getFiles() != null) {
                boolean alreadyAssociated = dataset.getFiles().stream()
                        .anyMatch(existing ->
                                existing.getId() != null
                                        && existing.getId().equals(file.getId()));

                if (!alreadyAssociated) {
                    dataset.getFiles().add(file);
                }
            }

            persistQuarantineIfNeeded(file, ingestion, report);

            return toResponseDTO(file, report);

        } catch (Exception e) {
            cleanupImmediatelyIfNoTransaction(artifacts);
            throw e;
        }
    }

    @Transactional
    public FileResponseDTO uploadToSource(
            MultipartFile multipartFile,
            Long sourceId,
            Long userId) throws Exception {

        validateMultipartFile(multipartFile);

        String filename = sanitizeAndValidateFilename(
                multipartFile.getOriginalFilename());

        Dictionary dictionary =
                dictionaryService.acquireForValidation(sourceId);

        String hash = calculateHash(multipartFile);

        if (fileRepository.existsByHashAndIngestion_Dictionary_Source_Id(
                hash, sourceId)) {

            throw new DuplicateFileException(
                    "Este arquivo já foi enviado anteriormente para esta fonte.");
        }

        User user = userService.findEntityById(userId);

        String rawLocation = bucketService.uploadToSourceRawZone(
                multipartFile, sourceId, hash, filename);

        List<String> artifacts = new ArrayList<>();
        artifacts.add(rawLocation);
        artifacts.add(rawLocation + ".validation.json");

        registerRollbackCleanup(artifacts);

        try {
            byte[] original = multipartFile.getBytes();

            FileValidationReport report = validateAndStoreReport(
                    rawLocation, original, filename, dictionary);

            String validatedLocation = null;

            if (report.validRecords() > 0
                    && report.records().stream()
                            .noneMatch(r -> r.recordIndex() < 0)) {

                byte[] accepted = fileValidationService.acceptedContent(
                        new ByteArrayInputStream(original),
                        formatOf(filename),
                        report.records());

                if (accepted.length > 0) {
                    validatedLocation =
                            bucketService.uploadSourceValidatedContent(
                                    accepted, sourceId, hash, filename);

                    artifacts.add(validatedLocation);
                }
            }

            IngestionStatus status =
                    report.status().equals("REJEITADO")
                            ? IngestionStatus.ERRO
                            : IngestionStatus.CONCLUIDO;

            DataIngestion ingestion =
                    createIngestion(dictionary, user, status);

            File file = createFile(
                    ingestion,
                    filename,
                    multipartFile.getSize(),
                    hash,
                    rawLocation);

            saveVersion(file, rawLocation, 1);

            if (validatedLocation != null) {
                saveVersion(file, validatedLocation, 1);
            }

            persistQuarantineIfNeeded(file, ingestion, report);

            return toResponseDTO(file, report);

        } catch (Exception e) {
            cleanupImmediatelyIfNoTransaction(artifacts);
            throw e;
        }
    }

    @Transactional
    public FileResponseDTO uploadCollectedFile(
            CollectedData data,
            Long datasetId,
            Long userId,
            Long sourceId) throws Exception {

        if (data == null
                || data.content() == null
                || data.content().length == 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A coleta não retornou conteúdo.");
        }

        String filename = sanitizeAndValidateFilename(data.filename());

        Dictionary dictionary =
                dictionaryService.acquireForValidation(sourceId);

        String hash = sha256(data.content());

        if (fileRepository.existsByHashAndIngestion_Dictionary_Source_Id(
                hash, sourceId)) {

            throw new DuplicateFileException(
                    "Este arquivo já foi coletado anteriormente para esta fonte.");
        }

        Dataset dataset = datasetId == null
                ? null
                : datasetService.findLockedEntityById(datasetId);

        User user = userService.findEntityById(userId);

        String rawLocation = "bruta/" + filename;

        List<String> artifacts = new ArrayList<>();
        artifacts.add(rawLocation);
        artifacts.add(rawLocation + ".validation.json");
        artifacts.add(rawLocation + ".collection.json");

        registerRollbackCleanup(artifacts);

        try {
            bucketService.uploadCollectionArtifact(
                    rawLocation,
                    data.content(),
                    contentType(data.format()));

            if (data.originals() != null) {
                for (int i = 0; i < data.originals().size(); i++) {
                    CollectedData.Original original =
                            data.originals().get(i);

                    String originalName =
                            sanitizeFilename(original.filename());

                    if (originalName.isBlank()) {
                        originalName = "arquivo_sem_nome";
                    }

                    String key = rawLocation + ".originals/"
                            + String.format(
                                    Locale.ROOT,
                                    "%05d_%s",
                                    i,
                                    originalName);

                    bucketService.uploadCollectionArtifact(
                            key,
                            original.content(),
                            original.contentType());

                    artifacts.add(key);
                }
            }

            bucketService.uploadCollectionArtifact(
                    rawLocation + ".collection.json",
                    objectMapper.writeValueAsBytes(data.metadata()),
                    "application/json");

            FileValidationReport report = validateAndStoreReport(
                    rawLocation,
                    data.content(),
                    filename,
                    dictionary);

            String validatedLocation = null;

            if (report.validRecords() > 0
                    && report.records().stream()
                            .noneMatch(r -> r.recordIndex() < 0)) {

                byte[] accepted = fileValidationService.acceptedContent(
                        new ByteArrayInputStream(data.content()),
                        formatOf(filename),
                        report.records());

                if (accepted.length > 0) {
                    validatedLocation =
                            bucketService.uploadSourceValidatedContent(
                                    accepted, sourceId, hash, filename);

                    artifacts.add(validatedLocation);
                }
            }

            IngestionStatus status =
                    report.status().equals("REJEITADO")
                            ? IngestionStatus.ERRO
                            : IngestionStatus.CONCLUIDO;

            DataIngestion ingestion =
                    createIngestion(dictionary, user, status);

            File file = createFile(
                    ingestion,
                    filename,
                    data.content().length,
                    hash,
                    rawLocation);

            saveVersion(file, rawLocation, 1);

            if (validatedLocation != null) {
                saveVersion(file, validatedLocation, 1);
            }

            if (dataset != null && dataset.getFiles() != null) {
                boolean alreadyAssociated = dataset.getFiles().stream()
                        .anyMatch(existing ->
                                existing.getId() != null
                                        && existing.getId().equals(file.getId()));

                if (!alreadyAssociated) {
                    dataset.getFiles().add(file);
                }
            }

            persistQuarantineIfNeeded(file, ingestion, report);

            return toResponseDTO(file, report);

        } catch (Exception e) {
            cleanupImmediatelyIfNoTransaction(artifacts);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findBySourceId(Long sourceId) {
        return fileRepository
                .findByIngestion_Dictionary_Source_IdOrderByIdDesc(sourceId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectedData.Metadata collectionMetadata(Long fileId)
            throws IOException {

        File file = findEntityById(fileId);

        String json = bucketService.readCollectionMetadata(file.getLocation());

        return objectMapper.readValue(
                json,
                CollectedData.Metadata.class);
    }

    @Transactional
    public FileValidationReport validationReport(Long fileId)
            throws IOException {

        File file = findEntityById(fileId);

        String json = bucketService.readValidationReport(file.getLocation());

        try {
            return objectMapper.readValue(json, FileValidationReport.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "O relatório de validação armazenado está inválido.",
                    e);
        }
    }

    String calculateHash(MultipartFile file)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = file.getInputStream();
                DigestInputStream digestInput =
                        new DigestInputStream(input, digest)) {

            byte[] buffer = new byte[8192];

            while (digestInput.read(buffer) != -1) {
                // Calcula o hash do conteúdo.
            }
        }

        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(byte[] content)
            throws NoSuchAlgorithmException {

        return java.util.HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(content));
    }

    private FileValidationReport validateAndStoreReport(
            String rawLocation,
            byte[] content,
            String filename,
            Dictionary dictionary) throws Exception {

        List<RecordValidationResult> records =
                fileValidationService.validate(
                        new ByteArrayInputStream(content),
                        formatOf(filename),
                        dictionaryService.rules(dictionary));

        FileValidationReport report = FileValidationReport.of(
                dictionary.getId(),
                dictionary.getVersion(),
                records);

        bucketService.uploadValidationReport(
                rawLocation,
                objectMapper.writeValueAsString(report));

        return report;
    }

    private void persistQuarantineIfNeeded(
            File file,
            DataIngestion ingestion,
            FileValidationReport report) {

        List<RecordValidationResult> rejected = report.records()
                .stream()
                .filter(r -> !r.valid())
                .toList();

        if (rejected.isEmpty()) {
            return;
        }

        Quarantine quarantine = new Quarantine();
        quarantine.setFile(file);
        quarantine.setIngestion(ingestion);

        quarantine.setStatus(
                report.status().equals("REJEITADO")
                        ? "ARQUIVO_REJEITADO"
                        : "REGISTROS_REJEITADOS");

        String reason = rejected.stream()
                .flatMap(r -> r.errors().stream())
                .distinct()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Registros rejeitados na validação.");

        quarantine.setReason(
                truncateUtf8(reason, MAX_QUARANTINE_REASON_BYTES));

        quarantineRepository.save(quarantine);
    }

    private DataIngestion createIngestion(
            Dictionary dictionary,
            User user,
            IngestionStatus status) {

        DataIngestion ingestion = new DataIngestion();
        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);
        ingestion.setIngStatus(status);

        return ingestionRepository.save(ingestion);
    }

    private File createFile(
            DataIngestion ingestion,
            String filename,
            long size,
            String hash,
            String location) {

        File file = new File();
        file.setIngestion(ingestion);
        file.setName(filename);
        file.setFormatFile(formatOf(filename));
        file.setSize(size);
        file.setHash(hash);
        file.setLocation(location);

        return fileRepository.save(file);
    }

    private void saveVersion(
            File file,
            String location,
            int number) {

        Version version = new Version();
        version.setFile(file);
        version.setVersionNumber(number);
        version.setStatus(VersionStatus.BRUTA);
        version.setLocation(location);

        versionRepository.save(version);
    }

    private void registerRollbackCleanup(List<String> artifacts) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            cleanup(artifacts);
                        }
                    }
                });
    }

    private void cleanupImmediatelyIfNoTransaction(
            List<String> artifacts) {

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        cleanup(artifacts);
    }

    private void cleanup(List<String> artifacts) {
        for (int i = artifacts.size() - 1; i >= 0; i--) {
            try {
                bucketService.deleteObject(artifacts.get(i));
            } catch (RuntimeException ignored) {
                // Limpeza de melhor esforço.
            }
        }
    }

    private void validateMultipartFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O arquivo é obrigatório e não pode estar vazio.");
        }
    }

    /**
     * Sanitiza e valida o nome antes de qualquer acesso ao banco ou bucket.
     * O limite considera bytes UTF-8, não apenas caracteres.
     */
    private String sanitizeAndValidateFilename(String original) {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do arquivo é obrigatório.");
        }

        String filename = sanitizeFilename(original);

        if (filename.isBlank()
                || filename.startsWith(".")
                || filename.endsWith(".")) {

            throw new IllegalArgumentException(
                    "O nome do arquivo é inválido.");
        }

        if (filename.getBytes(StandardCharsets.UTF_8).length
                > MAX_FILENAME_BYTES) {

            throw new IllegalArgumentException(
                    "Nome do arquivo excede o limite de 200 bytes.");
        }

        String format = formatOf(filename);

        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException(
                    "O arquivo precisa possuir uma extensão.");
        }

        if (format.getBytes(StandardCharsets.UTF_8).length
                > MAX_FORMAT_BYTES) {

            throw new IllegalArgumentException(
                    "Formato do arquivo excede o limite permitido.");
        }

        return filename;
    }

    /**
     * Normaliza caracteres e remove caracteres não permitidos.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        String normalized = Normalizer
                .normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String formatOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }

        return filename
                .substring(filename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String contentType(String format) {
        if (format == null) {
            return "application/octet-stream";
        }

        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json", "geojson" -> "application/geo+json";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    private String truncateUtf8(String value, int maxBytes) {
        StringBuilder result = new StringBuilder();
        int used = 0;

        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);

            String character =
                    new String(Character.toChars(codePoint));

            int bytes = character
                    .getBytes(StandardCharsets.UTF_8)
                    .length;

            if (used + bytes > maxBytes) {
                break;
            }

            result.append(character);
            used += bytes;
            i += Character.charCount(codePoint);
        }

        return result.toString();
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findByDatasetId(Long datasetId) {
        datasetService.findEntityById(datasetId);

        return fileRepository.findByDatasetId(datasetId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<File> findEntitiesByDatasetId(Long datasetId) {
        datasetService.findEntityById(datasetId);

        return fileRepository.findByDatasetId(datasetId);
    }

    @Transactional(readOnly = true)
    public File findEntityById(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado."));
    }

    private FileResponseDTO toResponseDTO(File file) {
        return toResponseDTO(file, null);
    }

    private FileResponseDTO toResponseDTO(
            File file,
            FileValidationReport report) {

        return new FileResponseDTO(
                file.getId(),
                file.getName(),
                file.getFormatFile(),
                file.getSize(),
                file.getHash(),
                file.getLocation(),
                file.getIngestion().getId(),
                file.getReceivedDate(),
                file.getIngestion().getUser().getId(),
                report);
    }
}