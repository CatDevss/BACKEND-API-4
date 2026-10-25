package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.Feature;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.repository.FeatureRepository.NewFeature;
import java.util.*;
import org.locationtech.jts.io.geojson.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureService {
    private final FeatureRepository features;
    private final LayerRepository layers;
    private final jakarta.persistence.EntityManager entityManager;
    private static final int FLUSH_SIZE = 500;
    public FeatureService(FeatureRepository features, LayerRepository layers, jakarta.persistence.EntityManager entityManager) {
        this.features = features; this.layers = layers; this.entityManager = entityManager;
    }
    @Transactional
    public int insertBatch(Long layerId, int srid, List<NewFeature> input) {
        if (input.isEmpty()) return 0;
        var layer = layers.getReferenceById(layerId);
        var entities = new ArrayList<Feature>(FLUSH_SIZE);
        var reader = new GeoJsonReader();
        for (var item : input) {
            try {
                var geometry = reader.read(item.geometryGeoJson());
                if (geometry.isEmpty() || !geometry.isValid()) throw new IllegalArgumentException("Geometria inválida.");
                geometry.setSRID(srid);
                var feature = new Feature();
                feature.setLayer(layer); feature.setOriginId(item.originId());
                feature.setGeometry(geometry); feature.setAttributes(item.attributesJson());
                entities.add(feature);
                if (entities.size() == FLUSH_SIZE) {
                    persistAndDetach(entities);
                    entities = new ArrayList<>(FLUSH_SIZE);
                }
            } catch (org.locationtech.jts.io.ParseException e) {
                throw new IllegalArgumentException("GeoJSON inválido na persistência da feição.", e);
            }
        }
        if (!entities.isEmpty()) persistAndDetach(entities);
        return input.size();
    }
    private void persistAndDetach(List<Feature> batch) {
        // Flush antes de aprovar a versão. Detach só das feições: camada/versão continuam gerenciadas.
        // IDENTITY não permite prometer batching JDBC; o lote aqui limita memória e dirty checking.
        features.saveAllAndFlush(batch);
        batch.forEach(entityManager::detach);
    }
    @Transactional(readOnly = true)
    public Optional<String> findGeometryAsGeoJson(Long featureId) {
        var writer = new GeoJsonWriter(); writer.setEncodeCRS(false);
        return features.findById(featureId).map(f -> writer.write(f.getGeometry()));
    }
}
