package catdevs.georuraldatahub.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Layer;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.GeometryType;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.FeatureRepository.NewFeature;

/**
 * Usa o banco configurado no .env, com rollback automático ao fim de cada teste.
 * Precisa de pelo menos um Dataset e uma Version com status BRUTA no banco.
 */
@SpringBootTest
@Transactional
class FeatureRepositoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private FeatureRepository featureRepository;
    @Autowired
    private LayerRepository layerRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private VersionRepository versionRepository;

    /** Círculo aproximado por n vértices (anel fechado, sentido anti-horário). */
    private static String bigPolygon(int vertices) {
        StringBuilder sb = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[[");
        double centerX = -51.16;
        double centerY = -23.31;
        double radius = 0.1;
        for (int i = 0; i <= vertices; i++) {
            double angle = 2 * Math.PI * (i % vertices) / vertices;
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "[%.6f,%.6f]",
                    centerX + radius * Math.cos(angle),
                    centerY + radius * Math.sin(angle)));
        }
        return sb.append("]]}").toString();
    }

    @Test
    void insertsBatchAndReadsBackAsGeoJson() throws Exception {
        Dataset dataset = datasetRepository.findAll().stream().findFirst().orElse(null);
        Version version = versionRepository.findAll().stream()
                .filter(v -> v.getStatus() == VersionStatus.BRUTA)
                .findFirst().orElse(null);
        assumeTrue(dataset != null && version != null,
                "Precisa de um Dataset e uma Version BRUTA no banco");

        Layer layer = new Layer();
        layer.setDataset(dataset);
        layer.setVersion(version);
        layer.setName("teste feicoes");
        layer.setGeometryType(GeometryType.MIXED);
        layer.setSrid(4326);
        layerRepository.saveAndFlush(layer);

        int vertices = 600;
        String point = "{\"type\":\"Point\",\"coordinates\":[-51.16,-23.31]}";
        String polygon = bigPolygon(vertices);
        assertTrue(polygon.length() > 4000, "O polígono precisa passar de 4000 caracteres");

        int inserted = featureRepository.insertBatch(layer.getId(), 4326, List.of(
                new NewFeature("1", point, "{\"nome\":\"ponto\"}"),
                new NewFeature("2", polygon, null)));

        assertEquals(2, inserted);
        assertEquals(2, featureRepository.countByLayerId(layer.getId()));

        List<Long> ids = featureRepository.findIdsByLayerId(layer.getId());
        JsonNode first = MAPPER.readTree(
                featureRepository.findGeometryAsGeoJson(ids.get(0)).orElseThrow());
        JsonNode second = MAPPER.readTree(
                featureRepository.findGeometryAsGeoJson(ids.get(1)).orElseThrow());

        assertEquals("Point", first.get("type").asText());
        assertEquals("Polygon", second.get("type").asText());
        assertEquals(vertices + 1, second.get("coordinates").get(0).size());
    }
}