package catdevs.georuraldatahub.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

    private static final int MAX_FILENAME_LENGTH = 200;
    private static final int MAX_FORMAT_LENGTH = 10;
    private static final int MAX_QUARANTINE_REASON_BYTES = 500;

    private final FileRepository fileRepository;
    private final VersionRepository versionRepository;
    private final IngestionRepository ingestionRepository;
    private final DatasetService datasetService;
    private final UserService userService;
    private final DictionaryService dictionaryService;
    private final BucketService bucketService;
    private final FileValidationService validationService;
    private final QuarantineRepository quarantineRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileService(FileRepository fileRepository, VersionRepository versionRepository,
            IngestionRepository ingestionRepository, DatasetService datasetService,
            UserService userService, DictionaryService dictionaryService, BucketService bucketService,
            FileValidationService validationService, QuarantineRepository quarantineRepository) {
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

    @Transactional
    public FileResponseDTO uploadFile(MultipartFile multipartFile, Long datasetId, Long userId, Long sourceId)
            throws Exception {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("O arquivo não pode estar vazio.");
        }
        String filename = sanitizeFilename(multipartFile.getOriginalFilename());
        validateFilenameAndFormat(filename);
        byte[] content = multipartFile.getBytes();
        String hash = calculateHash(content);
        Dictionary dictionary = dictionaryService.acquireForValidation(sourceId);
        if (fileRepository.existsByHashAndIngestion_Dictionary_Source_Id(hash, sourceId)) {
            throw new DuplicateFileException("Este arquivo já foi enviado anteriormente para esta fonte.");
        }
        Dataset dataset = datasetId == null ? null : datasetService.findLockedEntityById(datasetId);
        User user = userService.findEntityById(userId);
        List<String> uploadedObjects = new ArrayList<>();
        String rawLocation = null;
        try {
            rawLocation = bucketService.uploadToSourceRawZone(multipartFile, sourceId, hash, filename);
            addObjectIfAbsent(uploadedObjects, rawLocation);
            return processAndSave(content, filename, extractFormat(filename), hash, rawLocation,
                    dataset, user, dictionary, sourceId, uploadedObjects);
        } catch (Exception e) {
            registerCleanup(uploadedObjects);
            throw e;
        }
    }

    @Transactional
    public FileResponseDTO uploadToSource(MultipartFile multipartFile, Long sourceId, Long userId) throws Exception {
        return uploadFile(multipartFile, null, userId, sourceId);
    }

    @Transactional
    public FileResponseDTO uploadCollectedFile(CollectedData data, Long datasetId, Long userId, Long sourceId)
            throws Exception {
        if (data == null || data.content() == null || data.content().length == 0) {
            throw new IllegalArgumentException("O conteúdo coletado não pode estar vazio.");
        }
        String filename = sanitizeFilename(data.filename());
        validateFilenameAndFormat(filename);
        byte[] content = data.content();
        String hash = calculateHash(content);
        Dictionary dictionary = dictionaryService.acquireForValidation(sourceId);
        if (fileRepository.existsByHashAndIngestion_Dictionary_Source_Id(hash, sourceId)) {
            throw new DuplicateFileException("Este arquivo já foi coletado para esta fonte.");
        }
        Dataset dataset = datasetId == null ? null : datasetService.findLockedEntityById(datasetId);
        User user = userService.findEntityById(userId);

        List<String> uploadedObjects = new ArrayList<>();
        String rawLocation = "bruta/" + filename;
        String reportLocation = rawLocation + ".validation.json";
        String metadataLocation = rawLocation + ".collection.json";
        // Register the expected artifacts before upload, so partial failures are cleaned up too.
        addObjectIfAbsent(uploadedObjects, rawLocation);
        addObjectIfAbsent(uploadedObjects, reportLocation);
        try {
            bucketService.uploadCollectionArtifact(rawLocation, content, contentTypeFor(data.format()));

            List<CollectedData.OriginalMetadata> originalsMetadata = new ArrayList<>();
            if (data.originals() != null) {
                for (int i = 0; i < data.originals().size(); i++) {
                    CollectedData.Original original = data.originals().get(i);
                    if (original == null || original.content() == null) {
                        throw new IllegalArgumentException("Um dos arquivos originais coletados está inválido.");
                    }
                    String originalFilename = sanitizeFilename(original.filename());
                    String originalLocation = String.format(Locale.ROOT, "%s.originals/%05d_%s",
                            rawLocation, i, originalFilename);
                    addObjectIfAbsent(uploadedObjects, originalLocation);
                    String originalContentType = original.contentType() == null || original.contentType().isBlank()
                            ? "application/octet-stream" : original.contentType();
                    bucketService.uploadCollectionArtifact(originalLocation, original.content(), originalContentType);
                    originalsMetadata.add(new CollectedData.OriginalMetadata(original.filename(), original.origin(),
                            original.contentType(), original.content().length, original.sha256()));
                }
            }

            CollectedData.Metadata metadata = data.metadata();
            if (metadata == null) {
                metadata = new CollectedData.Metadata(null, null, data.format(), data.format(), null,
                        Instant.now().toString(), null, List.of(), originalsMetadata);
            }
            // Always write metadata, including for manual/empty metadata collection responses.
            addObjectIfAbsent(uploadedObjects, metadataLocation);
            byte[] metadataJson = objectMapper.writeValueAsBytes(metadata);
            bucketService.uploadCollectionArtifact(metadataLocation, metadataJson, "application/json");

            return processAndSave(content, filename, extractFormat(filename), hash, rawLocation,
                    dataset, user, dictionary, sourceId, uploadedObjects);
        } catch (Exception e) {
            registerCleanup(uploadedObjects);
            throw e;
        }
    }

    private FileResponseDTO processAndSave(byte[] content, String filename, String format, String hash,
            String rawLocation, Dataset dataset, User user, Dictionary dictionary, Long sourceId,
            List<String> uploadedObjects) throws Exception {
        List<RecordValidationResult> records = validationService.validate(
                new ByteArrayInputStream(content), format, dictionaryService.rules(dictionary));
        FileValidationReport report = FileValidationReport.of(dictionary.getId(), dictionary.getVersion(), records);

        String reportJson = objectMapper.writeValueAsString(report);
        addObjectIfAbsent(uploadedObjects, rawLocation + ".validation.json");
        bucketService.uploadValidationReport(rawLocation, reportJson);

        byte[] acceptedContent = validationService.acceptedContent(
                new ByteArrayInputStream(content), format, records);
        String validatedLocation = null;
        if (report.validRecords() > 0 && acceptedContent.length > 0) {
            // The expected key is tracked before uploading to handle a partially failed upload.
            validatedLocation = "validada/fontes/" + sourceId + "/" + hash + "_" + filename;
            addObjectIfAbsent(uploadedObjects, validatedLocation);
            String returnedLocation = bucketService.uploadSourceValidatedContent(acceptedContent, sourceId, hash, filename);
            if (returnedLocation != null && !Objects.equals(returnedLocation, validatedLocation)) {
                uploadedObjects.remove(validatedLocation);
                addObjectIfAbsent(uploadedObjects, returnedLocation);
                validatedLocation = returnedLocation;
            }
        }

        DataIngestion ingestion = new DataIngestion();
        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);
        ingestion.setIngStatus(report.status().equals("REJEITADO") ? IngestionStatus.ERRO : IngestionStatus.CONCLUIDO);
        ingestion = ingestionRepository.save(ingestion);

        File file = new File();
        file.setIngestion(ingestion);
        file.setName(filename);
        file.setFormatFile(format);
        file.setSize((long) content.length);
        file.setHash(hash);
        file.setLocation(rawLocation);
        file = fileRepository.save(file);

        Version rawVersion = new Version();
        rawVersion.setFile(file);
        rawVersion.setVersionNumber(1);
        rawVersion.setStatus(VersionStatus.BRUTA);
        rawVersion.setLocation(rawLocation);
        versionRepository.save(rawVersion);

        if (validatedLocation != null) {
            Version validatedVersion = new Version();
            validatedVersion.setFile(file);
            validatedVersion.setVersionNumber(2);
            // VersionStatus has no separate validated value in the current schema.
            validatedVersion.setStatus(VersionStatus.BRUTA);
            validatedVersion.setLocation(validatedLocation);
            versionRepository.save(validatedVersion);
        }

        if (dataset != null) {
            Long savedFileId = file.getId();
            boolean alreadyAssociated = dataset.getFiles().stream()
                    .anyMatch(existing -> Objects.equals(existing.getId(), savedFileId));
            if (!alreadyAssociated) {
                dataset.getFiles().add(file);
            }
        }

        if (report.rejectedRecords() > 0 || report.status().equals("REJEITADO")) {
            Quarantine quarantine = new Quarantine();
            quarantine.setIngestion(ingestion);
            quarantine.setFile(file);
            quarantine.setStatus(report.status().equals("REJEITADO") ? "ARQUIVO_REJEITADO" : "REGISTROS_REJEITADOS");
            quarantine.setReason(buildQuarantineReason(records));
            quarantineRepository.save(quarantine);
        }

        registerCleanup(uploadedObjects);
        return new FileResponseDTO(file.getId(), file.getName(), file.getFormatFile(), file.getSize(),
                file.getHash(), file.getLocation(), ingestion.getId(), file.getReceivedDate(), user.getId(), report);
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findBySourceId(Long sourceId) {
        return fileRepository.findByIngestion_Dictionary_Source_IdOrderByIdDesc(sourceId)
                .stream().map(this::toResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> findByDatasetId(Long datasetId) {
        datasetService.findEntityById(datasetId);
        return fileRepository.findByDatasetId(datasetId).stream().map(this::toResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<File> findEntitiesByDatasetId(Long datasetId) {
        datasetService.findEntityById(datasetId);
        return fileRepository.findByDatasetId(datasetId);
    }

    @Transactional(readOnly = true)
    public File findEntityById(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
    }

    @Transactional(readOnly = true)
    public FileValidationReport validationReport(Long fileId) throws IOException {
        File file = findEntityById(fileId);
        String json = bucketService.readValidationReport(file.getLocation());
        try {
            return objectMapper.readValue(json, FileValidationReport.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Relatório de validação armazenado está inválido.", e);
        }
    }

    @Transactional(readOnly = true)
    public CollectedData.Metadata collectionMetadata(Long fileId) throws IOException {
        File file = findEntityById(fileId);
        String json = bucketService.readCollectionMetadata(file.getLocation());
        try {
            return objectMapper.readValue(json, CollectedData.Metadata.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Metadados de coleta armazenados estão inválidos.", e);
        }
    }

    String calculateHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        return calculateHash(file.getBytes());
    }

    private String calculateHash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não está disponível nesta JVM.", e);
        }
    }

    private String extractFormat(String filename) {
        if (filename == null || !filename.contains(".")) return null;
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "arquivo_sem_nome";
        String noAccent = Normalizer.normalize(filename, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccent.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void validateFilenameAndFormat(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("Nome do arquivo deve ter no máximo 200 caracteres.");
        }
        String format = extractFormat(filename);
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("O arquivo precisa possuir uma extensão válida.");
        }
        if (format.length() > MAX_FORMAT_LENGTH) {
            throw new IllegalArgumentException("A extensão do arquivo deve ter no máximo 10 caracteres.");
        }
    }

    private String contentTypeFor(String format) {
        if (format == null) return "application/octet-stream";
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "csv" -> "text/csv";
            case "json", "geojson" -> "application/geo+json";
            default -> "application/octet-stream";
        };
    }

    private String buildQuarantineReason(List<RecordValidationResult> records) {
        String reason = records.stream().filter(r -> !r.valid())
                .flatMap(r -> r.errors().stream()).distinct().reduce((a, b) -> a + "; " + b)
                .orElse("Arquivo rejeitado pela validação.");
        byte[] bytes = reason.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= MAX_QUARANTINE_REASON_BYTES) return reason;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < reason.length(); i++) {
            String next = result.toString() + reason.charAt(i);
            if (next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_QUARANTINE_REASON_BYTES) break;
            result.append(reason.charAt(i));
        }
        return result.toString();
    }

    private FileResponseDTO toResponseDTO(File file) {
        return new FileResponseDTO(file.getId(), file.getName(), file.getFormatFile(), file.getSize(),
                file.getHash(), file.getLocation(), file.getIngestion().getId(), file.getReceivedDate(),
                file.getIngestion().getUser().getId());
    }

    private void addObjectIfAbsent(List<String> objects, String objectName) {
        if (objectName != null && !objects.contains(objectName)) objects.add(objectName);
    }

    private void registerCleanup(List<String> uploadedObjects) {
        if (uploadedObjects == null || uploadedObjects.isEmpty()) return;
        List<String> objectsToClean = List.copyOf(uploadedObjects);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        for (String object : objectsToClean) {
                            try { bucketService.deleteObject(object); }
                            catch (RuntimeException ignored) { /* Best-effort cleanup; preserve original failure. */ }
                        }
                    }
                }
            });
        }
    }
}
