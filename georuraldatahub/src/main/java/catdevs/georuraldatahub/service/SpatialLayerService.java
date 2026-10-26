package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.enums.GeometryType;
import catdevs.georuraldatahub.processing.TreatmentService;
import catdevs.georuraldatahub.repository.LayerRepository;
import catdevs.georuraldatahub.util.GeoJsonFeatureExtractor;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpatialLayerService {
    private final LegacySpatialLayerStore legacy;
    private final LayerRepository layers;
    private final BucketService bucket;
    private final TreatmentService treatments;

    public SpatialLayerService(LegacySpatialLayerStore legacy, LayerRepository layers,
            BucketService bucket, TreatmentService treatments) {
        this.legacy = legacy;
        this.layers = layers;
        this.bucket = bucket;
        this.treatments = treatments;
    }

    public record LayerLoadResult(Long layerId, String name, GeometryType geometryType, int srid, int featureCount) { }

    // Leituras do bucket e processamento não mantêm transação/conexão de banco aberta.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LayerLoadResult createLayerFromFile(Long datasetId, Long fileId, Long userId) throws Exception {
        if (treatments.isEnabled()) {
            var result = treatments.startForDataset(datasetId, fileId, userId);
            var layer = layers.findById(result.layerId()).orElseThrow();
            return new LayerLoadResult(layer.getId(), layer.getName(), layer.getGeometryType(), layer.getSrid(), result.outputCount());
        }
        var input = legacy.load(datasetId, fileId);
        var content = input.location().toLowerCase(Locale.ROOT).endsWith(".csv")
                ? bucket.readCsvAsGeoJson(input.location()) : bucket.readGeoJson(input.location());
        return legacy.complete(input, GeoJsonFeatureExtractor.extract(content));
    }
}
