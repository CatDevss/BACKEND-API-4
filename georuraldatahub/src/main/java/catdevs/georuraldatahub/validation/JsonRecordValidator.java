package catdevs.georuraldatahub.validation;

import java.io.InputStream;
import java.util.*;
import com.fasterxml.jackson.databind.*;

/** JSON records and GeoJSON share field rules, but only GeoJSON requires Feature geometry. */
public class JsonRecordValidator {
    private final GeoJsonRecordValidator geoJson;
    static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public JsonRecordValidator(GeoJsonRecordValidator geoJson) { this.geoJson = geoJson; }

    public List<RecordValidationResult> validate(InputStream input, List<FieldRule> rules) throws Exception {
        JsonNode root;
        try { root = MAPPER.readTree(input); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { return error("MALFORMED_FILE"); }
        if (isGeoJson(root)) return geoJson.validate(root, rules);
        if (root == null || !root.isObject() && !root.isArray()) return error("INVALID_FILE_STRUCTURE: JSON deve conter um objeto ou uma lista de objetos.");
        if (root.isArray() && root.isEmpty()) return error("EMPTY_FILE");
        List<RecordValidationResult> results = new ArrayList<>();
        var state = new RuleValidationState();
        Iterable<JsonNode> rows = root.isArray() ? root : List.of(root);
        int index = 0;
        for (JsonNode row : rows) {
            List<String> errors;
            if (!row.isObject()) errors = List.of("INVALID_RECORD_STRUCTURE: Cada registro JSON deve ser um objeto.");
            else {
                Map<String, JsonNode> values = new TreeMap<>();
                row.properties().forEach(field -> values.put(field.getKey(), field.getValue()));
                errors = state.validate(values, rules, false);
            }
            results.add(errors.isEmpty() ? RecordValidationResult.ok(index, row.toString())
                    : RecordValidationResult.invalid(index, row.toString(), errors));
            index++;
        }
        return results;
    }

    static boolean isGeoJson(JsonNode root) {
        return root != null && root.isObject() && Set.of("FeatureCollection", "Feature").contains(root.path("type").asText());
    }
    private List<RecordValidationResult> error(String code) {
        return List.of(RecordValidationResult.invalid(-1, "", List.of(code)));
    }
}
