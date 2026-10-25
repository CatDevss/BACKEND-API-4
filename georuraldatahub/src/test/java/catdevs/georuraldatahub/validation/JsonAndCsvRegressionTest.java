package catdevs.georuraldatahub.validation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonAndCsvRegressionTest {
    private final FileValidationService service = new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator());
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<FieldRule> rules = List.of(new FieldRule("id", true, FieldType.INTEGER, List.of(), true));
    private InputStream input(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }

    @Test void ordinaryJsonObjectIsValidatedWithoutRequiringGeoJsonEnvelope() throws Exception {
        String text = "{\"id\":1}";
        var rows = service.validate(input(text), "json", rules);
        assertTrue(rows.getFirst().valid());
        assertTrue(mapper.readTree(service.acceptedContent(input(text), "json", rows)).isObject());
        assertEquals(1, mapper.readTree(service.acceptedContent(input(text), "json", rows)).path("id").asInt());
    }
    @Test void ordinaryJsonArrayPreservesOnlyValidRowsAndRejectsUnknownFieldsDuplicatesAndWrongTypes() throws Exception {
        String text = "[{\"id\":1},{\"id\":1},{\"id\":\"2\"},{\"id\":2,\"extra\":0},null,{\"id\":3}]";
        var rows = service.validate(input(text), "json", rules);
        assertEquals(2, rows.stream().filter(RecordValidationResult::valid).count());
        var accepted = mapper.readTree(service.acceptedContent(input(text), "json", rows));
        assertTrue(accepted.isArray()); assertEquals(2, accepted.size()); assertEquals(3, accepted.get(1).path("id").asInt());
        assertEquals("PARCIAL", FileValidationReport.of(1L, 1, rows).status());
    }
    @Test void malformedJsonDuplicateKeysTrailingDocumentsAndScalarsNeverPass() throws Exception {
        for (String text : List.of("{broken", "{\"id\":1,\"id\":2}", "{\"id\":1} {}", "null", "42", "[]")) {
            var rows = service.validate(input(text), "json", rules);
            assertEquals("REJEITADO", FileValidationReport.of(1L, 1, rows).status());
            assertEquals(0, service.acceptedContent(input(text), "json", rows).length);
        }
    }
    @Test void singleFeatureAndFeatureCollectionRemainSpatialJson() throws Exception {
        String feature = "{\"type\":\"Feature\",\"properties\":{\"id\":1},\"geometry\":{\"type\":\"Point\",\"coordinates\":[-49,-25]}}";
        for (String format : List.of("json", "geojson")) {
            var rows = service.validate(input(feature), format, rules);
            assertTrue(rows.getFirst().valid());
            assertEquals("FeatureCollection", mapper.readTree(service.acceptedContent(input(feature), format, rows)).path("type").asText());
        }
    }
    @Test void ordinaryJsonWithoutSpatialCoordinatesIsAcceptedButHasExplicitMapError() throws Exception {
        var node = mapper.readTree("{\"id\":1}");
        assertTrue(service.validate(input(node.toString()), "json", rules).getFirst().valid());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GeoJsonContent.featureCollection(node)).getMessage().contains("mapa"));
    }
    @Test void genericJsonWithCoordinatesOrGeometryCanBeDisplayedWithoutDroppingProperties() throws Exception {
        var fromCoordinates = GeoJsonContent.featureCollection(mapper.readTree("[{\"id\":1,\"lat\":-25,\"lon\":-49}]"));
        assertEquals(-49, fromCoordinates.path("features").get(0).path("geometry").path("coordinates").get(0).asDouble());
        assertEquals(1, fromCoordinates.path("features").get(0).path("properties").path("id").asInt());
        var fromGeometry = GeoJsonContent.featureCollection(mapper.readTree("{\"id\":1,\"geometry\":{\"type\":\"Point\",\"coordinates\":[-49,-25]}}"));
        assertFalse(fromGeometry.path("features").get(0).path("properties").has("geometry"));
        assertEquals("Point", fromGeometry.path("features").get(0).path("geometry").path("type").asText());
    }
    @Test void delimitersAreConsistentAcrossValidationAndAcceptedContentIncludingQuotedDecimalComma() throws Exception {
        var fields = List.of(FieldRule.required("id", FieldType.INTEGER), FieldRule.required("lat", FieldType.LATITUDE));
        for (String text : List.of("id;lat\n1;\"-25,5\"", "id\tlat\n1\t-25.5", "\uFEFF\"id\";\"lat\"\n1;-25.5")) {
            var rows = service.validate(input(text), "csv", fields);
            assertTrue(rows.getFirst().valid(), rows.toString());
            var normalized = service.acceptedContent(input(text), "csv", rows);
            assertTrue(service.validate(new ByteArrayInputStream(normalized), "csv", fields).getFirst().valid());
        }
    }
    @Test void quotedMultilineHeaderIsDetectedAsOneCsvRecord() throws Exception {
        var fields = List.of(FieldRule.required("nome\ncompleto", FieldType.STRING), FieldRule.required("id", FieldType.INTEGER));
        assertTrue(service.validate(input("\"nome\ncompleto\";id\nAna;1"), "csv", fields).getFirst().valid());
    }
    @Test void quotedHeaderCommaDoesNotOverrideSemicolonDelimiter() throws Exception {
        var fields = List.of(FieldRule.required("nome,completo", FieldType.STRING), FieldRule.required("id", FieldType.INTEGER));
        assertTrue(service.validate(input("\"nome,completo\";id\nAna;1"), "csv", fields).getFirst().valid());
    }
}
