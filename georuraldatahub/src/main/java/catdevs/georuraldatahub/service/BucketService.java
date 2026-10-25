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
        private final catdevs.georuraldatahub.processing.ProcessingProperties limits;

        public BucketService(ObjectStorageClient client, ObjectStorageProperties props,
                        catdevs.georuraldatahub.processing.ProcessingProperties limits) {
                this.client = client;
                this.props = props;
                this.limits = limits;
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
                return uploadMultipart(file, objectName, hash);
        }

        public static String quarantineLocation(String rawLocation) {
                if (rawLocation == null || !rawLocation.startsWith("bruta/") || rawLocation.length() <= 6)
                        throw new IllegalArgumentException("Localização bruta inválida para quarentena.");
                return "quarentena/" + rawLocation.substring("bruta/".length());
        }

        public String uploadToQuarantineZone(MultipartFile file, String rawLocation) throws IOException {
                return uploadMultipart(file, quarantineLocation(rawLocation));
        }

        private String uploadMultipart(MultipartFile file, String objectName) throws IOException {
                return uploadMultipart(file, objectName, null);
        }

        private String uploadMultipart(MultipartFile file, String objectName, String originalHash) throws IOException {
                try (InputStream body = file.getInputStream()) {
                        PutObjectRequest request = PutObjectRequest.builder()
                                .namespaceName(props.namespace())
                                .bucketName(props.bucketName())
                                .objectName(objectName)
                                .putObjectBody(body)
                                .contentLength(file.getSize())
                                .ifNoneMatch(originalHash == null ? null : "*")
                                .opcMeta(originalHash == null ? null : java.util.Map.of("sha256", originalHash))
                                .build();
                        try { client.putObject(request); }
                        catch (com.oracle.bmc.model.BmcException error) {
                                if (originalHash == null || error.getStatusCode() != 412) throw error;
                                var existing = client.headObject(com.oracle.bmc.objectstorage.requests.HeadObjectRequest.builder()
                                        .namespaceName(props.namespace()).bucketName(props.bucketName()).objectName(objectName).build());
                                String storedHash = existing.getOpcMeta() == null ? null : existing.getOpcMeta().get("sha256");
                                if (storedHash == null) storedHash = originalHash(objectName);
                                if (!Long.valueOf(file.getSize()).equals(existing.getContentLength()) || !originalHash.equals(storedHash))
                                        throw new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.CONFLICT,
                                                "Já existe um original bruto neste caminho; ele não será sobrescrito.");
                        }
                }
                return objectName;
        }

        private String originalHash(String key) throws IOException {
                try {
                        var digest = java.security.MessageDigest.getInstance("SHA-256");
                        var response = client.getObject(GetObjectRequest.builder().namespaceName(props.namespace())
                                .bucketName(props.bucketName()).objectName(key).build());
                        try (var input = new java.security.DigestInputStream(response.getInputStream(), digest)) {
                                input.transferTo(java.io.OutputStream.nullOutputStream());
                        }
                        return java.util.HexFormat.of().formatHex(digest.digest());
                } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
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

        public JsonNode readVersionValidationReport(String versionLocation, String rawLocation) throws IOException {
                String json;
                try { json = readValidationReport(versionLocation); }
                catch (org.springframework.web.server.ResponseStatusException error) {
                        if (error.getStatusCode().value() != 404) throw error;
                        // Compatibilidade com versões anteriores ao relatório associado à cópia de trabalho.
                        json = readValidationReport(rawLocation);
                }
                return objectMapper.readTree(json);
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

        public String readGeospatialMetadata(String rawLocation) throws IOException {
                var request = GetObjectRequest.builder().namespaceName(props.namespace()).bucketName(props.bucketName())
                        .objectName(rawLocation + ".geospatial.json").build();
                try (InputStream input = client.getObject(request).getInputStream()) {
                        int maximum = catdevs.georuraldatahub.processing.ProcessingProperties.MAX_METADATA_BYTES;
                        byte[] bytes = input.readNBytes(maximum + 1);
                        if (bytes.length > maximum) throw new IllegalArgumentException("Metadados geoespaciais excedem o limite.");
                        return new String(bytes, StandardCharsets.UTF_8);
                } catch (com.oracle.bmc.model.BmcException e) {
                        if (e.getStatusCode() == 404) throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "Arquivo sem metadados geoespaciais extraídos.");
                        throw e;
                }
        }

        private InputStream bounded(InputStream input) {
                return new java.io.FilterInputStream(input) {
                        private long remaining = limits.getMaxOutputBytes();
                        private void consumed(int count) {
                                if (count > 0 && (remaining -= count) < 0)
                                        throw new IllegalArgumentException("Conteúdo vetorial excede o limite de leitura.");
                        }
                        @Override public int read() throws IOException {
                                int value = in.read();
                                consumed(value == -1 ? 0 : 1);
                                return value;
                        }
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                                int count = in.read(bytes, offset, (int) Math.min(length, Math.max(0, remaining) + 1));
                                consumed(count);
                                return count;
                        }
                };
        }

        public JsonNode readRasterManifest(String location) throws IOException {
                if (location == null || !location.startsWith("validada/") || !location.endsWith(".raster.json"))
                        throw new IllegalArgumentException("Informe o manifesto da versão raster aprovada.");
                var request = GetObjectRequest.builder().namespaceName(props.namespace()).bucketName(props.bucketName())
                        .objectName(location).build();
                try (InputStream input = client.getObject(request).getInputStream()) {
                        int maximum = catdevs.georuraldatahub.processing.ProcessingProperties.MAX_METADATA_BYTES;
                        byte[] bytes = input.readNBytes(maximum + 1);
                        if (bytes.length > maximum) throw new IllegalArgumentException("Metadados geoespaciais excedem o limite.");
                        return objectMapper.readTree(bytes);
                }
        }

        public static String workingLocation(String location, String zone) {
                if (location == null || !location.contains("/")
                        || !java.util.Set.of("validada/", "quarentena/", "tratada/", "publicada/").contains(zone))
                        throw new IllegalArgumentException("Localização ou zona de trabalho inválida.");
                return zone + location.substring(location.indexOf('/') + 1);
        }

        /** Copia por streaming; a origem é preservada e o destino nunca pode ser a zona bruta. */
        public String copyWorkingObject(String source, String target) throws IOException {
                if (source == null || source.equals(target) || target == null
                        || java.util.Set.of("validada", "quarentena", "tratada", "publicada").stream()
                                .noneMatch(zone -> target.startsWith(zone + "/")))
                        throw new IllegalArgumentException("Destino de cópia inválido.");
                var object = client.getObject(GetObjectRequest.builder().namespaceName(props.namespace())
                        .bucketName(props.bucketName()).objectName(source).build());
                try (InputStream input = object.getInputStream()) {
                        client.putObject(PutObjectRequest.builder().namespaceName(props.namespace()).bucketName(props.bucketName())
                                .objectName(target).putObjectBody(input).contentLength(object.getContentLength())
                                .contentType(object.getContentType()).build());
                }
                return target;
        }

        public void deleteObject(String objectName) {
                if (objectName == null || objectName.startsWith("bruta/"))
                        throw new IllegalArgumentException("O original e seus artefatos brutos são permanentes e não podem ser excluídos.");
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

                        JsonNode root = objectMapper.readTree(bounded(inputStream));
                        JsonNode collection = catdevs.georuraldatahub.validation.GeoJsonContent.featureCollection(root);
                        if (collection.path("features").size() > limits.getMaxFeatures())
                                throw new IllegalArgumentException("Limite de feições excedido.");
                        return collection;
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
                                var reader = new catdevs.georuraldatahub.validation.CsvRecordValidator.TableReader(bounded(input))) {
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
                                if (index >= limits.getMaxFeatures()) throw new IllegalArgumentException("Limite de feições excedido.");
                                features.add(catdevs.georuraldatahub.validation.GeoJsonContent.feature(record, index++));
                        }
                        return output;
                }
        }
}
