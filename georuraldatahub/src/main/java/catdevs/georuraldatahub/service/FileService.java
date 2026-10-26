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

/** Serviço de ingestão e consulta de arquivos. */
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
    private final catdevs.georuraldatahub.processing.GeospatialReader geospatial;

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
            catdevs.georuraldatahub.processing.GeospatialReader geospatial) {

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

     /** Envia um arquivo para uma fonte, sem associar a nenhum dataset. */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadToSource(MultipartFile file, Long sourceId, Long userId) throws Exception {
        return uploadToSource(file, sourceId, userId, null);
    }

    /** Igual ao anterior, mas permite escolher o dicionário. */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadToSource(MultipartFile file, Long sourceId, Long userId, Long dictionaryId) throws Exception {
        return ingest(file, null, userId, sourceId, null, dictionaryId, DataIngestionType.UPLOAD, null);
    }

     /** Endpoint de compatibilidade: envia para a fonte e já associa ao dataset. */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadFile(MultipartFile multipartFile, Long datasetId, Long userId, Long sourceId)
            throws Exception {
        return uploadFile(multipartFile, datasetId, userId, sourceId, null);
    }

    /** Igual ao anterior, mas permite escolher o dicionário. */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadFile(MultipartFile multipartFile, Long datasetId, Long userId, Long sourceId,
            Long dictionaryId) throws Exception {
        return ingest(multipartFile, datasetId, userId, sourceId, null, dictionaryId, DataIngestionType.UPLOAD, null);
    }

     /** Ingere um arquivo vindo de coleta automática e associa ao dataset. */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadCollectedFile(CollectedData collected,
            Long datasetId, Long userId, Long sourceId) throws Exception {
        return uploadCollectedFile(collected, datasetId, userId, sourceId, null, null);
    }
    
      /** Igual ao anterior, mas permite escolher o dicionário. */
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDTO uploadCollectedFile(CollectedData collected,
            Long datasetId, Long userId, Long sourceId, Long dictionaryId, String connector) throws Exception {
        var file = new DownloadedMultipartFile(collected.filename(),
                collected.format().equals("csv") ? "text/csv" : "application/geo+json", collected.content());
        return ingest(file, datasetId, userId, sourceId, collected, dictionaryId, DataIngestionType.API, connector);
    }

    /** Fluxo principal: valida, guarda no bucket e salva ingestão, arquivo e versões. */
    private FileResponseDTO ingest(MultipartFile multipartFile, Long datasetId, Long userId, Long sourceId,
            CollectedData collected, Long dictionaryId, DataIngestionType ingestionType, String connector) throws Exception {
        String hash = calculateHash(multipartFile);

        User user = userService.findEntityById(userId);
         // Bloqueia a fonte e pega o dicionário; se não informado, usa a versão mais recente.
        Dictionary dictionary = dictionaryService.acquireForValidation(sourceId, dictionaryId);
        // O bloqueio da fonte também evita duplicidade entre uploads e coletas agendadas.
        if (fileRepository.existsByHashAndIngestion_Dictionary_Source_Id(hash, sourceId)) {
            throw new DuplicateFileException("Este conteúdo já foi enviado para esta fonte. Selecione o arquivo existente para compor o conjunto.");
        }
        Dataset dataset = datasetId == null ? null : datasetService.findLockedEntityById(datasetId);

        String sanitizedFilename = sanitizeFilename(multipartFile.getOriginalFilename());
        String format = extractFormat(sanitizedFilename);
        if (sanitizedFilename.length() > 200) throw new IllegalArgumentException("Nome do arquivo excede 200 caracteres.");
        if (format == null || format.isBlank() || format.length() > 10)
            throw new IllegalArgumentException("Informe um arquivo com extensão de até 10 caracteres.");

        var rules = dictionaryService.rules(dictionary);
        catdevs.georuraldatahub.processing.GeospatialReader.Result prepared = null;
        List<RecordValidationResult> preparationErrors = null;
        boolean raster = java.util.Set.of("tif", "tiff").contains(format);
        int sourceSrid = 0;
        boolean declaredCrs = false;
        if (collected != null && collected.metadata() != null
                && "shapefile.zip".equals(collected.metadata().originFormat())
                && java.util.Set.of("IBGE_BIOMAS", "IBGE_MUNICIPIOS").contains(collected.metadata().product())) {
            // O leitor IBGE já verificou SIRGAS 2000 geográfico; converter para GeoJSON não reprojeta o datum.
            sourceSrid = 4674;
        } else if (java.util.Set.of("geojson", "json").contains(format)) {
            try (InputStream input = multipartFile.getInputStream()) {
                var root = mapper.readTree(input);
                declaredCrs = root != null && root.hasNonNull("crs")
                        && java.util.Set.of("FeatureCollection", "Feature").contains(root.path("type").asText());
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // O validador abaixo registra o erro estrutural e preserva o original na quarentena.
            }
        }
        if (raster || java.util.Set.of("zip", "gpkg").contains(format) || sourceSrid != 0 || declaredCrs) {
            try (InputStream input = multipartFile.getInputStream()) {
                prepared = geospatial.read(input, format.equals("json") ? "geojson" : format, sourceSrid);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() != 422) throw e;
                preparationErrors = List.of(RecordValidationResult.invalid(-1, "",
                        List.of("INVALID_GEOSPATIAL_FILE: " + e.getReason())));
            }
        }
        List<RecordValidationResult> records;
        try (InputStream input = prepared == null ? multipartFile.getInputStream()
                : new java.io.ByteArrayInputStream(prepared.content())) {
            records = preparationErrors == null
                    ? validationService.validate(input, prepared == null ? format : raster ? "json" : "geojson", rules)
                    : preparationErrors;
        }
        var report = FileValidationReport.of(dictionary.getId(), dictionary.getVersion(), records);
        String reportJson = mapper.writeValueAsString(report);
        byte[] accepted;
        if (!report.status().equals("VALIDO")) {
            accepted = new byte[0];
        } else if (raster) {
            accepted = mapper.writeValueAsBytes(prepared.metadata());
        } else {
            try (InputStream input = prepared == null ? multipartFile.getInputStream()
                    : new java.io.ByteArrayInputStream(prepared.content())) {
                accepted = validationService.acceptedContent(input, prepared == null ? format : "geojson", records);
            }
        }
        String workingFilename = sanitizedFilename + (prepared == null || java.util.Set.of("json", "geojson").contains(format)
                ? "" : raster ? ".raster.json" : ".geojson");
        // O original permanece na zona bruta; somente uma cópia aprovada pode avançar.
        String location = bucketService.uploadToSourceRawZone(multipartFile, sourceId, hash, sanitizedFilename);

        List<String> uploaded = new ArrayList<>();
        // O rollback remove apenas cópias de trabalho; o original bruto é permanente.
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
        bucketService.uploadValidationReport(location, reportJson);

         if (prepared != null) {
            bucketService.uploadCollectionArtifact(location + ".geospatial.json",
                    mapper.writeValueAsBytes(prepared.metadata()), "application/json");
        }

        // Em coletas, guarda também os arquivos originais e os metadados.
        if (collected != null) {
            int index = 0;
            for (var original : collected.originals()) {
                String key = location + ".originals/" + String.format(java.util.Locale.ROOT, "%05d_", index++)
                        + sanitizeFilename(original.filename());
                bucketService.uploadCollectionArtifact(key, original.content(), original.contentType());
            }
            bucketService.uploadCollectionArtifact(location + ".collection.json",
                    mapper.writeValueAsBytes(collected.metadata()), "application/json");
        }

        String quarantineLocation = null;
        if (!report.status().equals("VALIDO")) {
            // Qualquer rejeição bloqueia a cópia inteira; somente outra validação poderá liberá-la.
            // A zona física acompanha a quarentena do banco. O original bruto e o histórico ficam preservados.
            quarantineLocation = BucketService.quarantineLocation(location);
            // Registrar antes do envio também cobre falha de rede após a gravação no OCI.
            uploaded.add(quarantineLocation);
            bucketService.uploadToQuarantineZone(multipartFile, location);
            uploaded.add(quarantineLocation + ".validation.json");
            bucketService.uploadValidationReport(quarantineLocation, reportJson);
        }

        // Qualquer rejeição, inclusive parcial, marca a ingestão com erro.
        DataIngestion ingestion = new DataIngestion();
        ingestion.setDictionary(dictionary);
        ingestion.setUser(user);
        ingestion.setType(ingestionType);
        ingestion.setConnector(connector);
        ingestion.setIngStatus(report.status().equals("VALIDO") ? IngestionStatus.CONCLUIDO : IngestionStatus.ERRO);
        ingestion = ingestionRepository.save(ingestion);

        // Salva o arquivo.
        File file = new File();
        file.setIngestion(ingestion);
        file.setName(sanitizedFilename);
        file.setFormatFile(format);
        file.setSize(multipartFile.getSize());
        file.setHash(hash);
        file.setLocation(location);
        file = fileRepository.save(file);

         // Versão 1: conteúdo bruto.
        Version version = new Version();
        version.setFile(file);
        version.setVersionNumber(1);
        version.setStatus(VersionStatus.BRUTA);
        version.setLocation(location);
        versionRepository.save(version);

          // Se não for totalmente válido, vai para a quarentena.
        if (!report.status().equals("VALIDO")) {
            var quarantine = new Quarantine();
            quarantine.setIngestion(ingestion);
            quarantine.setFile(file);
            quarantine.setStatus(report.status().equals("REJEITADO") ? "ARQUIVO_REJEITADO" : "REGISTROS_REJEITADOS");
            quarantine.setReason(quarantineSummary(report));
            quarantineRepository.save(quarantine);
        }

        // A versão 1 representa o original. A versão 2 rastreia a cópia de trabalho no bucket.
        Version working = new Version();
        working.setFile(file);
        working.setVersionNumber(2);
        if (quarantineLocation != null) {
            working.setStatus(VersionStatus.QUARENTENA);
            working.setLocation(quarantineLocation);
        } else if (raster) {
            String rasterLocation = BucketService.workingLocation(location, "tratada/");
            uploaded.add(rasterLocation);
            bucketService.copyWorkingObject(location, rasterLocation);
            uploaded.add(rasterLocation + ".raster.json");
            bucketService.uploadSourceTreatedContent(accepted, sourceId, hash, workingFilename);
            working.setStatus(VersionStatus.TRATADA);
            working.setLocation(rasterLocation);
        } else {
            if (accepted.length == 0) throw new IllegalArgumentException("Arquivo sem conteúdo aprovado para gerar a cópia de trabalho.");
            String expectedLocation = BucketService.workingLocation(location, "tratada/")
                    + (prepared == null || java.util.Set.of("json", "geojson").contains(format) ? "" : ".geojson");
            uploaded.add(expectedLocation);
            String acceptedLocation = bucketService.uploadSourceTreatedContent(accepted, sourceId, hash, workingFilename);
            working.setStatus(VersionStatus.TRATADA);
            working.setLocation(acceptedLocation);
        }
        if (quarantineLocation == null) {
            uploaded.add(working.getLocation() + ".validation.json");
            bucketService.uploadValidationReport(working.getLocation(), reportJson);
        }
        versionRepository.save(working);

         // Associa ao dataset, se informado.
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


    /** Calcula o hash SHA-256 do arquivo, em hexadecimal. */
    String calculateHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = file.getInputStream();
                DigestInputStream dis = new DigestInputStream(is, digest)) {
            byte[] buffer = new byte[8192];
            // Só ler já atualiza o hash, por isso o laço fica vazio.
            while (dis.read(buffer) != -1) {
            }

        }

          // Converte os bytes do hash para texto hexadecimal.
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

      /** Retorna a extensão do arquivo em minúsculas, ou null se não tiver. */
    private String extractFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase(java.util.Locale.ROOT);
    }

   /**
     * Remove acentos e troca espaços/caracteres especiais por "_",
     * pra gerar um nome seguro pra usar como chave de objeto no bucket.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "arquivo_sem_nome";
        }
        String semAcento = Normalizer.normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

     /** Monta o resumo do motivo da quarentena (só ASCII, máximo de 500 caracteres). */
    private String quarantineSummary(FileValidationReport report) {
        String codes = report.records().stream().flatMap(r -> r.errors().stream())
                .map(error -> error.split(":", 2)[0].replaceAll("[^A-Z_]", ""))
                .filter(code -> !code.isEmpty()).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(","));
        String summary = report.status() + "; validos=" + report.validRecords()
                + "; rejeitados=" + report.rejectedRecords() + "; codigos=" + codes;
        return summary.substring(0, Math.min(summary.length(), 500));
    }

     /** Busca o relatório de validação de um arquivo. */
    @Transactional(readOnly = true)
    public FileValidationReport validationReport(Long fileId) throws Exception {
        File file = fileRepository.findById(fileId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        return mapper.readValue(bucketService.readValidationReport(file.getLocation()), FileValidationReport.class);
    }

       /** Busca os metadados da coleta de um arquivo. */
    @Transactional(readOnly = true)
    public CollectedData.Metadata collectionMetadata(Long fileId) throws Exception {
        File file = fileRepository.findById(fileId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        return mapper.readValue(bucketService.readCollectionMetadata(file.getLocation()),
                CollectedData.Metadata.class);
    }

      @Transactional(readOnly = true)
    public com.fasterxml.jackson.databind.JsonNode geospatialMetadata(Long fileId) throws Exception {
        File file = fileRepository.findById(fileId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        return mapper.readTree(bucketService.readGeospatialMetadata(file.getLocation()));
    }

    /** Lista os arquivos de uma fonte, do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public List<FileResponseDTO> findBySourceId(Long sourceId) {
        return fileRepository.findByIngestion_Dictionary_Source_IdOrderByIdDesc(sourceId).stream()
                .map(file -> new FileResponseDTO(file.getId(), file.getName(), file.getFormatFile(),
                        file.getSize(), file.getHash(), file.getLocation(), file.getIngestion().getId(),
                        file.getReceivedDate(), file.getIngestion().getUser().getId()))
                .toList();
    }

      /** Lista os arquivos de um dataset. */
    @Transactional(readOnly = true)
    public List<FileResponseDTO> findByDatasetId(Long datasetId) {

        Dataset dataset = datasetService.findEntityById(datasetId);

        return dataset.getFiles()
                .stream()
                .map(file -> new FileResponseDTO(
                        file.getId(),
                        file.getName(),
                        file.getFormatFile(),
                        file.getSize(),
                        file.getHash(),
                        file.getLocation(),
                        file.getIngestion().getId(),
                        file.getReceivedDate(),
                        file.getIngestion().getUser().getId()))
                .toList();
    }
}
