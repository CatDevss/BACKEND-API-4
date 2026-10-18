package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

}