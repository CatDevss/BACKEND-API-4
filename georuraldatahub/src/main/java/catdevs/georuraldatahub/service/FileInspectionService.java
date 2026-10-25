package catdevs.georuraldatahub.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.validation.CsvRecordValidator;

@Service
public class FileInspectionService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int HEADER_BYTES_TO_SHOW = 32;
    private static final int SAMPLE_BYTES = 4096;
    private static final int MAX_JSON_INSPECTION_BYTES = 5 * 1024 * 1024;
    private static final int MAX_LINE_CHARS = 1_000_000;

    private final FileService fileService;
    private final BucketService bucketService;
    private final ObjectMapper objectMapper;

    public FileInspectionService(
            FileService fileService,
            BucketService bucketService) {

        this.fileService = fileService;
        this.bucketService = bucketService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Retorna os metadados básicos do arquivo.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMetadata(Long fileId)
            throws IOException {

        File file = findFile(fileId);

        byte[] sample = bucketService.readObjectPrefix(
                requireLocation(file),
                SAMPLE_BYTES);

        Long registeredSize = file.getSize();

        /*
         * Se o tamanho registrado for desconhecido, não podemos
         * garantir que o conteúdo foi lido integralmente.
         *
         * Se o tamanho registrado for maior que o tamanho lido,
         * sabemos que recebemos apenas uma amostra.
         *
         * Se ambos forem iguais, consideramos a leitura completa.
         */
        boolean isSample = registeredSize == null
                || registeredSize > sample.length;

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("id", file.getId());
        result.put("nome", file.getName());
        result.put("formato", file.getFormatFile());
        result.put("tamanhoRegistradoBytes", registeredSize);
        result.put("tamanhoLidoBytes", sample.length);
        result.put("tamanhoLidoEhAmostra", isSample);
        result.put("hash", file.getHash());
        result.put("localizacao", file.getLocation());
        result.put("dataRecebimento", file.getReceivedDate());
        result.put("texto", isTextFile(file, sample));
        result.put("dataInspecao", LocalDateTime.now());

        return result;
    }

    /**
     * Retorna uma página do conteúdo textual sem ler todo o arquivo
     * para calcular o número total de linhas.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTextContent(
            Long fileId,
            int page,
            int size) throws IOException {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O parâmetro page não pode ser negativo.");
        }

        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        size = Math.min(size, MAX_PAGE_SIZE);

        File file = findFile(fileId);
        String location = requireLocation(file);

        byte[] sample = bucketService.readObjectPrefix(
                location,
                SAMPLE_BYTES);

        if (!isTextFile(file, sample)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "A visualização paginada está disponível apenas para arquivos textuais.");
        }

        long startIndex = (long) page * size;

        List<String> pageLines = new ArrayList<>(size);
        long linesSkipped = 0;
        boolean hasNextPage = false;

        try (InputStream input =
                     bucketService.openObjectStream(location);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(
                             input,
                             StandardCharsets.UTF_8))) {

            String line;

            /*
             * Avança até o começo da página solicitada.
             */
            while (linesSkipped < startIndex) {
                line = readBoundedLine(reader);

                if (line == null) {
                    break;
                }

                linesSkipped++;
            }

            /*
             * Lê apenas as linhas que pertencem à página.
             */
            while (pageLines.size() < size) {
                line = readBoundedLine(reader);

                if (line == null) {
                    break;
                }

                pageLines.add(line);
            }

            /*
             * Lê no máximo uma linha adicional para verificar
             * se existe uma próxima página.
             */
            if (pageLines.size() == size) {
                hasNextPage = readBoundedLine(reader) != null;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("arquivoId", file.getId());
        result.put("nome", file.getName());
        result.put("pagina", page);
        result.put("tamanhoPagina", size);
        result.put("linhasRetornadas", pageLines.size());
        result.put("temPaginaAnterior", page > 0);
        result.put("temProximaPagina", hasNextPage);

        /*
         * Os totais não são calculados em cada solicitação,
         * evitando percorrer o arquivo até EOF.
         */
        result.put("totalLinhas", null);
        result.put("totalPaginas", null);

        result.put("linhas", pageLines);

        return result;
    }

    /**
     * Inspeciona a estrutura do arquivo.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStructure(Long fileId)
            throws IOException {

        File file = findFile(fileId);
        String location = requireLocation(file);

        String format = normalizeFormat(
                file.getFormatFile(),
                file.getName());

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("arquivoId", file.getId());
        result.put("nome", file.getName());
        result.put("formato", format);
        result.put("tamanhoBytes", file.getSize());

        /*
         * CSV e TSV: utiliza o mesmo TableReader do validador.
         */
        if (format.equals("csv") || format.equals("tsv")) {

            try (InputStream input =
                         bucketService.openObjectStream(location);
                 CsvRecordValidator.TableReader reader =
                         new CsvRecordValidator.TableReader(input)) {

                List<String> columns = reader.readRecord();

                boolean hasHeader = columns != null
                        && !(columns.size() == 1
                        && columns.get(0).isBlank());

                if (hasHeader) {
                    columns.replaceAll(String::trim);
                } else {
                    columns = new ArrayList<>();
                }

                long totalRecords = 0;
                List<String> record;

                while ((record = reader.readRecord()) != null) {
                    if (record.size() == 1
                            && record.get(0).isBlank()) {
                        continue;
                    }

                    totalRecords++;
                }

                char delimiter = reader.getDelimiter();

                result.put("tipoEstrutura", "tabela");

                result.put(
                        "delimitador",
                        delimiter == '\t'
                                ? "TAB"
                                : delimiter == ';'
                                ? "ponto e vírgula"
                                : "vírgula");

                result.put("quantidadeColunas", columns.size());
                result.put("colunas", columns);

                result.put(
                        "quantidadeLinhasComCabecalho",
                        totalRecords + (hasHeader ? 1L : 0L));

                result.put(
                        "quantidadeRegistrosEstimados",
                        totalRecords);

                return result;

            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Não foi possível interpretar a estrutura CSV/TSV do arquivo: "
                                + e.getMessage(),
                        e);
            }
        }

        /*
         * JSON e GeoJSON.
         */
        if (format.equals("json") || format.equals("geojson")) {

            byte[] bytes;

            try (InputStream input =
                         bucketService.openObjectStream(location)) {

                bytes = input.readNBytes(
                        MAX_JSON_INSPECTION_BYTES + 1);
            }

            if (bytes.length > MAX_JSON_INSPECTION_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.CONTENT_TOO_LARGE,
                        "A inspeção estrutural de JSON está limitada a 5 MB.");
            }

            try {
                JsonNode root = objectMapper.readTree(bytes);

                result.put("tipoEstrutura", "json");

                result.put(
                        "tipoRaiz",
                        root == null
                                ? "vazio"
                                : root.getNodeType().name());

                if (root != null && root.isObject()) {

                    List<String> fields = new ArrayList<>();

                    root.fieldNames().forEachRemaining(fields::add);

                    result.put("camposRaiz", fields);

                    JsonNode type = root.get("type");

                    if (type != null && type.isTextual()) {
                        result.put("tipoGeoJson", type.asText());
                    }

                    JsonNode features = root.get("features");

                    if (features != null && features.isArray()) {
                        result.put(
                                "quantidadeFeatures",
                                features.size());
                    }

                } else if (root != null && root.isArray()) {

                    result.put(
                            "quantidadeElementos",
                            root.size());
                }

                return result;

            } catch (ResponseStatusException e) {
                throw e;

            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Não foi possível interpretar a estrutura JSON do arquivo.",
                        e);
            }
        }

        /*
         * Outros formatos: identifica a assinatura binária.
         */
        byte[] header = bucketService.readObjectPrefix(
                location,
                HEADER_BYTES_TO_SHOW);

        result.put(
                "tipoEstrutura",
                "binaria-ou-formato-especializado");

        result.put(
                "assinatura",
                identifyBinaryFormat(header, format));

        result.put(
                "cabecalhoHexadecimal",
                HexFormat.of().formatHex(header));

        result.put(
                "observacao",
                "A assinatura e o cabeçalho são informativos; "
                        + "a extração completa depende de um leitor "
                        + "específico para o formato.");

        return result;
    }

    /**
     * Lê uma linha com limite máximo de caracteres.
     */
    private String readBoundedLine(BufferedReader reader)
            throws IOException {

        StringBuilder line = new StringBuilder();
        int value;
        boolean readAny = false;

        while ((value = reader.read()) != -1) {
            readAny = true;

            if (value == '\n') {
                break;
            }

            if (value != '\r') {
                if (line.length() >= MAX_LINE_CHARS) {
                    throw new ResponseStatusException(
                            HttpStatus.CONTENT_TOO_LARGE,
                            "Uma linha do arquivo excede o limite de inspeção de 1.000.000 caracteres.");
                }

                line.append((char) value);
            }
        }

        return !readAny && line.isEmpty()
                ? null
                : line.toString();
    }

    /**
     * Busca o arquivo pelo identificador.
     */
    private File findFile(Long fileId) {

        if (fileId == null || fileId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O identificador do arquivo deve ser positivo.");
        }

        return fileService.findEntityById(fileId);
    }

    /**
     * Valida a localização do arquivo no Object Storage.
     */
    private String requireLocation(File file) {

        if (file.getLocation() == null
                || file.getLocation().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "O arquivo não possui localização registrada no Object Storage.");
        }

        return file.getLocation();
    }

    /**
     * Verifica se o arquivo pode ser tratado como texto.
     */
    private boolean isTextFile(File file, byte[] sample) {

        String format = normalizeFormat(
                file.getFormatFile(),
                file.getName());

        if (List.of(
                "csv",
                "tsv",
                "txt",
                "json",
                "geojson",
                "xml",
                "html",
                "md",
                "log",
                "yaml",
                "yml")
                .contains(format)) {

            return true;
        }

        for (byte item : sample) {
            int value = item & 0xff;

            if (value == 0) {
                return false;
            }

            if (value < 0x20
                    && value != '\n'
                    && value != '\r'
                    && value != '\t'
                    && value != '\f') {

                return false;
            }
        }

        return true;
    }

    /**
     * Normaliza o formato do arquivo.
     */
    private String normalizeFormat(
            String format,
            String filename) {

        String value = format;

        if (value == null || value.isBlank()) {
            value = filename != null && filename.contains(".")
                    ? filename.substring(
                            filename.lastIndexOf('.') + 1)
                    : "";
        }

        return value.toLowerCase(Locale.ROOT)
                .replaceFirst("^\\.", "")
                .trim();
    }

    /**
     * Identifica formatos binários por suas assinaturas.
     */
    private String identifyBinaryFormat(
            byte[] bytes,
            String extension) {

        if (bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K') {

            return "ZIP/OOXML (assinatura PK)";
        }

        if (bytes.length >= 4
                && (bytes[0] & 0xff) == 0x25
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F') {

            return "PDF";
        }

        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xFF
                && (bytes[1] & 0xff) == 0xD8
                && (bytes[2] & 0xff) == 0xFF) {

            return "JPEG";
        }

        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {

            return "PNG";
        }

        if (bytes.length >= 6
                && new String(
                        bytes,
                        0,
                        6,
                        StandardCharsets.US_ASCII)
                        .startsWith("GIF8")) {

            return "GIF";
        }

        if (bytes.length >= 4
                && bytes[0] == 'O'
                && bytes[1] == 'b'
                && bytes[2] == 'j'
                && bytes[3] == '1') {

            return "Parquet";
        }

        if (bytes.length >= 4
                && bytes[0] == 'S'
                && bytes[1] == 'Q'
                && bytes[2] == 'L'
                && bytes[3] == 'i') {

            return "SQLite";
        }

        return extension.isBlank()
                ? "desconhecido"
                : "formato não reconhecido pela assinatura (extensão ."
                        + extension + ")";
    }
}