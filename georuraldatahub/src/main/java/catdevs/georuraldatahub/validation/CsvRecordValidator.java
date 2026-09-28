package catdevs.georuraldatahub.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class CsvRecordValidator {

    public List<RecordValidationResult> validate(InputStream inputStream, List<FieldRule> rules) throws IOException {
        List<RecordValidationResult> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return results;
            }

            String[] headers = splitCsvLine(headerLine);

            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] values = splitCsvLine(line);
                Map<String, String> row = toRowMap(headers, values);

                List<String> errors = validateRow(row, rules);

                results.add(errors.isEmpty()
                        ? RecordValidationResult.ok(index, line)
                        : RecordValidationResult.invalid(index, line, errors));

                index++;
            }
        }

        return results;
    }

    private Map<String, String> toRowMap(String[] headers, String[] values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String value = i < values.length ? values[i].trim() : "";
            row.put(headers[i].trim(), value);
        }
        return row;
    }

    private List<String> validateRow(Map<String, String> row, List<FieldRule> rules) {
        List<String> errors = new ArrayList<>();

        for (FieldRule rule : rules) {
            String value = row.get(rule.name());

            if (rule.required() && (value == null || value.isBlank())) {
                errors.add("Campo obrigatório ausente: " + rule.name());
                continue;
            }

            if (value == null || value.isBlank()) {
                continue;
            }

            if (rule.type() == FieldType.NUMBER && !isNumeric(value)) {
                errors.add("Campo '" + rule.name() + "' deveria ser numérico, veio: '" + value + "'");
            }
        }

        return errors;
    }

    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                insideQuotes = !insideQuotes;
            } else if (c == ',' && !insideQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}