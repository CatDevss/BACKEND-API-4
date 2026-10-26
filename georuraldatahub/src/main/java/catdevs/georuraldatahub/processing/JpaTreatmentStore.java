package catdevs.georuraldatahub.processing;

import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.service.*;
import catdevs.georuraldatahub.util.GeoJsonFeatureExtractor;
import catdevs.georuralprocessing.entity.*;
import catdevs.georuralprocessing.repository.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "processing.persistence.enabled", havingValue = "true")
public class JpaTreatmentStore {
    private final TreatmentExecutionRepository executions;
    private final RasterMetadataRepository rasters;
    private final FileRepository files;
    private final VersionRepository versions;
    private final DatasetRepository datasets;
    private final LayerRepository layers;
    private final FeatureService features;
    private final BucketService bucket;
    private final jakarta.persistence.EntityManager entityManager;
    private final ObjectMapper mapper = new ObjectMapper();
    public JpaTreatmentStore(TreatmentExecutionRepository executions, RasterMetadataRepository rasters,
            FileRepository files, VersionRepository versions, DatasetRepository datasets,
            LayerRepository layers, FeatureService features, BucketService bucket, jakarta.persistence.EntityManager entityManager) {
        this.executions = executions; this.rasters = rasters; this.files = files; this.versions = versions;
        this.datasets = datasets; this.layers = layers; this.features = features; this.bucket = bucket;
        this.entityManager = entityManager;
    }
    public record View(Long id, Long fileId, Long inputVersionId, Long userId, String status,
            LocalDateTime startedAt, LocalDateTime finishedAt, Long outputVersionId, Long layerId, Long rasterId,
            Integer inputCount, Integer outputCount, String error, JsonNode parameters, JsonNode provenance) { }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(TreatmentInputService.Input input, TreatmentRequest request) {
        var execution = new TreatmentExecution();
        execution.setFileId(input.fileId()); execution.setInputVersionId(input.inputVersionId());
        execution.setUserId(request.userId()); execution.setStatus("EM_EXECUCAO"); execution.setStartedAt(LocalDateTime.now());
        var parameters = mapper.createObjectNode();
        parameters.set("request", mapper.valueToTree(request));
        parameters.put("dictionaryId", input.dictionaryId()).put("dictionaryVersion", input.dictionaryVersion())
                .put("originalSha256", input.originalHash()).put("inputLocation", input.workingLocation())
                .put("format", input.format()).put("sourceId", input.sourceId()).put("targetSrid", 4326).put("engineVersion", "BE-07-v1");
        parameters.set("dictionaryRules", mapper.valueToTree(input.rules()));
        execution.setParameters(parameters.toString());
        return executions.saveAndFlush(execution).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public View complete(Long id, TreatmentInputService.Input input, TreatmentRequest request,
            JsonNode content, JsonNode provenance, int inputCount, int outputCount, boolean raster) throws Exception {
        var execution = execution(id);
        if (!"EM_EXECUCAO".equals(execution.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Execução já finalizada.");
        var file = files.findLockedById(input.fileId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        entityManager.refresh(file);
        var source = versions.findById(input.inputVersionId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Versão de entrada removida."));
        entityManager.refresh(source);
        if (!Objects.equals(source.getFile().getId(), file.getId()) || !Objects.equals(source.getLocation(), input.workingLocation())
                || !Objects.equals(file.getHash(), input.originalHash()) || !source.getLocation().startsWith("tratada/")
                || !Set.of(VersionStatus.TRATADA, VersionStatus.PUBLICADA).contains(source.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A versão de entrada não corresponde à cópia tratada aprovada.");
        Dataset dataset = null;
        if (!raster) {
            if (request.datasetId() == null) throw new IllegalArgumentException("Selecione o conjunto para criar a camada vetorial.");
            dataset = datasets.findLockedById(request.datasetId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto não encontrado."));
            entityManager.refresh(dataset);
            if (dataset.getFiles().stream().noneMatch(f -> f.getId().equals(file.getId())))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Associe o arquivo ao conjunto antes de criar a camada.");
        }
        int nextNumber = versions.findFirstByFile_IdOrderByVersionNumberDesc(file.getId())
                .map(v -> v.getVersionNumber() + 1).orElse(1);
        var version = new Version(); version.setFile(file); version.setVersionNumber(nextNumber); version.setStatus(VersionStatus.BRUTA);
        String outputKey = "tratada/fontes/" + input.sourceId() + "/" + file.getId() + "/execucao-" + id + (raster ? ".raster.json" : ".geojson");
        List<String> uploads = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) for (String key : uploads) {
                    try { bucket.deleteObject(key); }
                    catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(JpaTreatmentStore.class).error("Falha ao limpar {}", key, e); }
                }
            }
        });
        byte[] outputBytes = mapper.writeValueAsBytes(content);
        var trace = provenance.deepCopy();
        if (trace.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) trace)
                .put("outputArtifactSha256", VectorTreatment.hash(outputBytes)).put("outputLocation", outputKey);
        uploads.add(outputKey);
        bucket.uploadCollectionArtifact(outputKey, outputBytes, "application/json");
        String dataLocation = outputKey;
        if (raster) {
            dataLocation = outputKey.substring(0, outputKey.length() - ".raster.json".length()) + "." + input.format();
            String dataSource = input.workingLocation().endsWith(".raster.json") ? input.rawLocation() : input.workingLocation();
            uploads.add(dataLocation);
            bucket.copyWorkingObject(dataSource, dataLocation);
            if (trace.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) trace)
                    .put("manifestLocation", outputKey).put("manifestSha256", VectorTreatment.hash(outputBytes))
                    .put("outputLocation", dataLocation).put("outputArtifactSha256", input.originalHash()).put("dataInputLocation", dataSource);
        }
        version.setLocation(dataLocation);
        version = versions.saveAndFlush(version);
        if (raster) {
            JsonNode summary = content.path("summary");
            var record = new RasterMetadata(); record.setVersionId(version.getId()); record.setLocation(dataLocation);
            record.setUserId(request.userId()); record.setSourceSrid(content.path("sourceSrid").asInt()); record.setWidth(summary.path("width").asInt());
            record.setHeight(summary.path("height").asInt()); record.setBandCount(summary.path("bandCount").asInt());
            record.setPixelWidth(summary.path("pixelWidth").asDouble()); record.setPixelHeight(summary.path("pixelHeight").asDouble());
            record.setMetadata(content.toString());
            var geometry = new GeoJsonReader().read(summary.path("footprint").toString()); geometry.setSRID(4326);
            if (geometry.isEmpty() || !geometry.isValid() || geometry.getDimension() != 2) throw new IllegalArgumentException("Área coberta do raster inválida.");
            record.setFootprint(geometry); execution.setRasterId(rasters.saveAndFlush(record).getId());
        } else {
            var extraction = GeoJsonFeatureExtractor.extract(content);
            var layer = new Layer(); layer.setDataset(dataset); layer.setVersion(version);
            String suffix = " [tratamento " + id + "]";
            layer.setName(shortText(input.name(), 100 - suffix.getBytes(StandardCharsets.UTF_8).length) + suffix);
            layer.setSrid(4326); layer.setGeometryType(extraction.geometryType());
            layer = layers.saveAndFlush(layer);
            features.insertBatch(layer.getId(), 4326, extraction.features()); execution.setLayerId(layer.getId());
        }
        String traceKey = outputKey + ".processing.json";
        uploads.add(traceKey); bucket.uploadCollectionArtifact(traceKey, mapper.writeValueAsBytes(trace), "application/json");
        // Criamos outra versão; a cópia de entrada e os tratamentos anteriores continuam preservados.
        version.setStatus(VersionStatus.TRATADA); versions.saveAndFlush(version);
        execution.setOutputVersionId(version.getId()); execution.setStatus("CONCLUIDO"); execution.setFinishedAt(LocalDateTime.now());
        execution.setInputCount(inputCount); execution.setOutputCount(outputCount); execution.setProvenance(trace.toString());
        return view(executions.saveAndFlush(execution));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id, String message) {
        var execution = execution(id);
        if (!"EM_EXECUCAO".equals(execution.getStatus())) return;
        execution.setStatus("FALHOU"); execution.setFinishedAt(LocalDateTime.now());
        execution.setError(shortText(message == null ? "Falha no tratamento." : message, 500)); executions.save(execution);
    }
    @Transactional(readOnly = true)
    public View get(Long id) { return view(execution(id)); }
    @Transactional(readOnly = true)
    public Map<String, Object> list(Long fileId, int page, int size) {
        if (fileId <= 0 || page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginação ou arquivo inválido.");
        var result = executions.findByFileIdOrderByIdDesc(fileId, PageRequest.of(page, size));
        return Map.of("content", result.map(this::view).getContent(), "page", page, "size", size,
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public Map<String, Object> raster(Long id) {
        var raster = rasters.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Raster não encontrado."));
        return Map.of("id", raster.getId(), "versionId", raster.getVersionId(), "location", raster.getLocation(),
                "sourceSrid", raster.getSourceSrid(), "coverageSrid", 4326, "metadata", json(raster.getMetadata()));
    }
    @Transactional(readOnly = true)
    public Map<String, Object> searchRasters(Long featureId, List<Double> bbox, int page, int size) throws Exception {
        if (page < 0 || size < 1 || size > 100 || featureId != null && featureId <= 0)
            throw new IllegalArgumentException("Paginação ou feição inválida.");
        if (featureId != null && bbox != null) throw new IllegalArgumentException("Escolha feição ou bbox como filtro espacial.");
        org.locationtech.jts.geom.Geometry area = null;
        if (featureId != null) {
            String geometry = features.findGeometryAsGeoJson(featureId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feição não encontrada."));
            area = new GeoJsonReader().read(geometry);
        } else if (bbox != null) {
            if (bbox.size() != 4 || bbox.stream().anyMatch(n -> n == null || !Double.isFinite(n))
                    || bbox.get(0) < -180 || bbox.get(2) > 180 || bbox.get(1) < -90 || bbox.get(3) > 90
                    || bbox.get(0) >= bbox.get(2) || bbox.get(1) >= bbox.get(3))
                throw new IllegalArgumentException("bbox deve ser oeste,sul,leste,norte em EPSG:4326.");
            area = new org.locationtech.jts.geom.GeometryFactory().toGeometry(
                    new org.locationtech.jts.geom.Envelope(bbox.get(0), bbox.get(2), bbox.get(1), bbox.get(3)));
        }
        if (area != null) area.setSRID(4326);
        var pagination = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("id").descending());
        var result = area == null ? rasters.findAll(pagination) : rasters.findIntersecting(area, pagination);
        var content = result.getContent().stream().map(r -> Map.<String, Object>of("id", r.getId(), "versionId", r.getVersionId(),
                "location", r.getLocation(), "sourceSrid", r.getSourceSrid(), "coverageSrid", 4326,
                "width", r.getWidth(), "height", r.getHeight(), "bandCount", r.getBandCount())).toList();
        return Map.of("content", content, "page", page, "size", size, "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }
    private TreatmentExecution execution(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Informe um ID positivo.");
        return executions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execução não encontrada."));
    }
    private View view(TreatmentExecution e) {
        return new View(e.getId(), e.getFileId(), e.getInputVersionId(), e.getUserId(), e.getStatus(), e.getStartedAt(),
                e.getFinishedAt(), e.getOutputVersionId(), e.getLayerId(), e.getRasterId(), e.getInputCount(), e.getOutputCount(),
                e.getError(), json(e.getParameters()), json(e.getProvenance()));
    }
    private JsonNode json(String text) {
        if (text == null) return mapper.nullNode();
        try { return mapper.readTree(text); } catch (java.io.IOException e) { throw new IllegalStateException("JSON persistido inválido.", e); }
    }
    private String shortText(String text, int bytes) {
        String result = text;
        while (result.getBytes(StandardCharsets.UTF_8).length > bytes)
            result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
        return result;
    }
}
