package catdevs.georuraldatahub.validation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

@Component
public class CsvRecordValidator {
    public List<RecordValidationResult> validate(InputStream inputStream, List<FieldRule> rules) throws IOException {
        List<RecordValidationResult> results = new ArrayList<>();
        RuleValidationState state = new RuleValidationState();
        try (PushbackReader reader = new PushbackReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 1)) {
            skipBom(reader);
            List<String> headers = readRecord(reader);
            if (headers == null) return fileError("EMPTY_FILE");
            headers.set(0, headers.get(0).replaceFirst("^\uFEFF", ""));
            headers = headers.stream().map(String::trim).toList();
            if (headers.stream().anyMatch(String::isBlank) || new HashSet<>(headers).size() != headers.size())
                return fileError("INVALID_HEADER");
            Set<String> known = new HashSet<>();
            for (FieldRule r : rules) known.add(r.name());
            List<String> headerErrors = new ArrayList<>();
            for (String h : headers) if (!known.contains(h)) headerErrors.add("UNKNOWN_FIELD: " + h);
            for (FieldRule r : rules) if (r.required() && !headers.contains(r.name())) headerErrors.add("REQUIRED_FIELD: " + r.name());
            if (!headerErrors.isEmpty()) return List.of(RecordValidationResult.invalid(-1, "", headerErrors));
            List<String> values;
            int index = 0;
            while ((values = readRecord(reader)) != null) {
                if (values.size() == 1 && values.get(0).isBlank()) continue;
                String raw = encode(values);
                if (values.size() != headers.size()) {
                    results.add(RecordValidationResult.invalid(index++, raw, List.of("INVALID_COLUMN_COUNT")));
                    continue;
                }
                Map<String, JsonNode> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) row.put(headers.get(i), TextNode.valueOf(values.get(i).trim()));
                List<String> errors = state.validate(row, rules, true);
                results.add(errors.isEmpty() ? RecordValidationResult.ok(index, raw)
                        : RecordValidationResult.invalid(index, raw, errors));
                index++;
            }
            return results.isEmpty() ? fileError("EMPTY_FILE") : results;
        } catch (IllegalArgumentException e) { return fileError("MALFORMED_FILE: " + e.getMessage()); }
    }
    private List<RecordValidationResult> fileError(String error) {
        return List.of(RecordValidationResult.invalid(-1, "", List.of(error)));
    }
    static void skipBom(PushbackReader reader) throws IOException {
        int first = reader.read();
        if (first != -1 && first != '\uFEFF') reader.unread(first);
    }
    static String encode(List<String> fields) {
        return fields.stream().map(v -> "\"" + v.replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining(","));
    }
    // RFC 4180: escaped quotes and multiline fields. Ambiguous framing rejects the whole file.
    static List<String> readRecord(PushbackReader reader) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false, any = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            any = true;
            char c = (char) ch;
            if (quoted) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; closed = true; if (next != -1) reader.unread(next); }
                } else field.append(c);
            } else if (c == ',') {
                fields.add(field.toString()); field.setLength(0); closed = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r') { int next = reader.read(); if (next != '\n' && next != -1) reader.unread(next); }
                fields.add(field.toString()); return fields;
            } else if (c == '"' && field.isEmpty() && !closed) quoted = true;
            else {
                if (closed || c == '"') throw new IllegalArgumentException("Aspas CSV inválidas.");
                field.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Campo CSV sem fechamento de aspas.");
        if (!any) return null;
        fields.add(field.toString()); return fields;
    }
}
