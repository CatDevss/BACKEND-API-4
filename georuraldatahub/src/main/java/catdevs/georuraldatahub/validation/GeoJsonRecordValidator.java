package catdevs.georuraldatahub.validation;

import java.io.InputStream;
import java.util.*;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.*;

@Component
public class GeoJsonRecordValidator {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public List<RecordValidationResult> validate(InputStream input, List<FieldRule> rules) throws Exception {
        JsonNode root;
        try { root = mapper.readTree(input); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { return fileError("MALFORMED_FILE"); }
        if (root == null || !"FeatureCollection".equals(root.path("type").asText()) || !root.path("features").isArray())
            return fileError("INVALID_FILE_STRUCTURE");
        if (root.path("features").isEmpty()) return fileError("EMPTY_FILE");
        List<RecordValidationResult> results = new ArrayList<>();
        RuleValidationState state = new RuleValidationState();
        int index = 0;
        for (JsonNode feature : root.get("features")) {
            List<String> errors = new ArrayList<>();
            if (!"Feature".equals(feature.path("type").asText()) || !feature.path("properties").isObject())
                errors.add("INVALID_RECORD_STRUCTURE");
            if (!GeometryValidation.valid(feature.get("geometry"))) errors.add("INVALID_GEOMETRY");
            Map<String, JsonNode> row = new TreeMap<>();
            feature.path("properties").properties().forEach(e -> row.put(e.getKey(), e.getValue()));
            // A geometry rule refers to the standard Feature geometry rather than a duplicate property.
            for (FieldRule r : rules) if (r.type() == FieldType.GEOMETRY && r.name().equals("geometry"))
                row.put("geometry", feature.get("geometry"));
            // Reserve uniqueness only when the feature envelope and geometry are valid.
            List<String> fieldErrors = (errors.isEmpty() ? state : new RuleValidationState()).validate(row, rules, false, feature.path("geometry").toString());
            errors.addAll(fieldErrors);
            String raw = feature.toString();
            results.add(errors.isEmpty() ? RecordValidationResult.ok(index, raw) : RecordValidationResult.invalid(index, raw, errors));
            index++;
        }
        return results;
    }
    private List<RecordValidationResult> fileError(String code) {
        return List.of(RecordValidationResult.invalid(-1, "", List.of(code)));
    }
}
