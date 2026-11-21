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

    public FeatureService(FeatureRepository features, LayerRepository layers,
            jakarta.persistence.EntityManager entityManager) {
        this.features = features;
        this.layers = layers;
        this.entityManager = entityManager;
    }

    @Transactional
    public int insertBatch(Long layerId, int srid, List<NewFeature> input) {
        if (srid != 4326)
            throw new IllegalArgumentException("A carga tratada exige EPSG:4326; reprojete antes de persistir.");
        if (input.isEmpty())
            throw new IllegalArgumentException("A camada precisa conter pelo menos uma feição.");
        var layer = layers.getReferenceById(layerId);
        if (!Integer.valueOf(srid).equals(layer.getSrid()))
            throw new IllegalArgumentException("O SRID da camada difere do SRID da carga.");
        var entities = new ArrayList<Feature>(FLUSH_SIZE);
        var reader = new GeoJsonReader();
        for (var item : input) {
            try {
                var geometry = reader.read(item.geometryGeoJson());
                if (geometry.isEmpty() || !geometry.isValid())
                    throw new IllegalArgumentException("Geometria inválida.");
                for (var coordinate : geometry.getCoordinates()) {
                    if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                            || Math.abs(coordinate.x) > 180 || Math.abs(coordinate.y) > 90)
                        throw new IllegalArgumentException("Coordenadas fora dos limites EPSG:4326: feição " + item.originId());
                }
                geometry.setSRID(srid);
                var feature = new Feature();
                feature.setLayer(layer);
                feature.setOriginId(item.originId());
                feature.setGeometry(geometry);
                feature.setAttributes(item.attributesJson());
                entities.add(feature);
                if (entities.size() == FLUSH_SIZE) {
                    persistAndDetach(entities);
                    entities = new ArrayList<>(FLUSH_SIZE);
                }
            } catch (org.locationtech.jts.io.ParseException e) {
                throw new IllegalArgumentException("GeoJSON inválido na persistência da feição.", e);
            }
        }
        if (!entities.isEmpty())
            persistAndDetach(entities);
        // A aprovação depende da validação do SDO_GEOMETRY gravado, na mesma transação.
        var invalid = features.firstSpatialError(layerId);
        if (!invalid.isEmpty())
            throw new IllegalArgumentException("Camada rejeitada pelo Oracle Spatial: " + invalid.getFirst());
        return input.size();
    }

    private void persistAndDetach(List<Feature> batch) {
        // Grava as feições antes de aprovar a versão; mantém camada e versão na sessão.
        // Com IDENTITY, o lote limita a memória, sem garantir inserções JDBC agrupadas.
        features.saveAllAndFlush(batch);
        batch.forEach(entityManager::detach);
    }

    @Transactional(readOnly = true)
    public Optional<String> findGeometryAsGeoJson(Long featureId) {
        var writer = new GeoJsonWriter();
        writer.setEncodeCRS(false);
        return features.findById(featureId).map(f -> writer.write(f.getGeometry()));
    }
}
