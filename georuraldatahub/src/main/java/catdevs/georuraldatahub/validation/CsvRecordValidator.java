package catdevs.georuraldatahub.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

@Component
public class CsvRecordValidator {

    public List<RecordValidationResult> validate(
            InputStream inputStream,
            List<FieldRule> rules) throws IOException {

        List<RecordValidationResult> results = new ArrayList<>();
        RuleValidationState state = new RuleValidationState();

        try (var reader = new TableReader(inputStream)) {
            List<String> headers = reader.readRecord();

            if (headers == null) {
                return fileError("EMPTY_FILE");
            }

            headers.set(0, headers.get(0).replaceFirst("^\uFEFF", ""));
            headers = headers.stream().map(String::trim).toList();

            if (headers.stream().anyMatch(String::isBlank)
                    || new HashSet<>(headers).size() != headers.size()) {
                return fileError("INVALID_HEADER");
            }

            Set<String> known = new HashSet<>();

            for (FieldRule rule : rules) {
                known.add(rule.name());
            }

            List<String> headerErrors = new ArrayList<>();

            for (String header : headers) {
                if (!known.contains(header)) {
                    headerErrors.add("UNKNOWN_FIELD: " + header);
                }
            }

            for (FieldRule rule : rules) {
                if (rule.required() && !headers.contains(rule.name())) {
                    headerErrors.add("REQUIRED_FIELD: " + rule.name());
                }
            }

            if (!headerErrors.isEmpty()) {
                return List.of(
                        RecordValidationResult.invalid(-1, "", headerErrors));
            }

            List<String> values;
            int index = 0;

            while ((values = reader.readRecord()) != null) {
                if (values.size() == 1 && values.get(0).isBlank()) {
                    continue;
                }

                String raw = encode(values);

                if (values.size() != headers.size()) {
                    results.add(RecordValidationResult.invalid(
                            index++, raw,
                            List.of("INVALID_COLUMN_COUNT")));
                    continue;
                }

                Map<String, JsonNode> row = new LinkedHashMap<>();

                for (int i = 0; i < headers.size(); i++) {
                    row.put(
                            headers.get(i),
                            TextNode.valueOf(values.get(i).trim()));
                }

                List<String> errors = state.validate(row, rules, true);

                results.add(errors.isEmpty()
                        ? RecordValidationResult.ok(index, raw)
                        : RecordValidationResult.invalid(index, raw, errors));

                index++;
            }

            return results.isEmpty()
                    ? fileError("EMPTY_FILE")
                    : results;

        } catch (IllegalArgumentException e) {
            return fileError("MALFORMED_FILE: " + e.getMessage());
        }
    }

    private List<RecordValidationResult> fileError(String error) {
        return List.of(
                RecordValidationResult.invalid(-1, "", List.of(error)));
    }

    public static void skipBom(PushbackReader reader) throws IOException {
        int first = reader.read();

        if (first != -1 && first != '\uFEFF') {
            reader.unread(first);
        }
    }

    public static String encode(List<String> fields) {
        return fields.stream()
                .map(value -> "\""
                        + value.replace("\"", "\"\"")
                        + "\"")
                .collect(Collectors.joining(","));
    }

    /**
     * Parser compartilhado para validação, inspeção e conversão de registros.
     */
    public static final class TableReader implements AutoCloseable {

        private final PushbackReader reader;
        private final char delimiter;

        public TableReader(InputStream input) throws IOException {
            reader = new PushbackReader(
                    new BufferedReader(
                            new InputStreamReader(
                                    input, StandardCharsets.UTF_8)),
                    64 * 1024);

            skipBom(reader);

            StringBuilder header = new StringBuilder();
            boolean quoted = false;
            int next;

            while ((next = reader.read()) != -1) {
                char value = (char) next;
                header.append(value);

                if (value == '"') {
                    quoted = !quoted;
                }

                if (!quoted && (value == '\n' || value == '\r')) {
                    break;
                }

                if (header.length() >= 64 * 1024) {
                    throw new IllegalArgumentException(
                            "Cabeçalho CSV excede 64 KiB.");
                }
            }

            reader.unread(header.toString().toCharArray());
            delimiter = detectDelimiter(header.toString());
        }

        /**
         * Retorna o delimitador detectado no cabeçalho.
         */
        public char getDelimiter() {
            return delimiter;
        }

        public List<String> readRecord() throws IOException {
            return CsvRecordValidator.readRecord(reader, delimiter);
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

        private static char detectDelimiter(String header) {
            int commas = 0;
            int semicolons = 0;
            int tabs = 0;
            boolean quoted = false;

            for (int i = 0; i < header.length(); i++) {
                char c = header.charAt(i);

                if (c == '"') {
                    quoted = !quoted;
                } else if (!quoted) {
                    if (c == ',') {
                        commas++;
                    } else if (c == ';') {
                        semicolons++;
                    } else if (c == '\t') {
                        tabs++;
                    }
                }
            }

            if (semicolons > commas && semicolons >= tabs) {
                return ';';
            }

            if (tabs > commas) {
                return '\t';
            }

            return ',';
        }
    }

    /**
     * Lê um registro CSV respeitando aspas, delimitadores e quebras de linha
     * dentro de campos citados.
     */
    public static List<String> readRecord(PushbackReader reader)
            throws IOException {
        return readRecord(reader, ',');
    }

    private static List<String> readRecord(
            PushbackReader reader,
            char delimiter) throws IOException {

        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean quoted = false;
        boolean closed = false;
        boolean any = false;

        int ch;

        while ((ch = reader.read()) != -1) {
            any = true;
            char c = (char) ch;

            if (quoted) {
                if (c == '"') {
                    int next = reader.read();

                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        closed = true;

                        if (next != -1) {
                            reader.unread(next);
                        }
                    }
                } else {
                    field.append(c);
                }

            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                closed = false;

            } else if (c == '\n' || c == '\r') {
                if (c == '\r') {
                    int next = reader.read();

                    if (next != '\n' && next != -1) {
                        reader.unread(next);
                    }
                }

                fields.add(field.toString());
                return fields;

            } else if (c == '"' && field.isEmpty() && !closed) {
                quoted = true;

            } else {
                if (closed || c == '"') {
                    throw new IllegalArgumentException(
                            "Aspas CSV inválidas.");
                }

                field.append(c);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException(
                    "Campo CSV sem fechamento de aspas.");
        }

        if (!any) {
            return null;
        }

        fields.add(field.toString());
        return fields;
    }
}