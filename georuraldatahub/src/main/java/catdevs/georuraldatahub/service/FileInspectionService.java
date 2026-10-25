package catdevs.georuraldatahub.service;

import java.io.IOException;
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

/**
 * Fornece informações de inspeção dos arquivos armazenados no sistema.
 * Não altera o arquivo original nem cria versões novas.
 */
@Service
public class FileInspectionService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int HEADER_BYTES_TO_SHOW = 32;

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

    /** Retorna metadados registrados no banco e informações básicas do objeto. */
    @Transactional(readOnly = true)
    public Map<String, Object> getMetadata(Long fileId) throws IOException {
        File file = findFile(fileId);
        byte[] content = readOriginal(file);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", file.getId());
        result.put("nome", file.getName());
        result.put("formato", file.getFormatFile());
        result.put("tamanhoRegistradoBytes", file.getSize());
        result.put("tamanhoLidoBytes", content.length);
        result.put("hash", file.getHash());
        result.put("localizacao", file.getLocation());
        result.put("dataRecebimento", file.getReceivedDate());
        result.put("texto", isTextFile(file, content));
        result.put("dataInspecao", LocalDateTime.now());
        return result;
    }

    /**
     * Retorna uma página de linhas para arquivos textuais.
     * page é baseada em zero. O tamanho máximo permitido é 500 linhas.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTextContent(Long fileId, int page, int size) throws IOException {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O parâmetro page não pode ser negativo.");
        }
        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        File file = findFile(fileId);
        byte[] bytes = readOriginal(file);
        if (!isTextFile(file, bytes)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "A visualização paginada está disponível apenas para arquivos textuais.");
        }

        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\\R", -1);
        int start = Math.min(page * size, lines.length);
        int end = Math.min(start + size, lines.length);
        List<String> pageLines = new ArrayList<>();
        for (int i = start; i < end; i++) {
            pageLines.add(lines[i]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("arquivoId", file.getId());
        result.put("nome", file.getName());
        result.put("pagina", page);
        result.put("tamanhoPagina", size);
        result.put("totalLinhas", lines.length);
        result.put("totalPaginas", Math.max(1, (int) Math.ceil((double) lines.length / size)));
        result.put("temPaginaAnterior", page > 0);
        result.put("temProximaPagina", end < lines.length);
        result.put("linhas", pageLines);
        return result;
    }

    /**
     * Retorna uma descrição da estrutura identificável do arquivo.
     * CSV: cabeçalho e quantidade de linhas. JSON/GeoJSON: tipo e resumo dos campos.
     * Outros formatos binários: tamanho, assinatura provável e primeiros bytes em hexadecimal.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStructure(Long fileId) throws IOException {
        File file = findFile(fileId);
        byte[] bytes = readOriginal(file);
        String format = normalizeFormat(file.getFormatFile(), file.getName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("arquivoId", file.getId());
        result.put("nome", file.getName());
        result.put("formato", format);
        result.put("tamanhoBytes", bytes.length);

        if (format.equals("csv") || format.equals("tsv")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = text.split("\\R", -1);
            String firstLine = lines.length == 0 ? "" : lines[0];
            String delimiter = format.equals("tsv") || (!firstLine.contains(",") && firstLine.contains("\t"))
                    ? "\t" : ",";
            String[] columns = firstLine.isBlank() ? new String[0] : firstLine.split(java.util.regex.Pattern.quote(delimiter), -1);
            result.put("tipoEstrutura", "tabela");
            result.put("delimitador", delimiter.equals("\t") ? "TAB" : "vírgula");
            result.put("quantidadeColunas", columns.length);
            result.put("colunas", List.of(columns));
            result.put("quantidadeLinhasComCabecalho", lines.length);
            result.put("quantidadeRegistrosEstimados", Math.max(0, lines.length - (firstLine.isBlank() ? 0 : 1)));
            return result;
        }

        if (format.equals("json") || format.equals("geojson")) {
            try {
                JsonNode root = objectMapper.readTree(bytes);
                result.put("tipoEstrutura", "json");
                result.put("tipoRaiz", root == null ? "vazio" : root.getNodeType().name());
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
                        result.put("quantidadeFeatures", features.size());
                    }
                } else if (root != null && root.isArray()) {
                    result.put("quantidadeElementos", root.size());
                }
                return result;
            } catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Não foi possível interpretar a estrutura JSON do arquivo.");
            }
        }

        result.put("tipoEstrutura", "binaria-ou-formato-especializado");
        result.put("assinatura", identifyBinaryFormat(bytes, format));
        int headerLength = Math.min(bytes.length, HEADER_BYTES_TO_SHOW);
        result.put("cabecalhoHexadecimal", HexFormat.of().formatHex(bytes, 0, headerLength));
        result.put("observacao", "A assinatura e o cabeçalho são informativos; a extração completa depende de um leitor específico para o formato.");
        return result;
    }

    private File findFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O identificador do arquivo deve ser positivo.");
        }
        return fileService.findEntityById(fileId);
    }

    private byte[] readOriginal(File file) throws IOException {
        if (file.getLocation() == null || file.getLocation().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "O arquivo não possui localização registrada no Object Storage.");
        }
        return bucketService.readObjectContent(file.getLocation());
    }

    private boolean isTextFile(File file, byte[] bytes) {
        String format = normalizeFormat(file.getFormatFile(), file.getName());
        if (List.of("csv", "tsv", "txt", "json", "geojson", "xml", "html", "md", "log", "yaml", "yml").contains(format)) {
            return true;
        }
        int sampleLength = Math.min(bytes.length, 4096);
        for (int i = 0; i < sampleLength; i++) {
            int value = bytes[i] & 0xff;
            if (value == 0) {
                return false;
            }
            if (value < 0x20 && value != '\n' && value != '\r' && value != '\t' && value != '\f') {
                return false;
            }
        }
        return true;
    }

    private String normalizeFormat(String format, String filename) {
        String value = format;
        if (value == null || value.isBlank()) {
            value = filename != null && filename.contains(".")
                    ? filename.substring(filename.lastIndexOf('.') + 1)
                    : "";
        }
        return value.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "").trim();
    }

    private String identifyBinaryFormat(byte[] bytes, String extension) {
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') return "ZIP/OOXML (assinatura PK)";
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0x25 && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') return "PDF";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xFF && (bytes[1] & 0xff) == 0xD8 && (bytes[2] & 0xff) == 0xFF) return "JPEG";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "PNG";
        if (bytes.length >= 6 && new String(bytes, 0, 6, StandardCharsets.US_ASCII).startsWith("GIF8")) return "GIF";
        if (bytes.length >= 4 && bytes[0] == 'O' && bytes[1] == 'b' && bytes[2] == 'j' && bytes[3] == '1') return "Parquet";
        if (bytes.length >= 4 && bytes[0] == 'S' && bytes[1] == 'Q' && bytes[2] == 'L' && bytes[3] == 'i') return "SQLite";
        return extension.isBlank() ? "desconhecido" : "formato não reconhecido pela assinatura (extensão ." + extension + ")";
    }
}