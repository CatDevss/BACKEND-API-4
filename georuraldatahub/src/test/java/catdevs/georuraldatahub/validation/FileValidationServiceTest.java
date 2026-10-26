package catdevs.georuraldatahub.validation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileValidationServiceTest {
    private final FileValidationService service = new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator());
    private final List<FieldRule> rules = List.of(
            new FieldRule("id", true, FieldType.INTEGER, List.of(), true),
            new FieldRule("tipo", true, FieldType.STRING, List.of("rural", "urbano"), false),
            FieldRule.required("latitude", FieldType.LATITUDE), FieldRule.required("longitude", FieldType.LONGITUDE));

    private ByteArrayInputStream input(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
    private List<RecordValidationResult> csv(String text) throws Exception { return service.validate(input(text), "CSV", rules); }
    private void has(List<RecordValidationResult> results, String code) {
        assertTrue(results.stream().flatMap(r -> r.errors().stream()).anyMatch(e -> e.startsWith(code)), results.toString());
    }
    private String collection(String features) { return "{\"type\":\"FeatureCollection\",\"features\":[" + features + "]}"; }
    private String feature(String properties, String geometry) {
        return "{\"type\":\"Feature\",\"properties\":" + properties + ",\"geometry\":" + geometry + "}";
    }
    private final String point = "{\"type\":\"Point\",\"coordinates\":[-47,-23]}";

    @Test void handlesBomBeforeQuotedHeader() throws Exception {
        String text = "\uFEFF\"id\",tipo,latitude,longitude\n1,rural,0,0";
        var results = csv(text);
        assertTrue(results.getFirst().valid());
        String out = new String(service.acceptedContent(input(text), "csv", results), StandardCharsets.UTF_8);
        assertTrue(csv(out).getFirst().valid());
    }
    @Test void rejectsUnknownHeadersAtFileScope() throws Exception {
        var results = csv("id,tipo,latitude,longitude,extra\n1,rural,-23,-47\n");
        has(results, "UNKNOWN_FIELD"); assertEquals(-1, results.getFirst().recordIndex());
        assertEquals("REJEITADO", FileValidationReport.of(1L, 1, results).status());
    }
    @Test void rejectsMissingRequiredHeaders() throws Exception { has(csv("tipo,latitude,longitude\nrural,0,0"), "REQUIRED_FIELD"); }
    @Test void validatesRequiredTypesDomainsCoordinatesAndDuplicates() throws Exception {
        var results = csv("id,tipo,latitude,longitude\n1,rural,-23,-47\n1,urbano,0,0\nx,outro,91,NaN\n,rural,0,0\n2,rural,0,0,extra");
        assertTrue(results.getFirst().valid());
        for (String code : List.of("DUPLICATE_VALUE", "INVALID_TYPE", "UNKNOWN_VALUE", "INVALID_COORDINATES", "REQUIRED_FIELD", "INVALID_COLUMN_COUNT")) has(results, code);
        var report = FileValidationReport.of(1L, 2, results);
        assertEquals("PARCIAL", report.status()); assertEquals(1, report.validRecords()); assertEquals(4, report.rejectedRecords());
    }
    @Test void detectsEquivalentNumericDuplicates() throws Exception {
        var results = service.validate(input("n\n1\n1.0"), "csv", List.of(FieldRule.required("n", FieldType.NUMBER)));
        has(results, "DUPLICATE_RECORD");
    }
    @Test void rejectedRowsDoNotReserveUniqueValues() throws Exception {
        var results = csv("id,tipo,latitude,longitude\n1,desconhecido,0,0\n1,rural,0,0");
        assertFalse(results.get(0).valid()); assertTrue(results.get(1).valid());
    }
    @Test void handlesQuotedMultilineAndEscapedQuotes() throws Exception {
        String text = "name\r\n\"a,\"\"b\"\"\ntexto\"\r\n";
        var fields = List.of(FieldRule.required("name", FieldType.STRING));
        var results = service.validate(input(text), "csv", fields);
        assertTrue(results.getFirst().valid());
        var accepted = service.acceptedContent(input(text), "csv", results);
        assertTrue(service.validate(new ByteArrayInputStream(accepted), "csv", fields).getFirst().valid());
    }
    @Test void rejectsMalformedCsvEvenAfterValidRows() throws Exception {
        var results = csv("id,tipo,latitude,longitude\n1,rural,0,0\n\"unterminated");
        has(results, "MALFORMED_FILE");
        assertEquals(0, service.acceptedContent(input(""), "csv", results).length);
    }
    @Test void acceptedCsvContainsOnlyApprovedRecordsAndHeader() throws Exception {
        String text = "id,tipo,latitude,longitude\n1,rural,0,0\n2,invalido,0,0";
        var results = csv(text);
        String out = new String(service.acceptedContent(input(text), "csv", results), StandardCharsets.UTF_8);
        assertTrue(out.contains("\"id\"")); assertTrue(out.contains("\"1\"")); assertFalse(out.contains("invalido"));
        assertEquals(1, csv(out).size());
    }
    @Test void handlesEmptyUnsupportedAndDuplicateHeaders() throws Exception {
        has(csv(""), "EMPTY_FILE"); has(csv("id,id\n1,2"), "INVALID_HEADER");
        has(service.validate(input("abc"), "zip", rules), "UNSUPPORTED_FORMAT");
    }
    @Test void validatesBooleanDateIntegerAndFiniteNumber() throws Exception {
        var fields = List.of(FieldRule.required("b", FieldType.BOOLEAN), FieldRule.required("d", FieldType.DATE),
                FieldRule.required("i", FieldType.INTEGER), FieldRule.required("n", FieldType.NUMBER));
        var results = service.validate(input("b,d,i,n\ntrue,2026-10-20,1,1.5\nmaybe,2026-02-30,1.5,Infinity"), "csv", fields);
        assertTrue(results.getFirst().valid()); assertEquals(4, results.get(1).errors().size());
    }
    @Test void rejectsMalformedAndWrongGeoJsonEnvelope() throws Exception {
        for (String text : List.of("", "null", "{}", "{broken", "{\"type\":\"Point\"}", collection(""))) {
            var results = service.validate(input(text), "geojson", List.of());
            assertEquals("REJEITADO", FileValidationReport.of(1L, 1, results).status(), text);
        }
    }
    @Test void rejectsUnknownGeoJsonPropertiesAndValues() throws Exception {
        String text = collection(feature("{\"tipo\":\"outro\",\"extra\":1}", point));
        var results = service.validate(input(text), "geojson", List.of(rules.get(1)));
        has(results, "UNKNOWN_FIELD"); has(results, "UNKNOWN_VALUE");
    }
    @Test void jsonTypesAreStrict() throws Exception {
        var results = service.validate(input(collection(feature("{\"n\":\"1\"}", point))), "geojson", List.of(FieldRule.required("n", FieldType.NUMBER)));
        has(results, "INVALID_TYPE");
    }
    @Test void rejectsInvalidGeometryCoordinatesAndSelfIntersection() throws Exception {
        for (String geometry : List.of("null", "{\"type\":\"Point\",\"coordinates\":[181,0]}",
                "{\"type\":\"LineString\",\"coordinates\":[[0,0]]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1]]]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[2,2],[0,2],[2,0],[0,0]]]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[0,1],[1,1],[1,0],[0,0]],[[2,2],[3,2],[3,3],[2,2]]]}") ) {
            has(service.validate(input(collection(feature("{}", geometry))), "json", List.of()), "INVALID_GEOMETRY");
        }
    }
    @Test void validGeometryTypesAndGeometryRule() throws Exception {
        for (String geometry : List.of(point,
                "{\"type\":\"MultiPoint\",\"coordinates\":[[0,0],[1,1]]}",
                "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
                "{\"type\":\"MultiLineString\",\"coordinates\":[[[0,0],[1,1]]]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}",
                "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[1,0],[1,1],[0,0]]]]}",
                "{\"type\":\"GeometryCollection\",\"geometries\":[" + point + "]}")) {
            var results = service.validate(input(collection(feature("{}", geometry))), "json", List.of(FieldRule.required("geometry", FieldType.GEOMETRY)));
            assertTrue(results.getFirst().valid(), results.toString());
        }
    }
    @Test void filtersGeoJsonAndDetectsDuplicateFeatures() throws Exception {
        String valid = feature("{\"name\":\"a\"}", point);
        String text = collection(valid + "," + valid + "," + feature("{\"name\":\"b\"}", "null"));
        var results = service.validate(input(text), "geojson", List.of(FieldRule.required("name", FieldType.STRING)));
        has(results, "DUPLICATE_RECORD"); has(results, "INVALID_GEOMETRY");
        byte[] accepted = service.acceptedContent(input(text), "geojson", results);
        var filtered = service.validate(new ByteArrayInputStream(accepted), "geojson", List.of(FieldRule.required("name", FieldType.STRING)));
        assertEquals(1, filtered.size()); assertTrue(filtered.getFirst().valid());
    }
}
