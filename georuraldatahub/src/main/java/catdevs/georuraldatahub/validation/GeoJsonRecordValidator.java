package catdevs.georuraldatahub.validation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GeoJsonRecordValidator {

    private static final Set<String> VALID_GEOMETRY_TYPES = Set.of(
            "Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RecordValidationResult> validate(InputStream inputStream, List<FieldRule> rules) throws Exception {
        List<RecordValidationResult> results = new ArrayList<>();

        JsonNode root = objectMapper.readTree(inputStream);

        if (!root.has("features") || !root.get("features").isArray()) {
            results.add(RecordValidationResult.invalid(
                    0, root.toString(), List.of("GeoJSON inválido: não é um FeatureCollection com 'features'.")
            ));
            return results;
        }

        int index = 0;
        for (JsonNode feature : root.get("features")) {
            List<String> errors = new ArrayList<>();

            JsonNode geometry = feature.get("geometry");
            if (geometry == null || geometry.isNull()) {
                errors.add("Geometria ausente.");
            } else {
                JsonNode typeNode = geometry.get("type");
                if (typeNode == null || !VALID_GEOMETRY_TYPES.contains(typeNode.asText())) {
                    errors.add("Tipo de geometria inválido ou ausente: "
                            + (typeNode != null ? typeNode.asText() : "null"));
                }
                JsonNode coordinates = geometry.get("coordinates");
                if (coordinates == null || !coordinates.isArray() || coordinates.isEmpty()) {
                    errors.add("Coordenadas ausentes ou vazias.");
                }
            }

            JsonNode properties = feature.get("properties");
            for (FieldRule rule : rules) {
                JsonNode value = properties != null ? properties.get(rule.name()) : null;

                if (rule.required() && (value == null || value.isNull()
                        || (value.isTextual() && value.asText().isBlank()))) {
                    errors.add("Campo obrigatório ausente: " + rule.name());
                    continue;
                }

                if (value == null || value.isNull()) {
                    continue;
                }

                if (rule.type() == FieldType.NUMBER && !value.isNumber() && !isNumericText(value.asText())) {
                    errors.add("Campo '" + rule.name() + "' deveria ser numérico, veio: '" + value.asText() + "'");
                }
            }

            results.add(errors.isEmpty()
                    ? RecordValidationResult.ok(index, feature.toString())
                    : RecordValidationResult.invalid(index, feature.toString(), errors));

            index++;
        }

        return results;
    }

    private boolean isNumericText(String value) {
        try {
            Double.parseDouble(value.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}