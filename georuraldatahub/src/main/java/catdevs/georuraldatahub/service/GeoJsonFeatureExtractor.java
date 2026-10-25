package catdevs.georuraldatahub.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import catdevs.georuraldatahub.entity.enums.GeometryType;
import catdevs.georuraldatahub.repository.FeatureRepository.NewFeature;

/**
 * Converte um GeoJSON FeatureCollection nas feições que serão gravadas na
 * tabela feicao e descobre o tipo de geometria da camada.
 */
public final class GeoJsonFeatureExtractor {

    private static final int MAX_ORIGIN_ID_LENGTH = 100;

    private GeoJsonFeatureExtractor() {
    }

    public record Extraction(List<NewFeature> features, GeometryType geometryType) {
    }

    public static Extraction extract(JsonNode collection) {
        JsonNode array = collection.get("features");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("O GeoJSON precisa possuir um array chamado features.");
        }

        List<NewFeature> features = new ArrayList<>(array.size());
        Set<GeometryType> types = EnumSet.noneOf(GeometryType.class);
        int position = 0;

        for (JsonNode feature : array) {
            position++;
            JsonNode geometry = feature.get("geometry");
            if (geometry == null || geometry.isNull() || !geometry.hasNonNull("type")) {
                throw new IllegalArgumentException("A feição " + position + " não possui geometria.");
            }
            types.add(toGeometryType(geometry.get("type").asText()));
            features.add(new NewFeature(originId(feature, position), geometry.toString(), attributes(feature)));
        }

        if (features.isEmpty()) {
            throw new IllegalArgumentException("O GeoJSON não possui feições.");
        }

        GeometryType type = types.size() == 1 ? types.iterator().next() : GeometryType.MIXED;
        return new Extraction(features, type);
    }

    private static GeometryType toGeometryType(String geoJsonType) {
        try {
            return GeometryType.valueOf(geoJsonType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GeometryType.MIXED; // ex.: GeometryCollection
        }
    }

    /** Usa o "id" da feição quando existe; senão, a posição dela no arquivo (a partir de 1). */
    private static String originId(JsonNode feature, int position) {
        JsonNode id = feature.get("id");
        String value = (id == null || id.isNull()) ? String.valueOf(position) : id.asText();
        return value.length() > MAX_ORIGIN_ID_LENGTH ? value.substring(0, MAX_ORIGIN_ID_LENGTH) : value;
    }

    private static String attributes(JsonNode feature) {
        JsonNode properties = feature.get("properties");
        return (properties == null || properties.isNull()) ? null : properties.toString();
    }
}