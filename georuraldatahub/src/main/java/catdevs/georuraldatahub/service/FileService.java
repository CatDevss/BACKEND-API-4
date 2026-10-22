
package catdevs.georuraldatahub.service;

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
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.exception.UserNotFoundException;
import catdevs.georuraldatahub.validation.FileValidationReport;
import catdevs.georuraldatahub.validation.FileValidationService;
import catdevs.georuraldatahub.validation.RecordValidationResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FileService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FileService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final BucketService bucketService;
    private final DatasetService datasetService;
    private final DictionaryService dictionaryService;
    private final FileValidationService validationService;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileService(
            BucketService bucketService,
            DatasetService datasetService,
            DictionaryService dictionaryService,
            FileValidationService validationService) {
        this.bucketService = bucketService;
        this.datasetService = datasetService;
        this.dictionaryService = dictionaryService;
        this.validationService = validationService;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadToSource(
            MultipartFile file,
            Long sourceId,
            Long userId) throws Exception {

        return ingest(file, null, userId, sourceId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadFile(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId,
            Long sourceId) throws Exception {

        return ingest(
                multipartFile,
                datasetId,
                userId,
                sourceId,
                null
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadCollectedFile(
            CollectedData collected,
            Long datasetId,
            Long userId,
            Long sourceId) throws Exception {

        MultipartFile file = new DownloadedMultipartFile(
                collected.filename(),
                "csv".equalsIgnoreCase(collected.format())
                        ? "text/csv"
                        : "application/geo+json",
                collected.content()
        );

        return ingest(
                file,
                datasetId,
                userId,
                sourceId,
                collected
        );
    }

    private FileResponseDTO ingest(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId,
            Long sourceId,
            CollectedData collected) throws Exception {

        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O arquivo enviado está vazio."
            );
        }

        String hash = calculateHash(multipartFile);

        User user = entityManager.find(User.class, userId);

        if (user == null) {
            throw new UserNotFoundException(
                    "Usuário não encontrado."
            );
        }

        // Obtém o dicionário mais recente e bloqueia a fonte
        // durante a operação de validação.
        Dictionary dictionary =
                dictionaryService.acquireForValidation(sourceId);

        Long duplicates = entityManager.createQuery(
                """
                SELECT COUNT(f)
                FROM File f
                WHERE f.hash = :hash
                  AND f.ingestion.dictionary.source.id = :sourceId
                """,
                Long.class
        )
        .setParameter("hash", hash)
        .setParameter("sourceId", sourceId)
        .getSingleResult();

        if (duplicates > 0) {
            throw new DuplicateFileException(
                    "Este conteúdo já foi enviado para esta fonte. "
                    + "Selecione o arquivo existente para compor o conjunto."
            );
        }

        Dataset dataset = datasetId == null
                ? null
                : datasetService.findLockedEntityById(datasetId);

        String sanitizedFilename =
                sanitizeFilename(multipartFile.getOriginalFilename());

        if (sanitizedFilename.length() > 200) {
            throw new IllegalArgumentException(
                    "Nome do arquivo excede 200 caracteres."
            );
        }

        String format = extractFormat(sanitizedFilename);

        if (format == null || format.isBlank() || format.length() > 10) {
            throw new IllegalArgumentException(
                    "Informe um arquivo com extensão de até 10 caracteres."
            );
        }

        format = format.toLowerCase(Locale.ROOT);

        List<catdevs.georuraldatahub.validation.FieldRule> rules =
                dictionaryService.rules(dictionary);

        List<RecordValidationResult> records;

        try (InputStream input = multipartFile.getInputStream()) {
            records = validationService.validate(input, format, rules);
        }

        FileValidationReport report = FileValidationReport.of(
                dictionary.getId(),
                dictionary.getVersion(),
                records
        );

        String reportJson = mapper.writeValueAsString(report);

        byte[] accepted;

        try (InputStream input = multipartFile.getInputStream()) {
            accepted = validationService.acceptedContent(
                    input,
                    format,
                    records
            );
        }

        // O arquivo original é preservado para auditoria.
        // Somente o artefato filtrado fica disponível para tratamento.
        String location = bucketService.uploadToSourceRawZone(
                multipartFile,
                sourceId,
                hash,
                sanitizedFilename
        );

        List<String> uploaded = new ArrayList<>();
        uploaded.add(location);

        // Remove objetos enviados ao bucket caso a transação seja revertida.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            for (String key : uploaded) {
                                try {
                                    bucketService.deleteObject(key);
                                } catch (RuntimeException cleanupError) {
                                    LOGGER.error(
                                            "Falha ao remover objeto {}",
                                            key,
                                            cleanupError
                                    );
                                }
                            }
                        }
                    }
                }
        );

        String reportLocation =
                bucketService.uploadValidationReport(location, reportJson);
        uploaded.add(reportLocation);

        if (collected != null) {
            int index = 0;

            for (var original : collected.originals()) {
                String key = location + ".originals/"
                        + String.format(Locale.ROOT, "%05d_", index++)
                        + sanitizeFilename(original.filename());

                uploaded.add(
                        bucketService.uploadCollectionArtifact(
                                key,
                                original.content(),
                                original.contentType()
                        )
                );
            }

            uploaded.add(
                    bucketService.uploadCollectionArtifact(
                            location + ".collection.json",
                            mapper.writeValueAsBytes(collected.metadata()),
                            "application/json"
                    )
            );
        }

        DataIngestion ingestion = new DataIngestion();
        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);
        ingestion.setIngStatus(
                "REJEITADO".equals(report.status())
                        ? IngestionStatus.ERRO
                        : IngestionStatus.CONCLUIDO
        );

        entityManager.persist(ingestion);
        entityManager.flush();

        File file = new File();
        file.setIngestion(ingestion);
        file.setName(sanitizedFilename);
        file.setFormatFile(format);
        file.setSize(multipartFile.getSize());
        file.setHash(hash);
        file.setLocation(location);

        entityManager.persist(file);
        entityManager.flush();

        Version version = new Version();
        version.setFile(file);
        version.setVersionNumber(1);
        version.setStatus(VersionStatus.BRUTA);
        version.setLocation(location);

        entityManager.persist(version);

        if (!"VALIDO".equals(report.status())) {
            Quarantine quarantine = new Quarantine();
            quarantine.setIngestion(ingestion);
            quarantine.setFile(file);
            quarantine.setStatus(
                    "REJEITADO".equals(report.status())
                            ? "ARQUIVO_REJEITADO"
                            : "REGISTROS_REJEITADOS"
            );
            quarantine.setReason(quarantineSummary(report));

            entityManager.persist(quarantine);
        }

        if (accepted.length > 0) {
            String acceptedLocation =
                    bucketService.uploadSourceValidatedContent(
                            accepted,
                            sourceId,
                            hash,
                            sanitizedFilename
                    );

            uploaded.add(acceptedLocation);

            Version validated = new Version();
            validated.setFile(file);
            validated.setVersionNumber(2);
            validated.setStatus(VersionStatus.BRUTA);
            validated.setLocation(acceptedLocation);

            entityManager.persist(validated);
        }

        if (dataset != null) {
            dataset.getFiles().add(file);
        }

        entityManager.flush();

        return new FileResponseDTO(
                file.getId(),
                file.getName(),
                file.getFormatFile(),
                file.getSize(),
                file.getHash(),
                file.getLocation(),
                ingestion.getId(),
                file.getReceivedDate(),
                user.getId(),
                report
        );
    }

    @Transactional(readOnly = true)
    public FileValidationReport validationReport(Long fileId)
            throws Exception {

        File file = findFile(fileId);

        return mapper.readValue(
                bucketService.readValidationReport(file.getLocation()),
                FileValidationReport.class
        );
    }

    @Transactional(readOnly = true)
    public CollectedData.Metadata collectionMetadata(Long fileId)
            throws Exception {

        File file = findFile(fileId);

        return mapper.readValue(
                bucketService.readCollectionMetadata(file.getLocation()),
                CollectedData.Metadata.class
        );
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findBySourceId(Long sourceId) {
        return entityManager.createQuery(
                """
                SELECT f
                FROM File f
                WHERE f.ingestion.dictionary.source.id = :sourceId
                ORDER BY f.id DESC
                """,
                File.class
        )
        .setParameter("sourceId", sourceId)
        .getResultList()
        .stream()
        .map(this::toDto)
        .toList();
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findByDatasetId(Long datasetId) {
        Dataset dataset = datasetService.findEntityById(datasetId);

        return buscarArquivosPorDataset(dataset.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private File findFile(Long fileId) {
        File file = entityManager.find(File.class, fileId);

        if (file == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Arquivo não encontrado."
            );
        }

        return file;
    }

    private List<File> buscarArquivosPorDataset(Long datasetId) {
        return entityManager.createQuery(
                """
                SELECT f
                FROM Dataset d JOIN d.files f
                WHERE d.id = :datasetId
                ORDER BY f.id DESC
                """,
                File.class
        )
        .setParameter("datasetId", datasetId)
        .getResultList();
    }

    private String quarantineSummary(FileValidationReport report) {
        String codes = report.records()
                .stream()
                .flatMap(record -> record.errors().stream())
                .map(error -> error.split(":", 2)[0]
                        .replaceAll("[^A-Z_]", ""))
                .filter(code -> !code.isEmpty())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));

        String summary = report.status()
                + "; validos=" + report.validRecords()
                + "; rejeitados=" + report.rejectedRecords()
                + "; codigos=" + codes;

        return summary.substring(0, Math.min(summary.length(), 500));
    }

    public String calculateHash(MultipartFile file)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = file.getInputStream();
             DigestInputStream digestInput =
                     new DigestInputStream(input, digest)) {

            byte[] buffer = new byte[8192];

            while (digestInput.read(buffer) != -1) {
                // A leitura atualiza o digest.
            }
        }

        StringBuilder result = new StringBuilder();

        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }

        return result.toString();
    }

    private String extractFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }

        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "arquivo_sem_nome";
        }

        String withoutAccents = Normalizer
                .normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return withoutAccents.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private FileResponseDTO toDto(File file) {
        DataIngestion ingestion = file.getIngestion();

        Long ingestionId = ingestion != null
                ? ingestion.getId()
                : null;

        Long userId = ingestion != null && ingestion.getUser() != null
                ? ingestion.getUser().getId()
                : null;

        return new FileResponseDTO(
                file.getId(),
                file.getName(),
                file.getFormatFile(),
                file.getSize(),
                file.getHash(),
                file.getLocation(),
                ingestionId,
                file.getReceivedDate(),
                userId
        );
    }
}
