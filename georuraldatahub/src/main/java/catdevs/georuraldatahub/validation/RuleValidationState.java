package catdevs.georuraldatahub.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** State belongs to one file, never shared between uploads. */
final class RuleValidationState {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private record CompiledRule(FieldRule field, Set<String> domain) { }
    private final List<CompiledRule> rules;
    private final Set<String> known = new HashSet<>();
    private final Set<String> records = new HashSet<>();
    private final Map<String, Set<String>> uniqueValues = new HashMap<>();

    RuleValidationState(List<FieldRule> rules) {
        this.rules = rules.stream().map(rule -> {
            known.add(rule.name());
            return new CompiledRule(rule, new HashSet<>(rule.domain()));
        }).toList();
    }

    List<String> validate(Map<String, JsonNode> row, boolean csv) {
        return validate(row, csv, "", true);
    }
    List<String> validate(Map<String, JsonNode> row, boolean csv, String geometry, boolean trackUniqueness) {
        List<String> errors = new ArrayList<>();
        for (String name : row.keySet()) if (!known.contains(name)) errors.add("UNKNOWN_FIELD: " + name);
        Map<String, String> normalized = new TreeMap<>();
        for (CompiledRule compiled : rules) {
            FieldRule rule = compiled.field();
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
            if (!compiled.domain().isEmpty() && !compiled.domain().contains(text)) errors.add("UNKNOWN_VALUE: " + rule.name() + "=" + text);
            String canonical = text;
            if (valid && switch (rule.type()) {
                case NUMBER, INTEGER, LATITUDE, LONGITUDE -> true;
                default -> false;
            })
                canonical = new BigDecimal(text.replace(',', '.')).stripTrailingZeros().toPlainString();
            normalized.put(rule.name(), canonical);
            // Only approved records reserve unique values, so rejected data cannot poison later records.
            if (trackUniqueness && rule.unique() && uniqueValues.getOrDefault(rule.name(), Set.of()).contains(canonical))
                errors.add("DUPLICATE_VALUE: " + rule.name());
        }
        String key;
        try { key = MAPPER.writeValueAsString(normalized) + "|" + geometry; }
        catch (Exception e) { throw new IllegalStateException(e); }
        if (trackUniqueness && records.contains(key)) errors.add("DUPLICATE_RECORD");
        if (trackUniqueness && errors.isEmpty()) {
            records.add(key);
            for (CompiledRule compiled : rules) {
                FieldRule rule = compiled.field();
                if (rule.unique() && normalized.containsKey(rule.name()))
                    uniqueValues.computeIfAbsent(rule.name(), n -> new HashSet<>()).add(normalized.get(rule.name()));
            }
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
                case GEOMETRY -> GeometryValidation.valid(csv ? MAPPER.readTree(text) : value);
            };
        } catch (Exception e) { return false; }
    }
}
