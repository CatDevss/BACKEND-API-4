package catdevs.georuraldatahub.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.entity.enums.GeometryType;
import catdevs.georuraldatahub.util.GeoJsonFeatureExtractor.Extraction;

class GeoJsonFeatureExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String POINT = "{\"type\":\"Feature\",\"id\":\"A1\","
            + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[-51.16,-23.31]},"
            + "\"properties\":{\"nome\":\"ponto\"}}";

    private static final String POLYGON_NO_ID = "{\"type\":\"Feature\","
            + "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]},"
            + "\"properties\":null}";

    private static String collection(String... features) {
        return "{\"type\":\"FeatureCollection\",\"features\":[" + String.join(",", features) + "]}";
    }

    @Test
    void usesFeatureIdOrPositionAndKeepsAttributes() throws Exception {
        Extraction result = GeoJsonFeatureExtractor.extract(
                MAPPER.readTree(collection(POINT, POLYGON_NO_ID)));

        assertEquals(2, result.features().size());
        assertEquals("A1", result.features().get(0).originId());
        assertEquals("{\"nome\":\"ponto\"}", result.features().get(0).attributesJson());
        assertEquals("2", result.features().get(1).originId());
        assertNull(result.features().get(1).attributesJson());
    }

    @Test
    void mixedGeometriesBecomeMixedLayer() throws Exception {
        Extraction result = GeoJsonFeatureExtractor.extract(
                MAPPER.readTree(collection(POINT, POLYGON_NO_ID)));

        assertEquals(GeometryType.MIXED, result.geometryType());
    }

    @Test
    void singleGeometryTypeIsKept() throws Exception {
        Extraction result = GeoJsonFeatureExtractor.extract(
                MAPPER.readTree(collection(POLYGON_NO_ID, POLYGON_NO_ID)));

        assertEquals(GeometryType.POLYGON, result.geometryType());
    }

    @Test
    void rejectsFeatureWithoutGeometry() throws Exception {
        String noGeometry = "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{}}";

        assertThrows(IllegalArgumentException.class, () -> GeoJsonFeatureExtractor.extract(
                MAPPER.readTree(collection(POINT, noGeometry))));
    }

    @Test
    void rejectsEmptyCollection() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> GeoJsonFeatureExtractor.extract(
                MAPPER.readTree(collection())));
    }
}