package catdevs.georuraldatahub.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

/** Examina todos os registros; não inventa domínios nem unicidade a partir de uma amostra. */
@Component
public class DictionaryInference {
    public List<FieldRule> infer(InputStream input, String format, int maxRecords) throws Exception {
        var fields = new LinkedHashMap<String, Observed>();
        int count = 0;
        boolean geo = false;
        if ("csv".equals(format)) {
            try (var reader = new CsvRecordValidator.TableReader(input)) {
                var header = reader.readRecord();
                if (header == null) throw invalid("Arquivo vazio.");
                header = header.stream().map(String::trim).toList();
                if (new HashSet<>(header).size() != header.size()) throw invalid("Cabeçalho com campos repetidos.");
                for (String name : header) field(fields, name);
                List<String> row;
                while ((row = reader.readRecord()) != null) {
                    if (row.size() == 1 && row.getFirst().isBlank()) continue;
                    if (++count > maxRecords) throw invalid("Limite de registros excedido para geração do dicionário.");
                    if (row.size() != header.size()) throw invalid("Quantidade de colunas inválida no registro " + count + ".");
                    for (int i = 0; i < header.size(); i++)
                        fields.get(header.get(i)).add(TextNode.valueOf(row.get(i).trim()), true, header.get(i));
                }
            }
        } else if ("json".equals(format) || "geojson".equals(format)) {
            JsonNode root;
            try { root = JsonRecordValidator.MAPPER.readTree(input); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw invalid("JSON inválido: confira a estrutura e os campos repetidos."); }
            geo = JsonRecordValidator.isGeoJson(root);
            if (root == null || !root.isObject() && !root.isArray() || "geojson".equals(format) && !geo)
                throw invalid("Estrutura JSON/GeoJSON inválida.");
            Iterable<JsonNode> rows;
            if (geo && "FeatureCollection".equals(root.path("type").asText())) {
                if (!root.path("features").isArray()) throw invalid("FeatureCollection sem lista de feições.");
                rows = root.get("features");
            } else rows = root.isArray() ? root : List.of(root);
            for (var row : rows) {
                if (++count > maxRecords) throw invalid("Limite de registros excedido para geração do dicionário.");
                JsonNode values = geo ? row.path("properties") : row;
                if (!values.isObject() || geo && !"Feature".equals(row.path("type").asText()))
                    throw invalid("Registro " + count + " não possui a estrutura esperada.");
                for (var entry : values.properties()) {
                    if (geo && "geometry".equals(entry.getKey()))
                        throw invalid("A propriedade geometry conflita com a geometria padrão da feição.");
                    field(fields, entry.getKey()).add(entry.getValue(), false, entry.getKey());
                }
            }
        } else throw invalid("Formato não suportado para inferência: " + format);
        if (count == 0) throw invalid("O arquivo não contém registros para montar um dicionário.");
        List<FieldRule> rules = new ArrayList<>();
        for (var entry : fields.entrySet()) {
            var value = entry.getValue();
            rules.add(new FieldRule(entry.getKey(), value.present == count,
                    value.type == null ? FieldType.STRING : value.type));
        }
        if (geo) rules.add(FieldRule.required("geometry", FieldType.GEOMETRY));
        if (rules.isEmpty()) throw invalid("Nenhum campo encontrado no arquivo.");
        return List.copyOf(rules);
    }

    private Observed field(Map<String, Observed> fields, String name) {
        if (name == null || name.isBlank() || !name.equals(name.trim()))
            throw invalid("Campos precisam ter nomes não vazios e sem espaços nas extremidades.");
        if (!fields.containsKey(name) && fields.size() >= 100)
            throw invalid("A geração automática aceita até 100 campos. Divida a estrutura ou cadastre as regras manualmente.");
        return fields.computeIfAbsent(name, ignored -> new Observed());
    }

    private static final class Observed {
        private FieldType type;
        private int present;
        void add(JsonNode value, boolean csv, String name) {
            if (value == null || value.isNull() || value.isTextual() && value.asText().isBlank()) return;
            present++;
            FieldType next = detect(value, csv, name);
            if (type == null || type == next) type = next;
            else if (numeric(type) && numeric(next)) type = FieldType.NUMBER;
            else if (csv || (type == FieldType.DATE && next == FieldType.STRING)
                    || (type == FieldType.STRING && next == FieldType.DATE)) type = FieldType.STRING;
            else throw invalid("O campo '" + name + "' mistura tipos incompatíveis. Normalize seus valores antes de gerar o dicionário.");
        }
        private static boolean numeric(FieldType type) { return type == FieldType.INTEGER || type == FieldType.NUMBER; }
        private static FieldType detect(JsonNode value, boolean csv, String name) {
            if (value.isNumber()) return value.decimalValue().stripTrailingZeros().scale() <= 0 ? FieldType.INTEGER : FieldType.NUMBER;
            if (value.isBoolean()) return FieldType.BOOLEAN;
            if (value.isTextual()) {
                String text = value.asText();
                if (csv) {
                    if (text.equals("true") || text.equals("false")) return FieldType.BOOLEAN;
                    // Códigos com zeros iniciais precisam continuar sendo texto.
                    if (!text.matches("[+-]?0[0-9].*")) {
                        try { return new BigDecimal(text.replace(',', '.')).stripTrailingZeros().scale() <= 0
                                ? FieldType.INTEGER : FieldType.NUMBER; }
                        catch (NumberFormatException ignored) { }
                    }
                }
                try { LocalDate.parse(text); return FieldType.DATE; }
                catch (java.time.format.DateTimeParseException ignored) { return FieldType.STRING; }
            }
            if (value.isObject() && Set.of("Point", "MultiPoint", "LineString", "MultiLineString", "Polygon",
                    "MultiPolygon", "GeometryCollection").contains(value.path("type").asText())) return FieldType.GEOMETRY;
            throw invalid("O campo '" + name + "' contém objetos ou listas sem tipo suportado pelo dicionário.");
        }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
