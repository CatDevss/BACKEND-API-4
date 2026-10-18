package catdevs.georuraldatahub.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
     * Envia o arquivo para a zona bruta do bucket, no formato
     * "bruta/{datasetId}/{hash}_{nomeOriginal}", e retorna o nome do objeto salvo.
     */
    public String uploadToRawZone(MultipartFile file, Long datasetId, String hash, String sanitizedFilename)
            throws IOException {
        String objectName = "bruta/" + datasetId + "/" + hash + "_" + sanitizedFilename;

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
            throw new IllegalArgumentException(
                    "Nome de objeto inválido.");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        try (InputStream inputStream = client.getObject(request).getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                inputStream,
                                StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new IllegalArgumentException("O CSV está vazio.");
            }

            // Remove eventual BOM UTF-8.
            headerLine = headerLine.replace("\uFEFF", "");

            String delimiter = headerLine.contains(";") ? ";" : ",";

            String[] headers = headerLine.split(
                    java.util.regex.Pattern.quote(delimiter), -1);

            Map<String, Integer> columns = new LinkedHashMap<>();

            for (int i = 0; i < headers.length; i++) {
                columns.put(
                        headers[i].trim()
                                .replace("\"", "")
                                .toLowerCase(Locale.ROOT),
                        i);
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

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue;
                }

                String[] values = line.split(
                        java.util.regex.Pattern.quote(delimiter), -1);

                if (values.length != headers.length) {
                    throw new IllegalArgumentException(
                            "Quantidade de colunas inválida na linha "
                                    + lineNumber + ".");
                }

                try {
                    double latitude = Double.parseDouble(
                            values[latitudeIndex].trim()
                                    .replace("\"", "")
                                    .replace(",", "."));

                    double longitude = Double.parseDouble(
                            values[longitudeIndex].trim()
                                    .replace("\"", "")
                                    .replace(",", "."));

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

                    for (int i = 0; i < headers.length; i++) {
                        properties.put(
                                headers[i].trim().replace("\"", ""),
                                values[i].trim().replace("\"", ""));
                    }

                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Coordenadas inválidas na linha "
                                    + lineNumber + ".",
                            e);
                }
            }

            return featureCollection;
        }
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