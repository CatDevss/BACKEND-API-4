package catdevs.georuraldatahub.processing;

import catdevs.georuraldatahub.service.BucketService;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TreatmentService {
    private final Optional<JpaTreatmentStore> store;
    private final TreatmentInputService inputs;
    private final VectorTreatment vectors;
    private final BucketService bucket;
    private final ObjectMapper mapper = new ObjectMapper();
    public TreatmentService(Optional<JpaTreatmentStore> store, TreatmentInputService inputs, VectorTreatment vectors, BucketService bucket) {
        this.store = store; this.inputs = inputs; this.vectors = vectors; this.bucket = bucket;
    }
    public boolean isEnabled() { return store.isPresent(); }
    public JpaTreatmentStore.View startForDataset(Long datasetId, Long fileId, Long userId) throws Exception {
        Long actor = userId == null ? inputs.uploaderId(fileId) : userId;
        var input = inputs.load(fileId, actor);
        if (Set.of("tif", "tiff").contains(input.format())) throw new IllegalArgumentException("Raster usa o endpoint de tratamentos e o catálogo; não cria feições vetoriais.");
        return start(fileId, new TreatmentRequest(datasetId, actor, null, false, null));
    }
    public JpaTreatmentStore enabledStore() {
        return store.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Tratamento persistente desativado. Aplique a migração BE-07 e configure processing.persistence.enabled=true."));
    }
    public JpaTreatmentStore.View start(Long fileId, TreatmentRequest request) throws Exception {
        var persistence = enabledStore();
        var input = inputs.load(fileId, request.userId());
        Long id = persistence.start(input, request);
        try {
            JsonNode approval = bucket.readVersionValidationReport(input.workingLocation(), input.rawLocation());
            if (approval == null || !"VALIDO".equals(approval.path("status").asText()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O arquivo inteiro precisa passar na validação; rejeição parcial também bloqueia o tratamento.");
            if (Set.of("tif", "tiff").contains(input.format())) {
                if (request.dissolve() || !request.dissolveBy().isEmpty() || !request.attributes().isEmpty())
                    throw new IllegalArgumentException("Raster usa catálogo de metadados; regras vetoriais não se aplicam às células.");
                String manifestLocation = input.workingLocation().endsWith(".raster.json")
                        ? input.workingLocation() : input.workingLocation() + ".raster.json";
                JsonNode metadata = bucket.readRasterManifest(manifestLocation);
                if (!"RASTER".equals(metadata.path("kind").asText()))
                    throw new IllegalArgumentException("Raster sem metadados aprovados pelo fluxo específico.");
                var envelope = mapper.createObjectNode().put("type", "FeatureCollection");
                var feature = envelope.putArray("features").addObject().put("type", "Feature").put("id", "coverage");
                feature.set("geometry", metadata.path("summary").path("footprint"));
                feature.putObject("properties");
                var coverage = vectors.apply(envelope, List.of(), request);
                var trace = mapper.createObjectNode().put("mode", "RASTER_CATALOG").put("sourceSrid", metadata.path("sourceSrid").asInt())
                        .put("coverageSrid", 4326).put("pixelProcessing", false).put("toolVersion", metadata.path("toolVersion").asText())
                        .put("rawLocation", input.rawLocation());
                trace.set("paranaIntersection", coverage.content().path("features").get(0).path("geometry"));
                trace.set("coverageVerification", coverage.provenance());
                return persistence.complete(id, input, request, metadata, trace, 1, 1, true);
            }
            if (!Set.of("csv", "json", "geojson", "zip", "gpkg").contains(input.format()))
                throw new IllegalArgumentException("Formato não suportado para tratamento vetorial.");
            JsonNode content = input.workingLocation().toLowerCase(Locale.ROOT).endsWith(".csv")
                    ? bucket.readCsvAsGeoJson(input.workingLocation()) : bucket.readGeoJson(input.workingLocation());
            var result = vectors.apply(content, input.rules(), request);
            try {
                String metadata = bucket.readGeospatialMetadata(input.rawLocation());
                if (metadata != null && result.provenance().isObject())
                    ((com.fasterxml.jackson.databind.node.ObjectNode) result.provenance()).set("inputConversion", mapper.readTree(metadata));
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() != 404 || Set.of("zip", "gpkg").contains(input.format())) throw e;
            }
            return persistence.complete(id, input, request, result.content(), result.provenance(), result.inputCount(), result.outputCount(), false);
        } catch (Exception error) {
            try { persistence.fail(id, error.getMessage()); } catch (RuntimeException failure) { error.addSuppressed(failure); }
            throw error;
        }
    }
}
