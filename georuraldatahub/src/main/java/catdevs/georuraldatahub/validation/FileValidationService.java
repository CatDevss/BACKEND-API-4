package catdevs.georuraldatahub.validation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FileValidationService {
    private final CsvRecordValidator csvValidator;
    private final GeoJsonRecordValidator geoJsonValidator;
    private final JsonRecordValidator jsonValidator;
    public FileValidationService(CsvRecordValidator csvValidator, GeoJsonRecordValidator geoJsonValidator) {
        this.csvValidator = csvValidator; this.geoJsonValidator = geoJsonValidator;
        this.jsonValidator = new JsonRecordValidator(geoJsonValidator);
    }
    public List<RecordValidationResult> validate(InputStream input, String format, List<FieldRule> rules) throws Exception {
        return switch (normalize(format)) {
            case "csv" -> csvValidator.validate(input, rules);
            case "geojson" -> geoJsonValidator.validate(input, rules);
            case "json" -> jsonValidator.validate(input, rules);
            default -> List.of(RecordValidationResult.invalid(-1, "", List.of("UNSUPPORTED_FORMAT: " + format)));
        };
    }
    public byte[] acceptedContent(InputStream original, String format, List<RecordValidationResult> records) throws Exception {
        var valid = records.stream().filter(RecordValidationResult::valid).toList();
        // Índice negativo indica falha do arquivo inteiro; nesse caso, nenhum registro segue adiante.
        if (valid.isEmpty() || records.stream().anyMatch(r -> r.recordIndex() < 0)) return new byte[0];
        if (normalize(format).equals("csv")) {
            try (var reader = new CsvRecordValidator.TableReader(original)) {
                List<String> headers = reader.readRecord().stream().map(String::trim).toList();
                StringBuilder out = new StringBuilder(CsvRecordValidator.encode(headers)).append('\n');
                valid.forEach(r -> out.append(r.rawContent()).append('\n'));
                return out.toString().getBytes(StandardCharsets.UTF_8);
            }
        }
        ObjectMapper mapper = JsonRecordValidator.MAPPER;
        var originalRoot = mapper.readTree(original);
        if (normalize(format).equals("json") && !JsonRecordValidator.isGeoJson(originalRoot)) {
            if (originalRoot.isObject()) return mapper.writeValueAsBytes(mapper.readTree(valid.getFirst().rawContent()));
            var array = mapper.createArrayNode();
            for (var record : valid) array.add(mapper.readTree(record.rawContent()));
            return mapper.writeValueAsBytes(array);
        }
        var root = mapper.createObjectNode().put("type", "FeatureCollection");
        if (originalRoot != null && originalRoot.has("crs")) root.set("crs", originalRoot.get("crs"));
        var features = root.putArray("features");
        for (var record : valid) features.add(mapper.readTree(record.rawContent()));
        return mapper.writeValueAsBytes(root);
    }
    private String normalize(String format) { return format == null ? "" : format.toLowerCase(Locale.ROOT); }
}
