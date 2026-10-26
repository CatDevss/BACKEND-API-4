package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.collection.OfficialDataClient;
import catdevs.georuraldatahub.validation.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;

class VectorTreatmentTest {
    private final OfficialDataClient http = mock(OfficialDataClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final VectorTreatment treatment = new VectorTreatment(http, new ProcessingProperties());
    private final TreatmentRequest defaults = new TreatmentRequest(1L, 3L, null, false, null);
    @BeforeEach void boundary() throws Exception {
        when(http.collectionBudget()).thenReturn(() -> {});
        when(http.download(any())).thenReturn(mapper.writeValueAsBytes(mapper.readTree("""
            {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"codarea":"41"},
            "geometry":{"type":"Polygon","coordinates":[[[-53,-27],[-48,-27],[-48,-22],[-53,-22],[-53,-27]]]}}]}
            """)));
    }
    private JsonNode collection(String properties, String geometry) throws Exception {
        return mapper.readTree("{\"type\":\"FeatureCollection\",\"features\":[{\"id\":\"original-1\",\"type\":\"Feature\",\"properties\":"
                + properties + ",\"geometry\":" + geometry + "}]}");
    }
    private String polygon() { return "{\"type\":\"Polygon\",\"coordinates\":[[[-54,-26],[-50,-26],[-50,-24],[-54,-24],[-54,-26]]]}"; }
    @Test void clipsToExactBoundaryAndKeepsOriginalImmutable() throws Exception {
        var input = collection("{\"id\":\" 7 \",\"amount\":\"1,50\"}", polygon());
        String before = input.toString();
        var result = treatment.apply(input, List.of(FieldRule.required("id", FieldType.INTEGER),
                FieldRule.required("amount", FieldType.NUMBER)), defaults);
        var feature = result.content().path("features").get(0);
        assertEquals("original-1", feature.path("id").asText());
        assertTrue(feature.path("properties").path("id").isIntegralNumber());
        assertEquals(1.5, feature.path("properties").path("amount").asDouble());
        var geometry = new org.locationtech.jts.io.geojson.GeoJsonReader().read(feature.path("geometry").toString());
        assertEquals(-53, geometry.getEnvelopeInternal().getMinX());
        assertEquals(6, geometry.getArea(), 0.000001);
        assertEquals(before, input.toString()); assertEquals(4326, result.provenance().path("targetSrid").asInt());
        assertEquals(64, result.provenance().path("boundarySha256").asText().length());
    }
    @Test void repairsSelfIntersectingOfficialMaskAndPreservesItsOriginalHash() throws Exception {
        byte[] mask = """
            {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"codarea":"41"},
            "geometry":{"type":"Polygon","coordinates":[[[-53,-27],[-48,-22],[-53,-22],[-48,-27],[-53,-27]]]}}]}
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(http.download(any())).thenReturn(mask);
        var result = treatment.apply(collection("{}", polygon()), List.of(), defaults);
        assertTrue(result.outputCount() > 0);
        assertEquals(VectorTreatment.hash(mask), result.provenance().path("boundarySha256").asText());
        assertTrue(result.provenance().path("boundaryGeometryPolicy").asText().contains("original preservado"));
    }
    @Test void appliesExplicitDateUnitAndRenameRules() throws Exception {
        var input = collection("{\"data\":\"25/10/2026\",\"area_m2\":\"20000\"}", polygon());
        var request = new TreatmentRequest(1L, 3L, List.of(
                new TreatmentRequest.AttributeChange("data", "data_iso", FieldType.DATE, "dd/MM/yyyy", null, null, null),
                new TreatmentRequest.AttributeChange("area_m2", "area_ha", FieldType.NUMBER, null, new BigDecimal("0.0001"), null, "ha")), false, null);
        var properties = treatment.apply(input, List.of(), request).content().path("features").get(0).path("properties");
        assertEquals("2026-10-25", properties.path("data_iso").asText());
        assertEquals(2, properties.path("area_ha").asDouble()); assertFalse(properties.has("area_m2"));
    }
    @Test void dissolvesByRuleAndPreservesAllSourcesAndAttributes() throws Exception {
        var input = collection("{\"grupo\":\"A\",\"nome\":\"primeira\"}", polygon());
        var copy = input.path("features").get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy).put("id", "original-2");
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy.path("properties")).put("nome", "segunda");
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.path("features")).add(copy);
        var result = treatment.apply(input, List.of(), new TreatmentRequest(1L, 3L, null, true, List.of("grupo")));
        assertEquals(2, result.inputCount()); assertEquals(1, result.outputCount());
        var properties = result.content().path("features").get(0).path("properties");
        assertEquals(2, properties.path("_origens").size()); assertEquals(2, properties.path("_atributos_originais").size());
        assertEquals("segunda", properties.path("_atributos_originais").get(1).path("nome").asText());
    }
    @Test void rejectsInvalidDatesCollisionsAndUnspecifiedConversionFactors() throws Exception {
        var input = collection("{\"data\":\"31/02/2026\",\"outro\":\"x\"}", polygon());
        assertThrows(IllegalArgumentException.class, () -> treatment.apply(input, List.of(), new TreatmentRequest(1L, 3L,
                List.of(new TreatmentRequest.AttributeChange("data", "data", FieldType.DATE, "dd/MM/yyyy", null, null, null)), false, null)));
        assertThrows(IllegalArgumentException.class, () -> treatment.apply(input, List.of(), new TreatmentRequest(1L, 3L,
                List.of(new TreatmentRequest.AttributeChange("data", "outro", FieldType.STRING, null, null, null, null)), false, null)));
        assertThrows(IllegalArgumentException.class, () -> treatment.apply(input, List.of(), new TreatmentRequest(1L, 3L,
                List.of(new TreatmentRequest.AttributeChange("data", "data", FieldType.NUMBER, null, null, null, "ha")), false, null)));
    }
    @Test void rejectsOutsideDataAndDoesNotAssumeProjectedCoordinatesAreLongitude() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> treatment.apply(collection("{}",
                "{\"type\":\"Point\",\"coordinates\":[-60,-10]}"), List.of(), defaults));
        assertThrows(IllegalArgumentException.class, () -> treatment.apply(collection("{}",
                "{\"type\":\"Point\",\"coordinates\":[700000,7200000]}"), List.of(), defaults));
    }

    @Test void acceptsBlankOptionalFieldsAlreadyApprovedByValidation() throws Exception {
        var input = collection("""
                {"amount":"","day":"  ","flag":""}
                """, polygon());
        var rules = List.of(FieldRule.optional("amount", FieldType.NUMBER),
                FieldRule.optional("day", FieldType.DATE),
                FieldRule.optional("flag", FieldType.BOOLEAN));
        var properties = treatment.apply(input, rules, defaults).content().path("features").get(0).path("properties");
        assertTrue(properties.path("amount").isNull());
        assertTrue(properties.path("day").isNull());
        assertTrue(properties.path("flag").isNull());
    }

    @Test void clippingMixedGeometryKeepsPointLineAndPolygon() throws Exception {
        var input = collection("{}", "{\"type\":\"GeometryCollection\",\"geometries\":["
                + polygon() + ", {\"type\":\"Point\",\"coordinates\":[-49,-23]},"
                + "{\"type\":\"LineString\",\"coordinates\":[[-54,-26],[-49,-26]]}]}");
        var result = treatment.apply(input, List.of(), defaults);
        var geometry = new org.locationtech.jts.io.geojson.GeoJsonReader().read(
                result.content().path("features").get(0).path("geometry").toString());
        assertEquals(3, geometry.getNumGeometries());
        Set<Integer> dimensions = new HashSet<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) dimensions.add(geometry.getGeometryN(i).getDimension());
        assertEquals(Set.of(0, 1, 2), dimensions);
        assertTrue(geometry.getEnvelopeInternal().getMinX() >= -53);
    }

    @Test void rejectsGroupingOnReservedTraceFieldsInsteadOfOverwritingTheirValue() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> treatment.apply(collection("{\"_origens\":\"A\"}", polygon()),
                List.of(), new TreatmentRequest(1L, 3L, null, true, List.of("_origens"))));
    }
}
