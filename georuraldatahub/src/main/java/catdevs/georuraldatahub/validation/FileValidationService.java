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
    public FileValidationService(CsvRecordValidator csvValidator, GeoJsonRecordValidator geoJsonValidator) {
        this.csvValidator = csvValidator; this.geoJsonValidator = geoJsonValidator;
    }
    public List<RecordValidationResult> validate(InputStream input, String format, List<FieldRule> rules) throws Exception {
        return switch (normalize(format)) {
            case "csv" -> csvValidator.validate(input, rules);
            case "geojson", "json" -> geoJsonValidator.validate(input, rules);
            default -> List.of(RecordValidationResult.invalid(-1, "", List.of("UNSUPPORTED_FORMAT: " + format)));
        };
    }
    /** Produces the only content eligible for treatment, preserving CSV headers / GeoJSON envelope. */
    public byte[] acceptedContent(InputStream original, String format, List<RecordValidationResult> records) throws Exception {
        var valid = records.stream().filter(RecordValidationResult::valid).toList();
        if (valid.isEmpty() || records.stream().anyMatch(r -> r.recordIndex() < 0)) return new byte[0];
        if (normalize(format).equals("csv")) {
            try (PushbackReader reader = new PushbackReader(new InputStreamReader(original, StandardCharsets.UTF_8), 1)) {
                CsvRecordValidator.skipBom(reader);
                List<String> headers = CsvRecordValidator.readRecord(reader);
                headers.set(0, headers.get(0).replaceFirst("^\uFEFF", ""));
                StringBuilder out = new StringBuilder(CsvRecordValidator.encode(headers)).append('\n');
                valid.forEach(r -> out.append(r.rawContent()).append('\n'));
                return out.toString().getBytes(StandardCharsets.UTF_8);
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.createObjectNode(); root.put("type", "FeatureCollection");
        var features = root.putArray("features");
        for (var r : valid) features.add(mapper.readTree(r.rawContent()));
        return mapper.writeValueAsBytes(root);
    }
    private String normalize(String format) { return format == null ? "" : format.toLowerCase(Locale.ROOT); }
}
