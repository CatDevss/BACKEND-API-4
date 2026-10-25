package catdevs.georuraldatahub.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class RuleValidationState {
    private final Set<String> records = new HashSet<>();
    private final Map<String, Set<String>> uniqueValues = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    List<String> validate(Map<String, JsonNode> row, List<FieldRule> rules, boolean csv) {
        return validate(row, rules, csv, "");
    }
    List<String> validate(Map<String, JsonNode> row, List<FieldRule> rules, boolean csv, String geometry) {
        List<String> errors = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (FieldRule rule : rules) known.add(rule.name());
        for (String name : row.keySet()) if (!known.contains(name)) errors.add("UNKNOWN_FIELD: " + name);
        Map<String, String> normalized = new TreeMap<>();
        for (FieldRule rule : rules) {
            JsonNode value = row.get(rule.name());
            if (value == null || value.isNull() || value.isTextual() && value.asText().isBlank()) {
                if (rule.required()) errors.add("REQUIRED_FIELD: " + rule.name());
                continue;
            }
            String text = value.isValueNode() ? value.asText() : value.toString();
            boolean valid = validType(value, rule.type(), csv);
            if (!valid) errors.add((rule.type() == FieldType.GEOMETRY ? "INVALID_GEOMETRY: "
                    : rule.type() == FieldType.LATITUDE || rule.type() == FieldType.LONGITUDE
                    ? "INVALID_COORDINATES: " : "INVALID_TYPE: ") + rule.name());
            if (!rule.domain().isEmpty() && !rule.domain().contains(text)) errors.add("UNKNOWN_VALUE: " + rule.name() + "=" + text);
            String canonical = text;
            if (valid && Set.of(FieldType.NUMBER, FieldType.INTEGER, FieldType.LATITUDE, FieldType.LONGITUDE).contains(rule.type()))
                canonical = new BigDecimal(text.replace(',', '.')).stripTrailingZeros().toPlainString();
            normalized.put(rule.name(), canonical);
            if (rule.unique() && uniqueValues.getOrDefault(rule.name(), Set.of()).contains(canonical))
                errors.add("DUPLICATE_VALUE: " + rule.name());
        }
        String key;
        try { key = mapper.writeValueAsString(normalized) + "|" + geometry; }
        catch (Exception e) { throw new IllegalStateException(e); }
        if (records.contains(key)) errors.add("DUPLICATE_RECORD");
        // Registros rejeitados não reservam valores únicos nem tornam duplicado um registro válido posterior.
        if (errors.isEmpty()) {
            records.add(key);
            for (FieldRule rule : rules) if (rule.unique() && normalized.containsKey(rule.name()))
                uniqueValues.computeIfAbsent(rule.name(), n -> new HashSet<>()).add(normalized.get(rule.name()));
        }
        return errors;
    }
    private boolean validType(JsonNode value, FieldType type, boolean csv) {
        String text = value.asText();
        try {
            return switch (type) {
                case STRING -> value.isTextual();
                case NUMBER, INTEGER, LATITUDE, LONGITUDE -> {
                    if (!value.isNumber() && !(csv && value.isTextual())) yield false;
                    BigDecimal n = new BigDecimal(text.replace(',', '.'));
                    yield switch (type) {
                        case INTEGER -> n.stripTrailingZeros().scale() <= 0;
                        case LATITUDE -> n.abs().compareTo(BigDecimal.valueOf(90)) <= 0;
                        case LONGITUDE -> n.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
                        default -> true;
                    };
                }
                case BOOLEAN -> value.isBoolean() || csv && Set.of("true", "false").contains(text);
                case DATE -> { LocalDate.parse(text); yield value.isTextual(); }
                case GEOMETRY -> GeometryValidation.valid(csv ? mapper.readTree(text) : value);
            };
        } catch (Exception e) { return false; }
    }
}
