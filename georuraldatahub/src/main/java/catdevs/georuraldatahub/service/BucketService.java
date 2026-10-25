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

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;

import catdevs.georuraldatahub.config.ObjectStorageProperties;

/**
 * Responsável por conversar com o bucket: listar, enviar e baixar arquivos.
 */
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
     * Retorna os nomes de todos os arquivos que estão no bucket.
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
     * Envia o arquivo para a zona bruta do bucket.
     */
    public String uploadToRawZone(
            MultipartFile file,
            Long datasetId,
            String hash,
            String sanitizedFilename) throws IOException {

        return uploadToRawScope(
                file, datasetId.toString(), hash, sanitizedFilename);
    }

    public String uploadToSourceRawZone(
            MultipartFile file,
            Long sourceId,
            String hash,
            String filename) throws IOException {

        return uploadToRawScope(
                file, "fontes/" + sourceId, hash, filename);
    }

    private String uploadToRawScope(
            MultipartFile file,
            String scope,
            String hash,
            String sanitizedFilename) throws IOException {

        String objectName = "bruta/" + scope + "/" + hash + "_" + sanitizedFilename;

        PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .putObjectBody(file.getInputStream())
                .contentLength(file.getSize())
                .build();

        client.putObject(request);
        return objectName;
    }

    public String uploadValidatedContent(
            byte[] content,
            Long datasetId,
            String hash,
            String filename) {

        return uploadValidatedScope(
                content, datasetId.toString(), hash, filename);
    }

    public String uploadSourceValidatedContent(
            byte[] content,
            Long sourceId,
            String hash,
            String filename) {

        return uploadValidatedScope(
                content, "fontes/" + sourceId, hash, filename);
    }

    private String uploadValidatedScope(
            byte[] content,
            String scope,
            String hash,
            String filename) {

        String key = "validada/" + scope + "/" + hash + "_" + filename;

        try (var input = new java.io.ByteArrayInputStream(content)) {
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

    public String uploadValidationReport(String rawLocation, String json) {
        String key = rawLocation + ".validation.json";
        byte[] content = json.getBytes(StandardCharsets.UTF_8);

        try (var input = new java.io.ByteArrayInputStream(content)) {
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

    public String readValidationReport(String rawLocation) throws IOException {
        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(rawLocation + ".validation.json")
                .build();

        try (InputStream input = client.getObject(request).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (com.oracle.bmc.model.BmcException e) {
            if (e.getStatusCode() == 404) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Arquivo ainda não possui relatório de validação.");
            }
            throw e;
        }
    }

    /**
     * Lê o conteúdo de um objeto armazenado no OCI Object Storage.
     *
     * @param objectName nome completo do objeto no bucket.
     * @return conteúdo do arquivo em bytes.
     * @throws IOException se ocorrer um erro ao ler o conteúdo.
     */
    public byte[] readObjectContent(String objectName) throws IOException {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("O nome do objeto é obrigatório.");
        }

        if (objectName.contains("..")
                || objectName.startsWith("/")
                || objectName.contains("\\")) {
            throw new IllegalArgumentException("Nome de objeto inválido.");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        try (InputStream inputStream = client.getObject(request).getInputStream()) {
            return inputStream.readAllBytes();
        } catch (com.oracle.bmc.model.BmcException e) {
            if (e.getStatusCode() == 404) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado no Object Storage.");
            }
            throw e;
        }
    }

    public String uploadCollectionArtifact(
            String key,
            byte[] bytes,
            String contentType) {

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

    public String readCollectionMetadata(String rawLocation) throws IOException {
        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(rawLocation + ".collection.json")
                .build();

        try (InputStream input = client.getObject(request).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (com.oracle.bmc.model.BmcException e) {
            if (e.getStatusCode() == 404) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Arquivo não possui metadados de coleta automática.");
            }
            throw e;
        }
    }

    public void deleteObject(String objectName) {
        var request = DeleteObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        client.deleteObject(request);
    }

    /**
     * Lê um arquivo GeoJSON armazenado no Object Storage.
     *
     * @param objectName nome completo do objeto no bucket.
     * @return conteúdo JSON do arquivo.
     */
    public JsonNode readGeoJson(String objectName) throws IOException {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("O nome do objeto é obrigatório.");
        }

        if (objectName.contains("..")
                || objectName.startsWith("/")
                || objectName.contains("\\")) {
            throw new IllegalArgumentException("Nome de objeto inválido.");
        }

        String normalizedName = objectName.toLowerCase(Locale.ROOT);

        if (!normalizedName.endsWith(".geojson")
                && !normalizedName.endsWith(".json")) {
            throw new IllegalArgumentException(
                    "O arquivo precisa possuir extensão .geojson ou .json.");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        try (InputStream inputStream = client.getObject(request).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);

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
     * em um GeoJSON FeatureCollection para visualização no mapa.
     */
    public JsonNode readCsvAsGeoJson(String objectName) throws IOException {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do objeto é obrigatório.");
        }

        String normalizedName = objectName.toLowerCase(Locale.ROOT);

        if (!normalizedName.endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "O arquivo precisa possuir extensão .csv.");
        }

        if (objectName.contains("..")
                || objectName.startsWith("/")
                || objectName.contains("\\")) {
            throw new IllegalArgumentException("Nome de objeto inválido.");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        try (InputStream inputStream = client.getObject(request).getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            // Remove eventual BOM UTF-8 antes de ler o cabeçalho.
            reader.mark(1);
            if (reader.read() != '\uFEFF') {
                reader.reset();
            }

            // Detecta o delimitador pela primeira linha, sem consumi-la.
            reader.mark(64 * 1024);
            String firstLine = reader.readLine();
            reader.reset();

            if (firstLine == null) {
                throw new IllegalArgumentException("O CSV está vazio.");
            }

            char delimiter = firstLine.contains(";") ? ';' : ',';
            List<String> headers = readCsvRecord(reader, delimiter);

            Map<String, Integer> columns = new LinkedHashMap<>();

            for (int i = 0; i < headers.size(); i++) {
                columns.put(
                        headers.get(i).trim().toLowerCase(Locale.ROOT), i);
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

                try {
                    double latitude = Double.parseDouble(
                            values.get(latitudeIndex).trim().replace(",", "."));

                    double longitude = Double.parseDouble(
                            values.get(longitudeIndex).trim().replace(",", "."));

                    if (!Double.isFinite(latitude)
                            || !Double.isFinite(longitude)
                            || latitude < -90 || latitude > 90
                            || longitude < -180 || longitude > 180) {
                        throw new IllegalArgumentException(
                                "Coordenadas fora dos limites.");
                    }

                    ObjectNode feature = features.addObject();
                    feature.put("type", "Feature");

                    ObjectNode geometry = feature.putObject("geometry");
                    geometry.put("type", "Point");

                    ArrayNode coordinates = geometry.putArray("coordinates");

                    // GeoJSON exige longitude antes de latitude.
                    coordinates.add(longitude);
                    coordinates.add(latitude);

                    ObjectNode properties = feature.putObject("properties");

                    for (int i = 0; i < headers.size(); i++) {
                        properties.put(
                                headers.get(i).trim(), values.get(i).trim());
                    }

                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Coordenadas inválidas no registro "
                                    + recordNumber + ".",
                            e);
                }
            }

            return featureCollection;
        }
    }

    /**
     * Lê um registro CSV (RFC 4180).
     * Campos entre aspas podem conter delimitadores, aspas escapadas e quebras de linha.
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
}