package catdevs.georuraldatahub.validation;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class GeoJsonContent {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private GeoJsonContent() {}

    public static JsonNode featureCollection(JsonNode root) {
        if (root == null) throw new IllegalArgumentException("O arquivo JSON está vazio.");
        if (root.path("type").asText().equals("FeatureCollection")) {
            if (!root.path("features").isArray()) throw new IllegalArgumentException("GeoJSON sem lista features.");
            return root;
        }
        var output = MAPPER.createObjectNode().put("type", "FeatureCollection");
        var features = output.putArray("features");
        if (root.path("type").asText().equals("Feature")) { features.add(root); return output; }
        if (!root.isObject() && !root.isArray()) throw new IllegalArgumentException("JSON precisa conter objetos para visualização.");
        Iterable<JsonNode> records = root.isArray() ? root : List.of(root);
        int index = 0;
        for (var record : records) features.add(feature(record, index++));
        return output;
    }

    public static ObjectNode feature(JsonNode record, int index) {
        if (!record.isObject()) throw new IllegalArgumentException("Registro " + index + " não é um objeto JSON.");
        var geometry = record.get("geometry");
        var properties = ((ObjectNode) record).deepCopy();
        if (geometry != null && !geometry.isNull()) {
            if (!GeometryValidation.valid(geometry)) throw new IllegalArgumentException("Geometria inválida no registro " + index + ".");
            properties.remove("geometry");
        } else {
            Map<String, JsonNode> fields = new HashMap<>();
            record.properties().forEach(entry -> fields.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue()));
            var latitude = find(fields, "latitude", "lat", "nu_latitude");
            var longitude = find(fields, "longitude", "lon", "lng", "long", "nu_longitude");
            if (latitude == null || longitude == null) throw new IllegalArgumentException(
                    "Arquivo validado, mas sem geometria ou latitude/longitude para exibir no mapa (registro " + index + ").");
            double lat = number(latitude, index), lon = number(longitude, index);
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180) throw new IllegalArgumentException("Coordenadas fora dos limites no registro " + index + ".");
            var point = MAPPER.createObjectNode().put("type", "Point");
            point.putArray("coordinates").add(lon).add(lat);
            geometry = point;
        }
        var feature = MAPPER.createObjectNode().put("type", "Feature");
        feature.set("geometry", geometry); feature.set("properties", properties);
        return feature;
    }
    private static JsonNode find(Map<String, JsonNode> fields, String... names) {
        for (var name : names) if (fields.containsKey(name)) return fields.get(name);
        return null;
    }
    private static double number(JsonNode value, int index) {
        try {
            if (!value.isNumber() && !value.isTextual()) throw new NumberFormatException();
            double number = Double.parseDouble(value.asText().trim().replace(',', '.'));
            if (!Double.isFinite(number)) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Coordenada inválida no registro " + index + "."); }
    }
}
