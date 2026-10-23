package catdevs.georuraldatahub.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;

import catdevs.georuraldatahub.config.ObjectStorageProperties;

@Service
public class BucketService {

    private final ObjectStorageClient client;
    private final ObjectStorageProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BucketService(ObjectStorageClient client, ObjectStorageProperties props) {
        this.client = client;
        this.props = props;
    }

    /**
     * Retorna os nomes dos objetos armazenados no bucket.
     */
    public List<String> listObjectsBucket() {
        var request = ListObjectsRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .build();

        return client.listObjects(request)
                .getListObjects()
                .getObjects()
                .stream()
                .map(ObjectSummary::getName)
                .toList();
    }

    /**
     * Envia um arquivo para a zona bruta de um dataset.
     */
    public String uploadToRawZone(
            MultipartFile file,
            Long datasetId,
            String hash,
            String sanitizedFilename) throws IOException {

        if (datasetId == null || datasetId <= 0) {
            throw new IllegalArgumentException("Identificador do dataset inválido.");
        }

        return uploadToRawScope(file, datasetId.toString(), hash, sanitizedFilename);
    }

    /**
     * Envia um arquivo para a zona bruta de uma fonte.
     */
    public String uploadToSourceRawZone(
            MultipartFile file,
            Long sourceId,
            String hash,
            String filename) throws IOException {

        if (sourceId == null || sourceId <= 0) {
            throw new IllegalArgumentException("Identificador da fonte inválido.");
        }

        return uploadToRawScope(file, "fontes/" + sourceId, hash, filename);
    }

    private String uploadToRawScope(
            MultipartFile file,
            String scope,
            String hash,
            String sanitizedFilename) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }

        validateObjectPart(hash, "Hash");
        validateFilename(sanitizedFilename);

        String objectName = "bruta/" + scope + "/" + hash + "_" + sanitizedFilename;

        try (InputStream input = file.getInputStream()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(props.bucketName())
                    .objectName(objectName)
                    .putObjectBody(input)
                    .contentLength(file.getSize())
                    .build();

            client.putObject(request);
        }

        return objectName;
    }

    /**
     * Envia conteúdo validado de um dataset.
     */
    public String uploadValidatedContent(
            byte[] content,
            Long datasetId,
            String hash,
            String filename) {

        if (datasetId == null || datasetId <= 0) {
            throw new IllegalArgumentException("Identificador do dataset inválido.");
        }

        return uploadValidatedScope(content, datasetId.toString(), hash, filename);
    }

    /**
     * Envia conteúdo validado de uma fonte.
     */
    public String uploadSourceValidatedContent(
            byte[] content,
            Long sourceId,
            String hash,
            String filename) {

        if (sourceId == null || sourceId <= 0) {
            throw new IllegalArgumentException("Identificador da fonte inválido.");
        }

        return uploadValidatedScope(content, "fontes/" + sourceId, hash, filename);
    }

    private String uploadValidatedScope(
            byte[] content,
            String scope,
            String hash,
            String filename) {

        if (content == null) {
            throw new IllegalArgumentException("O conteúdo do arquivo é obrigatório.");
        }

        validateObjectPart(hash, "Hash");
        validateFilename(filename);

        String key = "validada/" + scope + "/" + hash + "_" + filename;

        try (InputStream input = new java.io.ByteArrayInputStream(content)) {
            client.putObject(PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(props.bucketName())
                    .objectName(key)
                    .putObjectBody(input)
                    .contentLength((long) content.length)
                    .build());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        return key;
    }

    /**
     * Armazena o relatório de validação associado a um objeto bruto.
     */
    public String uploadValidationReport(String rawLocation, String json) {
        validateObjectName(rawLocation);

        if (json == null) {
            throw new IllegalArgumentException("O relatório de validação é obrigatório.");
        }

        String key = rawLocation + ".validation.json";
        byte[] content = json.getBytes(StandardCharsets.UTF_8);

        try (InputStream input = new java.io.ByteArrayInputStream(content)) {
            client.putObject(PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(props.bucketName())
                    .objectName(key)
                    .putObjectBody(input)
                    .contentLength((long) content.length)
                    .contentType("application/json")
                    .build());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        return key;
    }

    /**
     * Lê um relatório de validação.
     */
    public String readValidationReport(String rawLocation) throws IOException {
        validateObjectName(rawLocation);

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(rawLocation + ".validation.json")
                .build();

        try (InputStream input = client.getObject(request).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arquivo ainda não possui relatório de validação.");
            }
            throw e;
        }
    }

    /**
     * Armazena um artefato produzido durante a coleta.
     */
    public String uploadCollectionArtifact(String key, byte[] bytes, String contentType) {
        validateObjectName(key);

        if (bytes == null) {
            throw new IllegalArgumentException("O conteúdo do artefato é obrigatório.");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("O tipo de conteúdo é obrigatório.");
        }

        try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(props.bucketName())
                    .objectName(key)
                    .putObjectBody(input)
                    .contentLength((long) bytes.length)
                    .contentType(contentType)
                    .build());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        return key;
    }

    /**
     * Lê os metadados de coleta associados a um objeto bruto.
     */
    public String readCollectionMetadata(String rawLocation) throws IOException {
        validateObjectName(rawLocation);

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(rawLocation + ".collection.json")
                .build();

        try (InputStream input = client.getObject(request).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arquivo não possui metadados de coleta automática.");
            }
            throw e;
        }
    }

    /**
     * Exclui um objeto do bucket.
     */
    public void deleteObject(String objectName) {
        validateObjectName(objectName);

        var request = DeleteObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        client.deleteObject(request);
    }

    /**
     * Lê e valida um GeoJSON FeatureCollection armazenado no bucket.
     */
    public JsonNode readGeoJson(String objectName) throws IOException {
        validateObjectName(objectName);

        String normalizedName = objectName.toLowerCase(Locale.ROOT);

        if (!normalizedName.endsWith(".geojson")
                && !normalizedName.endsWith(".json")) {
            throw new IllegalArgumentException(
                    "O arquivo precisa possuir extensão .geojson ou .json.");
        }

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        try (InputStream input = client.getObject(request).getInputStream()) {
            JsonNode root = objectMapper.readTree(input);

            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(
                        "O conteúdo do arquivo não é um objeto JSON válido.");
            }

            JsonNode type = root.get("type");

            if (type == null || !"FeatureCollection".equals(type.asText())) {
                throw new IllegalArgumentException(
                        "O arquivo precisa ser um GeoJSON FeatureCollection.");
            }

            JsonNode features = root.get("features");

            if (features == null || !features.isArray()) {
                throw new IllegalArgumentException(
                        "O GeoJSON precisa possuir um array chamado features.");
            }

            return root;
        }
    }

    /**
     * Lê um CSV com latitude e longitude e converte seus registros
     * em um GeoJSON FeatureCollection.
     */
    public JsonNode readCsvAsGeoJson(String objectName) throws IOException {
        validateObjectName(objectName);

        if (!objectName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "O arquivo precisa possuir extensão .csv.");
        }

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        try (InputStream input = client.getObject(request).getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {

            reader.mark(1);
            if (reader.read() != '\uFEFF') {
                reader.reset();
            }

            reader.mark(64 * 1024);
            String firstLine = reader.readLine();
            reader.reset();

            if (firstLine == null) {
                throw new IllegalArgumentException("O CSV está vazio.");
            }

            char delimiter = firstLine.contains(";") ? ';' : ',';
            List<String> headers = readCsvRecord(reader, delimiter);

            if (headers == null || headers.isEmpty()) {
                throw new IllegalArgumentException("O CSV não possui cabeçalho.");
            }

            Map<String, Integer> columns = new LinkedHashMap<>();

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i).trim().toLowerCase(Locale.ROOT);

                if (header.isEmpty()) {
                    throw new IllegalArgumentException(
                            "O CSV possui uma coluna sem nome.");
                }

                if (columns.putIfAbsent(header, i) != null) {
                    throw new IllegalArgumentException(
                            "O CSV possui nomes de coluna duplicados: " + header);
                }
            }

            Integer latitudeIndex = findColumn(
                    columns, "latitude", "lat", "nu_latitude");

            Integer longitudeIndex = findColumn(
                    columns, "longitude", "lon", "lng", "long", "nu_longitude");

            if (latitudeIndex == null || longitudeIndex == null) {
                throw new IllegalArgumentException(
                        "O CSV precisa possuir colunas de latitude e longitude.");
            }

            ObjectNode featureCollection = objectMapper.createObjectNode();
            featureCollection.put("type", "FeatureCollection");

            ArrayNode features = featureCollection.putArray("features");

            List<String> values;
            int recordNumber = 1;

            while ((values = readCsvRecord(reader, delimiter)) != null) {
                recordNumber++;

                if (values.size() == 1 && values.get(0).isBlank()) {
                    continue;
                }

                if (values.size() != headers.size()) {
                    throw new IllegalArgumentException(
                            "Quantidade de colunas inválida no registro "
                                    + recordNumber + ".");
                }

                double latitude;
                double longitude;

                try {
                    latitude = Double.parseDouble(
                            values.get(latitudeIndex).trim().replace(",", "."));

                    longitude = Double.parseDouble(
                            values.get(longitudeIndex).trim().replace(",", "."));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Coordenadas inválidas no registro "
                                    + recordNumber + ".", e);
                }

                if (!Double.isFinite(latitude)
                        || !Double.isFinite(longitude)
                        || latitude < -90 || latitude > 90
                        || longitude < -180 || longitude > 180) {
                    throw new IllegalArgumentException(
                            "Coordenadas fora dos limites no registro "
                                    + recordNumber + ".");
                }

                ObjectNode feature = features.addObject();
                feature.put("type", "Feature");

                ObjectNode geometry = feature.putObject("geometry");
                geometry.put("type", "Point");

                ArrayNode coordinates = geometry.putArray("coordinates");
                coordinates.add(longitude);
                coordinates.add(latitude);

                ObjectNode properties = feature.putObject("properties");

                for (int i = 0; i < headers.size(); i++) {
                    properties.put(headers.get(i).trim(), values.get(i).trim());
                }
            }

            return featureCollection;
        }
    }

    /**
     * Lê um registro CSV. Suporta delimitadores e quebras de linha dentro
     * de campos entre aspas, além de aspas escapadas ("").
     */
    private List<String> readCsvRecord(
            BufferedReader reader,
            char delimiter) throws IOException {

        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean inQuotes = false;
        boolean readAnything = false;

        int c;

        while ((c = reader.read()) != -1) {
            readAnything = true;
            char ch = (char) c;

            if (inQuotes) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();

                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;

                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"' && field.length() == 0) {
                inQuotes = true;
            } else if (ch == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                fields.add(field.toString());
                return fields;
            } else if (ch != '\r') {
                field.append(ch);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException(
                    "O CSV terminou com um campo entre aspas sem fechamento.");
        }

        if (!readAnything) {
            return null;
        }

        fields.add(field.toString());
        return fields;
    }

    private Integer findColumn(
            Map<String, Integer> columns,
            String... candidates) {

        for (String candidate : candidates) {
            Integer index = columns.get(candidate);

            if (index != null) {
                return index;
            }
        }

        return null;
    }

    private void validateObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()
                || objectName.startsWith("/")
                || objectName.contains("..")
                || objectName.contains("\\")
                || objectName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Nome de objeto inválido.");
        }
    }

    private void validateObjectPart(String value, String field) {
        if (value == null || value.isBlank()
                || value.contains("/")
                || value.contains("\\")
                || value.contains("..")) {
            throw new IllegalArgumentException(field + " inválido.");
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains("..")) {
            throw new IllegalArgumentException("Nome de arquivo inválido.");
        }
    }
}