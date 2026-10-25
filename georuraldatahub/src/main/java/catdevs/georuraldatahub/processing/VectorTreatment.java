package catdevs.georuraldatahub.processing;

import catdevs.georuraldatahub.collection.OfficialDataClient;
import catdevs.georuraldatahub.validation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.*;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.*;
import org.locationtech.jts.operation.overlayng.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.springframework.stereotype.Component;

@Component
public class VectorTreatment {
    public static final String BOUNDARY_URL = catdevs.georuraldatahub.collection.ParanaSpatialFilter.BOUNDARY_URL;
    private final OfficialDataClient http;
    private final ProcessingProperties limits;
    private final ObjectMapper mapper = new ObjectMapper();
    public VectorTreatment(OfficialDataClient http, ProcessingProperties limits) { this.http = http; this.limits = limits; }
    public record Result(JsonNode content, JsonNode provenance, int inputCount, int outputCount) { }

    public Result apply(JsonNode input, List<FieldRule> dictionary, TreatmentRequest request) throws IOException {
        validateRequest(request);
        if (!input.path("features").isArray() || input.path("features").isEmpty())
            throw new IllegalArgumentException("O tratamento exige feições aprovadas com geometria.");
        if (input.path("features").size() > limits.getMaxFeatures()) throw new IllegalArgumentException("Limite de feições excedido.");
        catdevs.georuraldatahub.collection.ParanaSpatialFilter.Boundary boundary;
        try (var budget = http.collectionBudget()) {
            boundary = new catdevs.georuraldatahub.collection.ParanaSpatialFilter(http).boundary();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Consulta do limite do Paraná interrompida.", e);
        }
        byte[] boundaryBytes = boundary.original().content();
        Geometry mask = boundary.geometry();
        var preparedMask = org.locationtech.jts.geom.prep.PreparedGeometryFactory.prepare(mask);
        var output = mapper.createObjectNode().put("type", "FeatureCollection");
        ArrayNode features = output.putArray("features");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(limits.getTimeoutSeconds());
        int index = 0;
        for (JsonNode original : input.path("features")) {
            if (System.nanoTime() > deadline) throw new IllegalArgumentException("Tratamento excedeu o prazo configurado.");
            index++;
            Geometry source = geometry(original.path("geometry"));
            if (!preparedMask.intersects(source)) continue;
            Geometry clipped = clip(source, mask, preparedMask);
            if (clipped.isEmpty()) continue;
            ObjectNode properties = original.path("properties").isObject()
                    ? ((ObjectNode) original.path("properties")).deepCopy() : mapper.createObjectNode();
            for (var rule : dictionary) {
                JsonNode value = properties.get(rule.name());
                if (value != null && !value.isNull() && rule.type() != FieldType.GEOMETRY)
                    properties.set(rule.name(), convert(value, rule.type(), null, null, null));
            }
            ObjectNode beforeChanges = properties.deepCopy();
            for (var change : request.attributes()) {
                JsonNode value = beforeChanges.get(change.source());
                if (value == null) throw new IllegalArgumentException("Campo de transformação não encontrado: " + change.source());
                if (!change.source().equals(change.target()) && beforeChanges.has(change.target()))
                    throw new IllegalArgumentException("A renomeação sobrescreveria o campo: " + change.target());
                properties.remove(change.source());
                properties.set(change.target(), value.isNull() ? value : convert(value, change.type(),
                        change.datePattern(), change.factor(), change.offset()));
            }
            ObjectNode feature = mapper.createObjectNode().put("type", "Feature");
            feature.set("id", original.hasNonNull("id") ? original.get("id") : mapper.getNodeFactory().numberNode(index));
            feature.set("properties", properties); feature.set("geometry", node(clipped)); features.add(feature);
        }
        if (features.isEmpty()) throw new IllegalArgumentException("Nenhuma feição aprovada intersecta o Paraná.");
        if (request.dissolve()) output.set("features", dissolve(features, request.dissolveBy()));
        if (System.nanoTime() > deadline) throw new IllegalArgumentException("Tratamento excedeu o prazo configurado.");
        if (mapper.writeValueAsBytes(output).length > limits.getMaxOutputBytes())
            throw new IllegalArgumentException("Resultado tratado excede o limite de tamanho.");
        ObjectNode provenance = mapper.createObjectNode().put("boundaryOrigin", BOUNDARY_URL)
                .put("boundarySha256", hash(boundaryBytes)).put("targetSrid", 4326)
                .put("clippedToParana", true).put("dissolved", request.dissolve())
                .put("boundaryGeometryPolicy", "Limite oficial reparado por GeometryFixer antes da interseção; original preservado.")
                .put("areaPolicy", "Áreas informadas pela origem são preservadas; não são recalculadas após o recorte.");
        provenance.set("attributes", mapper.valueToTree(request.attributes()));
        provenance.set("dissolveBy", mapper.valueToTree(request.dissolveBy()));
        return new Result(output, provenance, input.path("features").size(), output.path("features").size());
    }

    private void validateRequest(TreatmentRequest request) {
        if (request.attributes().size() > 100 || request.dissolveBy().size() > 16)
            throw new IllegalArgumentException("Excesso de regras de tratamento.");
        if (request.dissolveBy().stream().anyMatch(f -> f.isBlank() || Set.of("_origens", "_atributos_originais").contains(f))
                || new HashSet<>(request.dissolveBy()).size() != request.dissolveBy().size())
            throw new IllegalArgumentException("Campos de dissolução devem ser únicos, não vazios e não reservados para rastreabilidade.");
        if (!request.dissolve() && !request.dissolveBy().isEmpty())
            throw new IllegalArgumentException("Ative a dissolução para informar seus campos de agrupamento.");
        Set<String> sources = new HashSet<>(), targets = new HashSet<>();
        for (var rule : request.attributes()) {
            if (rule.source() == null || rule.source().isBlank() || rule.target() == null || rule.target().isBlank()
                    || !sources.add(rule.source()) || !targets.add(rule.target()) || rule.type() == null)
                throw new IllegalArgumentException("Transformações exigem origem e destino únicos e um tipo.");
            if (rule.targetUnit() != null && (rule.targetUnit().isBlank() || rule.factor() == null))
                throw new IllegalArgumentException("Informe o fator de conversão e uma unidade de destino não vazia.");
            boolean numeric = rule.type() == FieldType.NUMBER || rule.type() == FieldType.INTEGER;
            if ((!numeric && (rule.factor() != null || rule.offset() != null || rule.targetUnit() != null))
                    || rule.type() == FieldType.GEOMETRY || rule.type() == FieldType.LATITUDE || rule.type() == FieldType.LONGITUDE)
                throw new IllegalArgumentException("Converta atributos escalares; geometria e coordenadas não podem ser alteradas como campos.");
        }
        for (var rule : request.attributes())
            if (!rule.source().equals(rule.target()) && sources.contains(rule.target()))
                throw new IllegalArgumentException("Renomeações encadeadas ou circulares não são permitidas.");
    }
    private JsonNode convert(JsonNode value, FieldType type, String pattern, BigDecimal factor, BigDecimal offset) {
        if (!value.isValueNode()) throw new IllegalArgumentException("Conversões de atributos exigem valores escalares.");
        String text = value.asText().trim();
        // A validação aceita campos opcionais vazios; eles continuam ausentes, não viram zero/data inválida.
        if (text.isEmpty() && type != FieldType.STRING) return mapper.getNodeFactory().nullNode();
        try {
            return switch (type) {
                case STRING -> mapper.getNodeFactory().textNode(text);
                case NUMBER, INTEGER, LATITUDE, LONGITUDE -> {
                    BigDecimal number = new BigDecimal(text.replace(',', '.'));
                    number = number.multiply(factor == null ? BigDecimal.ONE : factor).add(offset == null ? BigDecimal.ZERO : offset);
                    yield type == FieldType.INTEGER ? mapper.getNodeFactory().numberNode(number.toBigIntegerExact())
                            : mapper.getNodeFactory().numberNode(number.stripTrailingZeros());
                }
                case BOOLEAN -> {
                    if (!Set.of("true", "false").contains(text.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
                    yield mapper.getNodeFactory().booleanNode(Boolean.parseBoolean(text));
                }
                case DATE -> mapper.getNodeFactory().textNode(LocalDate.parse(text,
                        pattern == null ? DateTimeFormatter.ISO_LOCAL_DATE
                                : new DateTimeFormatterBuilder().appendPattern(pattern).parseDefaulting(java.time.temporal.ChronoField.ERA, 1)
                                        .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)).toString());
                default -> value;
            };
        } catch (RuntimeException e) { throw new IllegalArgumentException("Não foi possível converter o valor '" + text + "' para " + type + ".", e); }
    }
    private ArrayNode dissolve(ArrayNode input, List<String> fields) throws IOException {
        if (new HashSet<>(fields).size() != fields.size() || fields.stream().anyMatch(f -> f == null || f.isBlank()))
            throw new IllegalArgumentException("Campos de dissolução devem ser únicos e não vazios.");
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        for (JsonNode feature : input) {
            ArrayNode key = mapper.createArrayNode();
            for (String field : fields) {
                if (!feature.path("properties").has(field)) throw new IllegalArgumentException("Campo de dissolução não encontrado: " + field);
                key.add(feature.path("properties").get(field));
            }
            groups.computeIfAbsent(key.toString(), ignored -> new ArrayList<>()).add(feature);
        }
        ArrayNode output = mapper.createArrayNode();
        int index = 0;
        for (var group : groups.values()) {
            List<Geometry> geometries = new ArrayList<>();
            ObjectNode properties = mapper.createObjectNode();
            for (String field : fields) properties.set(field, group.getFirst().path("properties").get(field));
            ArrayNode origins = properties.putArray("_origens"), attributes = properties.putArray("_atributos_originais");
            for (var feature : group) {
                geometries.add(geometry(feature.path("geometry"))); origins.add(feature.get("id"));
                attributes.add(feature.path("properties"));
            }
            ObjectNode feature = mapper.createObjectNode().put("type", "Feature").put("id", "dissolvida-" + ++index);
            feature.set("geometry", node(UnaryUnionOp.union(geometries))); feature.set("properties", properties); output.add(feature);
        }
        return output;
    }
    private Geometry geometry(JsonNode node) throws IOException {
        try {
            Geometry result = new GeoJsonReader().read(node.toString());
            if (result.isEmpty() || !result.isValid()) throw new IllegalArgumentException("Geometria inválida.");
            for (Coordinate c : result.getCoordinates())
                if (!Double.isFinite(c.x) || !Double.isFinite(c.y) || Math.abs(c.x) > 180 || Math.abs(c.y) > 90)
                    throw new IllegalArgumentException("A camada validada precisa estar em EPSG:4326.");
            return result;
        } catch (org.locationtech.jts.io.ParseException e) { throw new IOException("Geometria inválida no tratamento.", e); }
    }
    private Geometry clip(Geometry source, Geometry mask, org.locationtech.jts.geom.prep.PreparedGeometry preparedMask) {
        if (preparedMask.covers(source)) return source;
        // Coleções mistas precisam preservar cada dimensão (pontos, linhas e polígonos).
        if (source instanceof GeometryCollection collection) {
            List<Geometry> parts = new ArrayList<>();
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                Geometry part = collection.getGeometryN(i);
                if (!preparedMask.intersects(part)) continue;
                Geometry clipped = clip(part, mask, preparedMask);
                if (!clipped.isEmpty()) parts.add(clipped);
            }
            return source.getFactory().buildGeometry(parts);
        }
        return sameDimension(OverlayNGRobust.overlay(source, mask, OverlayNG.INTERSECTION), source.getDimension());
    }
    private Geometry sameDimension(Geometry source, int dimension) {
        if (source.isEmpty()) return source;
        if (source instanceof GeometryCollection collection) {
            List<Geometry> kept = new ArrayList<>();
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                Geometry item = sameDimension(collection.getGeometryN(i), dimension);
                if (!item.isEmpty()) kept.add(item);
            }
            return source.getFactory().buildGeometry(kept);
        }
        return source.getDimension() == dimension ? source : source.getFactory().createGeometryCollection();
    }
    private JsonNode node(Geometry geometry) throws IOException {
        var writer = new GeoJsonWriter(12); writer.setEncodeCRS(false); return mapper.readTree(writer.write(geometry));
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
