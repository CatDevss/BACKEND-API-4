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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import catdevs.georuraldatahub.processing.GeospatialReader;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.IngestionRepository;
import catdevs.georuraldatahub.repository.QuarantineRepository;
import catdevs.georuraldatahub.repository.VersionRepository;
import catdevs.georuraldatahub.validation.FileValidationReport;
import catdevs.georuraldatahub.validation.FileValidationService;
import catdevs.georuraldatahub.validation.RecordValidationResult;

/**
 * Serviço responsável pela ingestão, validação e consulta de arquivos.
 */
@Service
public class FileService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FileService.class);

    private static final int MAX_FILENAME_BYTES = 200;
    private static final int MAX_FORMAT_BYTES = 10;
    private static final int MAX_QUARANTINE_REASON_BYTES = 500;

    private static final Set<String> GEOSPATIAL_FORMATS =
            Set.of("zip", "gpkg");

    private static final Set<String> GEOJSON_FORMATS =
            Set.of("json", "geojson");

    private static final Set<String> RASTER_FORMATS =
            Set.of("tif", "tiff");

    private final FileRepository fileRepository;
    private final VersionRepository versionRepository;
    private final IngestionRepository ingestionRepository;
    private final DatasetService datasetService;
    private final UserService userService;
    private final DictionaryService dictionaryService;
    private final BucketService bucketService;
    private final FileValidationService validationService;
    private final QuarantineRepository quarantineRepository;
    private final GeospatialReader geospatial;

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
            QuarantineRepository quarantineRepository,
            GeospatialReader geospatial) {

        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.ingestionRepository = ingestionRepository;
        this.datasetService = datasetService;
        this.userService = userService;
        this.dictionaryService = dictionaryService;
        this.bucketService = bucketService;
        this.validationService = validationService;
        this.quarantineRepository = quarantineRepository;
        this.geospatial = geospatial;
    }

    // =========================================================
    // UPLOAD MANUAL
    // =========================================================

    /**
     * Envia um arquivo para uma fonte, sem associá-lo a um dataset.
     * Utiliza a versão mais recente do dicionário.
     */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadToSource(
            MultipartFile multipartFile,
            Long sourceId,
            Long userId) throws Exception {

        return uploadToSource(
                multipartFile,
                sourceId,
                userId,
                null);
    }

    /**
     * Envia um arquivo para uma fonte, permitindo selecionar o dicionário.
     */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadToSource(
            MultipartFile multipartFile,
            Long sourceId,
            Long userId,
            Long dictionaryId) throws Exception {

        return ingest(
                multipartFile,
                null,
                userId,
                sourceId,
                null,
                dictionaryId,
                DataIngestionType.UPLOAD,
                null);
    }

    /**
     * Envia um arquivo e o associa ao dataset informado.
     */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadFile(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId,
            Long sourceId) throws Exception {

        return uploadFile(
                multipartFile,
                datasetId,
                userId,
                sourceId,
                null);
    }

    /**
     * Envia um arquivo para um dataset, permitindo selecionar o dicionário.
     */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadFile(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId,
            Long sourceId,
            Long dictionaryId) throws Exception {

        return ingest(
                multipartFile,
                datasetId,
                userId,
                sourceId,
                null,
                dictionaryId,
                DataIngestionType.UPLOAD,
                null);
    }

    // =========================================================
    // COLETA AUTOMÁTICA
    // =========================================================

    /**
     * Processa o resultado de uma coleta automática.
     */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadCollectedFile(
            CollectedData collected,
            Long datasetId,
            Long userId,
            Long sourceId) throws Exception {

        return uploadCollectedFile(
                collected,
                datasetId,
                userId,
                sourceId,
                null,
                null);
    }

    /**
     * Processa uma coleta automática, permitindo escolher o dicionário
     * e identificar o conector responsável.
     */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadCollectedFile(
            CollectedData collected,
            Long datasetId,
            Long userId,
            Long sourceId,
            Long dictionaryId,
            String connector) throws Exception {

        if (collected == null
                || collected.content() == null
                || collected.content().length == 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A coleta não retornou conteúdo.");
        }

        String filename = collected.filename();
        String format = collected.format();

        if (format == null || format.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A coleta não informou o formato do arquivo.");
        }

        String contentType = format.equalsIgnoreCase("csv")
                ? "text/csv"
                : "application/geo+json";

        MultipartFile multipartFile = new DownloadedMultipartFile(
                filename,
                contentType,
                collected.content());

        return ingest(
                multipartFile,
                datasetId,
                userId,
                sourceId,
                collected,
                dictionaryId,
                DataIngestionType.API,
                connector);
    }

    // =========================================================
    // FLUXO PRINCIPAL DE INGESTÃO
    // =========================================================

    /**
     * Fluxo compartilhado por uploads manuais e coletas automáticas.
     *
     * Preserva o arquivo original, gera relatório de validação,
     * armazena metadados geoespaciais quando aplicável e registra
     * a ingestão, o arquivo, as versões e a quarentena.
     */
    private FileResponseDTO ingest(
            MultipartFile multipartFile,
            Long datasetId,
            Long userId,
            Long sourceId,
            CollectedData collected,
            Long dictionaryId,
            DataIngestionType ingestionType,
            String connector) throws Exception {

        validateMultipartFile(multipartFile);

        String filename = sanitizeAndValidateFilename(
                multipartFile.getOriginalFilename());

        String format = formatOf(filename);

        User user = userService.findEntityById(userId);

        Dictionary dictionary =
                dictionaryService.acquireForValidation(
                        sourceId,
                        dictionaryId);

        String hash = calculateHash(multipartFile);

        if (fileRepository
                .existsByHashAndIngestion_Dictionary_Source_Id(
                        hash,
                        sourceId)) {

            throw new DuplicateFileException(
                    "Este conteúdo já foi enviado para esta fonte.");
        }

        Dataset dataset = datasetId == null
                ? null
                : datasetService.findLockedEntityById(datasetId);

        /*
         * Prepara o arquivo geoespacial, quando necessário.
         */
        GeospatialReader.Result prepared = null;

        List<RecordValidationResult> preparationErrors = null;

        boolean raster = RASTER_FORMATS.contains(format);

        int sourceSrid = 0;
        boolean declaredCrs = false;

        if (collected != null
                && collected.metadata() != null
                && "shapefile.zip".equals(
                        collected.metadata().originFormat())
                && Set.of(
                        "IBGE_BIOMAS",
                        "IBGE_MUNICIPIOS")
                        .contains(collected.metadata().product())) {

            /*
             * Os dados desses produtos do IBGE já foram verificados
             * como SIRGAS 2000 geográfico.
             */
            sourceSrid = 4674;

        } else if (GEOJSON_FORMATS.contains(format)) {

            try (InputStream input =
                    multipartFile.getInputStream()) {

                JsonNode root = mapper.readTree(input);

                declaredCrs = root != null
                        && root.hasNonNull("crs")
                        && Set.of(
                                "FeatureCollection",
                                "Feature")
                                .contains(
                                        root.path("type").asText());

            } catch (JsonProcessingException ignored) {
                /*
                 * A validação abaixo produzirá o relatório de erro
                 * para conteúdo JSON inválido.
                 */
            }
        }

        boolean requiresGeospatialProcessing =
                raster
                || GEOSPATIAL_FORMATS.contains(format)
                || sourceSrid != 0
                || declaredCrs;

        if (requiresGeospatialProcessing) {

            try (InputStream input =
                    multipartFile.getInputStream()) {

                String readerFormat =
                        format.equals("json")
                                ? "geojson"
                                : format;

                prepared = geospatial.read(
                        input,
                        readerFormat,
                        sourceSrid);

            } catch (ResponseStatusException e) {

                if (e.getStatusCode().value() != 422) {
                    throw e;
                }

                preparationErrors = List.of(
                        RecordValidationResult.invalid(
                                -1,
                                "",
                                List.of(
                                        "INVALID_GEOSPATIAL_FILE: "
                                        + e.getReason())));
            }
        }

        /*
         * Valida o conteúdo preparado ou o arquivo original.
         */
        List<RecordValidationResult> records;

        try (InputStream input =
                prepared == null
                        ? multipartFile.getInputStream()
                        : new ByteArrayInputStream(
                                prepared.content())) {

            if (preparationErrors != null) {

                records = preparationErrors;

            } else {

                String validationFormat;

                if (prepared == null) {
                    validationFormat = format;
                } else if (raster) {
                    validationFormat = "json";
                } else {
                    validationFormat = "geojson";
                }

                records = validationService.validate(
                        input,
                        validationFormat,
                        dictionaryService.rules(dictionary));
            }
        }

        FileValidationReport report =
                FileValidationReport.of(
                        dictionary.getId(),
                        dictionary.getVersion(),
                        records);

        String reportJson =
                mapper.writeValueAsString(report);

        /*
         * Gera o conteúdo aceito pela validação.
         */
        byte[] accepted;

        if (preparationErrors != null) {

            accepted = new byte[0];

        } else if (raster) {

            accepted = report.status().equals("VALIDO")
                    ? mapper.writeValueAsBytes(prepared.metadata())
                    : new byte[0];

        } else {

            try (InputStream input =
                    prepared == null
                            ? multipartFile.getInputStream()
                            : new ByteArrayInputStream(
                                    prepared.content())) {

                accepted = validationService.acceptedContent(
                        input,
                        prepared == null ? format : "geojson",
                        records);
            }
        }

        String validatedFilename =
                filename
                + (prepared == null
                        || GEOJSON_FORMATS.contains(format)
                                ? ""
                                : raster
                                        ? ".raster.json"
                                        : ".geojson");

        /*
         * Faz upload do arquivo bruto e registra os objetos enviados
         * para possibilitar limpeza em caso de rollback.
         */
        String location = bucketService.uploadToSourceRawZone(
                multipartFile,
                sourceId,
                hash,
                filename);

        List<String> uploaded = new ArrayList<>();

        uploaded.add(location);

        registerRollbackCleanup(uploaded);

        /*
         * Salva o relatório de validação.
         */
        String reportLocation =
                bucketService.uploadValidationReport(
                        location,
                        reportJson);

        uploaded.add(reportLocation);

        /*
         * Salva metadados geoespaciais, quando existentes.
         */
        if (prepared != null) {

            String metadataLocation =
                    location + ".geospatial.json";

            uploaded.add(metadataLocation);

            bucketService.uploadCollectionArtifact(
                    metadataLocation,
                    mapper.writeValueAsBytes(
                            prepared.metadata()),
                    "application/json");
        }

        /*
         * Preserva os arquivos originais e os metadados da coleta.
         */
        if (collected != null) {

            if (collected.originals() != null) {

                int index = 0;

                for (CollectedData.Original original
                        : collected.originals()) {

                    String originalName =
                            sanitizeFilename(
                                    original.filename());

                    if (originalName.isBlank()) {
                        originalName = "arquivo_sem_nome";
                    }

                    String key =
                            location
                            + ".originals/"
                            + String.format(
                                    Locale.ROOT,
                                    "%05d_",
                                    index++)
                            + originalName;

                    uploaded.add(key);

                    bucketService.uploadCollectionArtifact(
                            key,
                            original.content(),
                            original.contentType());
                }
            }

            if (collected.metadata() != null) {

                String metadataLocation =
                        location + ".collection.json";

                uploaded.add(metadataLocation);

                bucketService.uploadCollectionArtifact(
                        metadataLocation,
                        mapper.writeValueAsBytes(
                                collected.metadata()),
                        "application/json");
            }
        }

        /*
         * Arquivos inválidos são copiados para a zona de quarentena.
         * O original bruto permanece preservado para auditoria.
         */
        if (!report.status().equals("VALIDO")) {

            String quarantineLocation =
                    BucketService.quarantineLocation(location);

            uploaded.add(quarantineLocation);

            bucketService.uploadToQuarantineZone(
                    multipartFile,
                    location);

            String quarantineReportLocation =
                    quarantineLocation + ".validation.json";

            uploaded.add(quarantineReportLocation);

            bucketService.uploadValidationReport(
                    quarantineLocation,
                    reportJson);
        }

        /*
         * Registra a ingestão.
         */
        IngestionStatus status =
                report.status().equals("REJEITADO")
                        ? IngestionStatus.ERRO
                        : IngestionStatus.CONCLUIDO;

        DataIngestion ingestion = new DataIngestion();

        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);
        ingestion.setType(ingestionType);
        ingestion.setConnector(connector);
        ingestion.setIngStatus(status);

        ingestion = ingestionRepository.save(ingestion);

        /*
         * Registra o arquivo.
         */
        File file = new File();

        file.setIngestion(ingestion);
        file.setName(filename);
        file.setFormatFile(format);
        file.setSize(multipartFile.getSize());
        file.setHash(hash);
        file.setLocation(location);

        file = fileRepository.save(file);

        /*
         * Guarda o ID em uma variável final para uso na lambda abaixo.
         */
        final Long savedFileId = file.getId();

        /*
         * Versão 1: conteúdo bruto.
         */
        saveVersion(file, location, 1);

        /*
         * Registra a quarentena no banco quando existem rejeições.
         */
        persistQuarantineIfNeeded(
                file,
                ingestion,
                report);

        /*
         * Versão 2: conteúdo filtrado, caso exista.
         */
        if (accepted.length > 0) {

            String acceptedLocation =
                    bucketService.uploadSourceValidatedContent(
                            accepted,
                            sourceId,
                            hash,
                            validatedFilename);

            uploaded.add(acceptedLocation);

            saveVersion(
                    file,
                    acceptedLocation,
                    2);
        }

        /*
         * Associa o arquivo ao dataset, quando informado.
         */
        if (dataset != null) {

            if (dataset.getFiles() == null) {
                throw new IllegalStateException(
                        "O dataset não possui uma coleção de arquivos inicializada.");
            }

            boolean alreadyAssociated =
                    dataset.getFiles()
                            .stream()
                            .anyMatch(existing ->
                                    existing.getId() != null
                                    && existing.getId().equals(
                                            savedFileId));

            if (!alreadyAssociated) {
                dataset.getFiles().add(file);
            }
        }

        return toResponseDTO(file, report);
    }

    // =========================================================
    // HASH
    // =========================================================

    /**
     * Calcula o hash SHA-256 de um arquivo multipart.
     */
    String calculateHash(MultipartFile file)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        try (InputStream input = file.getInputStream();
                DigestInputStream digestInput =
                        new DigestInputStream(input, digest)) {

            byte[] buffer = new byte[8192];

            while (digestInput.read(buffer) != -1) {
                // A leitura atualiza o digest automaticamente.
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Calcula o hash SHA-256 de um array de bytes.
     */
    private String sha256(byte[] content)
            throws NoSuchAlgorithmException {

        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(content));
    }

    // =========================================================
    // VERSÕES E QUARENTENA
    // =========================================================

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

    private void persistQuarantineIfNeeded(
            File file,
            DataIngestion ingestion,
            FileValidationReport report) {

        List<RecordValidationResult> rejected =
                report.records()
                        .stream()
                        .filter(record -> !record.valid())
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

        quarantine.setReason(
                quarantineSummary(report));

        quarantineRepository.save(quarantine);
    }

    /**
     * Cria um resumo do motivo da quarentena.
     */
    private String quarantineSummary(
            FileValidationReport report) {

        String codes = report.records()
                .stream()
                .flatMap(record -> record.errors().stream())
                .map(error ->
                        error.split(":", 2)[0]
                                .replaceAll("[^A-Z_]", ""))
                .filter(code -> !code.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));

        String summary =
                report.status()
                + "; validos=" + report.validRecords()
                + "; rejeitados=" + report.rejectedRecords()
                + "; codigos=" + codes;

        return truncateUtf8(
                summary,
                MAX_QUARANTINE_REASON_BYTES);
    }

    // =========================================================
    // LIMPEZA DE OBJETOS NO ROLLBACK
    // =========================================================

    /**
     * Registra a remoção dos objetos enviados ao bucket caso a
     * transação seja revertida.
     */
    private void registerRollbackCleanup(
            List<String> artifacts) {

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status) {

                                if (status == STATUS_ROLLED_BACK) {
                                    cleanup(artifacts);
                                }
                            }
                        });
    }

    private void cleanup(List<String> artifacts) {

        for (int i = artifacts.size() - 1; i >= 0; i--) {

            try {
                bucketService.deleteObject(
                        artifacts.get(i));

            } catch (RuntimeException cleanupError) {

                LOGGER.error(
                        "Falha ao remover objeto {} do bucket.",
                        artifacts.get(i),
                        cleanupError);
            }
        }
    }

    // =========================================================
    // VALIDAÇÃO DE NOME E FORMATO
    // =========================================================

    private void validateMultipartFile(
            MultipartFile file) {

        if (file == null || file.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O arquivo é obrigatório e não pode estar vazio.");
        }
    }

    /**
     * Sanitiza e valida o nome antes de utilizá-lo.
     * O limite considera bytes UTF-8.
     */
    private String sanitizeAndValidateFilename(
            String original) {

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
                    "Formato do arquivo excede o limite de 10 bytes.");
        }

        return filename;
    }

    /**
     * Remove acentos e substitui caracteres não permitidos por "_".
     */
    private String sanitizeFilename(String filename) {

        if (filename == null || filename.isBlank()) {
            return "";
        }

        String normalized = Normalizer
                .normalize(
                        filename,
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized.replaceAll(
                "[^a-zA-Z0-9._-]",
                "_");
    }

    /**
     * Extrai a extensão do arquivo em letras minúsculas.
     */
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

    /**
     * Trunca uma string respeitando o limite de bytes UTF-8.
     */
    private String truncateUtf8(
            String value,
            int maxBytes) {

        StringBuilder result = new StringBuilder();

        int used = 0;

        for (int i = 0; i < value.length();) {

            int codePoint = value.codePointAt(i);

            String character =
                    new String(
                            Character.toChars(codePoint));

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

    // =========================================================
    // CONSULTAS
    // =========================================================

    /**
     * Lista os arquivos de uma fonte, do mais recente para o mais antigo.
     */
    @Transactional(readOnly = true)
    public List<FileResponseDTO> findBySourceId(
            Long sourceId) {

        return fileRepository
                .findByIngestion_Dictionary_Source_IdOrderByIdDesc(
                        sourceId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /**
     * Lista os arquivos associados a um dataset.
     */
    @Transactional(readOnly = true)
    public List<FileResponseDTO> findByDatasetId(
            Long datasetId) {

        Dataset dataset =
                datasetService.findEntityById(datasetId);

        if (dataset.getFiles() == null) {
            return List.of();
        }

        return dataset.getFiles()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /**
     * Retorna as entidades de arquivo associadas a um dataset.
     */
    @Transactional(readOnly = true)
    public List<File> findEntitiesByDatasetId(
            Long datasetId) {

        datasetService.findEntityById(datasetId);

        return fileRepository.findByDatasetId(datasetId);
    }

    /**
     * Busca uma entidade de arquivo pelo ID.
     */
    @Transactional(readOnly = true)
    public File findEntityById(Long fileId) {

        return fileRepository.findById(fileId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Arquivo não encontrado."));
    }

    // =========================================================
    // RELATÓRIOS E METADADOS
    // =========================================================

    /**
     * Recupera o relatório de validação armazenado no bucket.
     */
    @Transactional(readOnly = true)
    public FileValidationReport validationReport(
            Long fileId) throws IOException {

        File file = findEntityById(fileId);

        String json =
                bucketService.readValidationReport(
                        file.getLocation());

        try {

            return mapper.readValue(
                    json,
                    FileValidationReport.class);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "O relatório de validação armazenado está inválido.",
                    e);
        }
    }

    /**
     * Recupera os metadados da coleta automática.
     */
    @Transactional(readOnly = true)
    public CollectedData.Metadata collectionMetadata(
            Long fileId) throws IOException {

        File file = findEntityById(fileId);

        String json =
                bucketService.readCollectionMetadata(
                        file.getLocation());

        try {

            return mapper.readValue(
                    json,
                    CollectedData.Metadata.class);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Os metadados da coleta armazenados estão inválidos.",
                    e);
        }
    }

    /**
     * Recupera os metadados geoespaciais de um arquivo.
     */
    @Transactional(readOnly = true)
    public JsonNode geospatialMetadata(
            Long fileId) throws IOException {

        File file = findEntityById(fileId);

        String json =
                bucketService.readGeospatialMetadata(
                        file.getLocation());

        try {

            return mapper.readTree(json);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Os metadados geoespaciais armazenados estão inválidos.",
                    e);
        }
    }

    // =========================================================
    // DTO
    // =========================================================

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