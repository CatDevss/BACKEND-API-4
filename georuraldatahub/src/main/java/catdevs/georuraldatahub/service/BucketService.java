package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

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
                return uploadToRawScope(file, datasetId.toString(), hash, sanitizedFilename);
        }

        public String uploadToSourceRawZone(MultipartFile file, Long sourceId, String hash, String filename)
                        throws IOException {
                return uploadToRawScope(file, "fontes/" + sourceId, hash, filename);
        }

        private String uploadToRawScope(MultipartFile file, String scope, String hash, String sanitizedFilename)
                        throws IOException {
                String objectName = "bruta/" + scope + "/" + hash + "_" + sanitizedFilename;

                try (InputStream body = file.getInputStream()) {
                        PutObjectRequest request = PutObjectRequest.builder()
                                .namespaceName(props.namespace())
                                .bucketName(props.bucketName())
                                .objectName(objectName)
                                .putObjectBody(body)
                                .contentLength(file.getSize())
                                .build();
                        client.putObject(request);
                }
                return objectName;
        }

        public String uploadValidatedContent(byte[] content, Long datasetId, String hash, String filename) {
                return uploadValidatedScope(content, datasetId.toString(), hash, filename);
        }

        public String uploadSourceValidatedContent(byte[] content, Long sourceId, String hash, String filename) {
                return uploadValidatedScope(content, "fontes/" + sourceId, hash, filename);
        }

        private String uploadValidatedScope(byte[] content, String scope, String hash, String filename) {
                String key = "validada/" + scope + "/" + hash + "_" + filename;
                try (var input = new java.io.ByteArrayInputStream(content)) {
                        client.putObject(PutObjectRequest.builder()
                                .namespaceName(props.namespace()).bucketName(props.bucketName())
                                .objectName(key).putObjectBody(input).contentLength((long) content.length).build());
                } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                return key;
        }

        public String uploadValidationReport(String rawLocation, String json) {
                String key = rawLocation + ".validation.json";
                byte[] content = json.getBytes(StandardCharsets.UTF_8);
                try (var input = new java.io.ByteArrayInputStream(content)) {
                        client.putObject(PutObjectRequest.builder()
                                .namespaceName(props.namespace()).bucketName(props.bucketName())
                                .objectName(key).putObjectBody(input).contentLength((long) content.length)
                                .contentType("application/json").build());
                } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                return key;
        }

        public String readValidationReport(String rawLocation) throws IOException {
                var request = GetObjectRequest.builder().namespaceName(props.namespace())
                        .bucketName(props.bucketName()).objectName(rawLocation + ".validation.json").build();
                try (InputStream input = client.getObject(request).getInputStream()) {
                        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                } catch (com.oracle.bmc.model.BmcException e) {
                        if (e.getStatusCode() == 404) throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND,
                                "Arquivo ainda não possui relatório de validação.");
                        throw e;
                }
        }

        public String uploadCollectionArtifact(String key, byte[] bytes, String contentType) {
                try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
                        client.putObject(PutObjectRequest.builder().namespaceName(props.namespace())
                                .bucketName(props.bucketName()).objectName(key).putObjectBody(input)
                                .contentLength((long) bytes.length).contentType(contentType).build());
                } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                return key;
        }

        public String readCollectionMetadata(String rawLocation) throws IOException {
                var request = GetObjectRequest.builder().namespaceName(props.namespace()).bucketName(props.bucketName())
                        .objectName(rawLocation + ".collection.json").build();
                try (InputStream input = client.getObject(request).getInputStream()) {
                        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                } catch (com.oracle.bmc.model.BmcException e) {
                        if (e.getStatusCode() == 404) throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "Arquivo não possui metadados de coleta automática.");
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

                        return catdevs.georuraldatahub.validation.GeoJsonContent.featureCollection(root);
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

                try (InputStream input = client.getObject(request).getInputStream();
                                var reader = new catdevs.georuraldatahub.validation.CsvRecordValidator.TableReader(input)) {
                        List<String> headers = reader.readRecord();
                        if (headers == null) throw new IllegalArgumentException("O CSV está vazio.");
                        headers = headers.stream().map(String::trim).toList();
                        if (headers.stream().anyMatch(String::isBlank) || new java.util.HashSet<>(headers).size() != headers.size())
                                throw new IllegalArgumentException("Cabeçalho CSV inválido.");
                        ObjectNode output = objectMapper.createObjectNode().put("type", "FeatureCollection");
                        ArrayNode features = output.putArray("features");
                        List<String> row;
                        int index = 0;
                        while ((row = reader.readRecord()) != null) {
                                if (row.size() == 1 && row.getFirst().isBlank()) continue;
                                if (row.size() != headers.size()) throw new IllegalArgumentException("Quantidade de colunas inválida no registro " + index + ".");
                                var record = objectMapper.createObjectNode();
                                for (int i = 0; i < headers.size(); i++) record.put(headers.get(i), row.get(i).trim());
                                features.add(catdevs.georuraldatahub.validation.GeoJsonContent.feature(record, index++));
                        }
                        return output;
                }
        }
}
