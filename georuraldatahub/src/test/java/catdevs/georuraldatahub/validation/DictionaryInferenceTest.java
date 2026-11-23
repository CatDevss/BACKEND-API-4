package catdevs.georuraldatahub.validation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class DictionaryInferenceTest {
    private final DictionaryInference inference = new DictionaryInference();
    private ByteArrayInputStream input(String content) { return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)); }
    private List<FieldRule> infer(String content, String format) throws Exception { return inference.infer(input(content), format, 1000); }

    @Test void scansEveryCsvRecordAndPreservesCodesOptionalFieldsAndMixedNumbers() throws Exception {
        var rules = infer("\uFEFFcodigo;nome;area;ativo;data;obs\n001;A;1;true;2022-01-01;\n002;B;1,5;false;2022-02-01;texto", "csv");
        assertEquals(List.of(FieldType.STRING, FieldType.STRING, FieldType.NUMBER, FieldType.BOOLEAN, FieldType.DATE, FieldType.STRING), rules.stream().map(FieldRule::type).toList());
        assertFalse(rules.getLast().required());
        assertTrue(rules.getFirst().required());
        assertTrue(rules.stream().allMatch(r -> !r.unique() && r.domain().isEmpty()));
        var validation = new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator());
        assertTrue(validation.validate(input("codigo;nome;area;ativo;data;obs\n001;A;1;true;2022-01-01;\n002;B;1,5;false;2022-02-01;texto"), "csv", rules).stream().allMatch(RecordValidationResult::valid));
    }
    @Test void csvConflictingScalarTypesRemainText() throws Exception {
        assertEquals(FieldType.STRING, infer("id\n12\nAB", "csv").getFirst().type());
    }
    @Test void discoversFieldsAddedInLaterJsonRecordsAndKeepsJsonStringCodes() throws Exception {
        var rules = infer("[{\"codigo\":\"4106902\",\"n\":1},{\"codigo\":\"4106903\",\"n\":1.5,\"extra\":true,\"vazio\":null}]", "json");
        assertEquals(List.of("codigo", "n", "extra", "vazio"), rules.stream().map(FieldRule::name).toList());
        assertEquals(FieldType.STRING, rules.getFirst().type());
        assertEquals(FieldType.NUMBER, rules.get(1).type());
        assertFalse(rules.get(2).required());
        assertFalse(rules.getLast().required());
    }
    @Test void municipal2022FieldsComeFromTheFileRatherThanTheCurrentTemplate() throws Exception {
        String content = """
            {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"CD_MUN":"4106902","NM_MUN":"Curitiba","SIGLA_UF":"PR","AREA_KM2":434.9},"geometry":{"type":"Point","coordinates":[-49,-25]}}]}
            """;
        var rules = infer(content, "geojson");
        assertEquals(List.of("CD_MUN", "NM_MUN", "SIGLA_UF", "AREA_KM2", "geometry"), rules.stream().map(FieldRule::name).toList());
        assertEquals(FieldType.GEOMETRY, rules.getLast().type());
        assertEquals(FieldType.STRING, rules.getFirst().type());
        assertTrue(new GeoJsonRecordValidator().validate(input(content), rules).getFirst().valid());
    }
    @Test void handlesStandaloneFeatureAndRasterFootprint() throws Exception {
        assertEquals(FieldType.GEOMETRY, infer("{\"type\":\"Feature\",\"properties\":{},\"geometry\":null}", "json").getFirst().type());
        var rules = infer("{\"width\":100,\"footprint\":{\"type\":\"Polygon\",\"coordinates\":[]}}", "json");
        assertEquals(FieldType.GEOMETRY, rules.getLast().type());
    }
    @Test void emptyMalformedDuplicateHeadersAndInconsistentRowsDoNotGenerateRules() {
        for (String content : List.of("", "id\n", "id,id\n1,2", "id,n\n1", "\"id\n1", ";id\n1;2"))
            assertThrows(IllegalArgumentException.class, () -> infer(content, "csv"), content);
        for (String content : List.of("[]", "null", "{}", "{", "{\"a\":1,\"a\":2}", "[{\"a\":1},false]",
                "{\"a\":[1]}", "[{\"a\":1},{\"a\":\"1\"}]", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
            assertThrows(IllegalArgumentException.class, () -> infer(content, "json"), content);
    }
    @Test void enforcesRecordAndFieldLimits() {
        assertThrows(IllegalArgumentException.class, () -> inference.infer(input("id\n1\n2"), "csv", 1));
        String header = java.util.stream.IntStream.range(0, 101).mapToObj(i -> "c" + i).collect(java.util.stream.Collectors.joining(","));
        assertThrows(IllegalArgumentException.class, () -> infer(header, "csv"));
    }
}
