package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

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
import catdevs.georuraldatahub.processing.ProcessingProperties;
import catdevs.georuraldatahub.validation.CsvRecordValidator;
import catdevs.georuraldatahub.validation.GeoJsonContent;

/**
 * Responsável por conversar com o OCI Object Storage:
 * listar, enviar, baixar, ler e excluir arquivos.
 */
@Service
public class BucketService {

    private final ObjectStorageClient client;
    private final ObjectStorageProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessingProperties limits;

    public BucketService(
            ObjectStorageClient client,
            ObjectStorageProperties props,
            ProcessingProperties limits) {

        this.client = client;
        this.props = props;
        this.limits = limits;
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
     * Envia um arquivo para a zona bruta de um conjunto de dados.
     */
    public String uploadToRawZone(
            MultipartFile file,
            Long datasetId,
            String hash,
            String sanitizedFilename) throws IOException {

        return uploadToRawScope(
                file,
                datasetId.toString(),
                hash,
                sanitizedFilename);
    }

    /**
     * Envia um arquivo para a zona bruta de uma fonte.
     */
    public String uploadToSourceRawZone(
            MultipartFile file,
            Long sourceId,
            String hash,
            String filename) throws IOException {

        return uploadToRawScope(
                file,
                "fontes/" + sourceId,
                hash,
                filename);
    }

    /**
     * Envia um arquivo para uma localização definida na zona bruta.
     */
    private String uploadToRawScope(
            MultipartFile file,
            String scope,
            String hash,
            String sanitizedFilename) throws IOException {

        String objectName =
                "bruta/" + scope + "/" + hash + "_" + sanitizedFilename;

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
     * Envia conteúdo validado para a zona de arquivos aprovados
     * de um conjunto de dados.
     */
    public String uploadValidatedContent(
            byte[] content,
            Long datasetId,
            String hash,
            String filename) {

        return uploadValidatedScope(
                content,
                datasetId.toString(),
                hash,
                filename);
    }

    /**
     * Envia conteúdo validado para a zona de arquivos aprovados
     * de uma fonte.
     */
    public String uploadSourceValidatedContent(
            byte[] content,
            Long sourceId,
            String hash,
            String filename) {

        return uploadValidatedScope(
                content,
                "fontes/" + sourceId,
                hash,
                filename);
    }

    /**
     * Envia conteúdo para a zona de arquivos aprovados.
     */
    private String uploadValidatedScope(
            byte[] content,
            String scope,
            String hash,
            String filename) {

        String key =
                "validada/" + scope + "/" + hash + "_" + filename;

        try (InputStream input =
                     new java.io.ByteArrayInputStream(content)) {

            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(props.bucketName())
                    .objectName(key)
                    .putObjectBody(input)
                    .contentLength((long) content.length)
                    .build();

            client.putObject(request);

        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        return key;
    }

    /**
     * Envia um relatório de validação associado a um arquivo bruto.
     */
    public String uploadValidationReport(
            String rawLocation,
            String json) {

        String key = rawLocation + ".validation.json";
        byte[] content = json.getBytes(StandardCharsets.UTF_8);

        try (InputStream input =
                     new java.io.ByteArrayInputStream(content)) {

            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(props.bucketName())
                    .objectName(key)
                    .putObjectBody(input)
                    .contentLength((long) content.length)
                    .contentType("application/json")
                    .build();

            client.putObject(request);

        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        return key;
    }

    /**
     * Lê o relatório de validação de um arquivo.
     */
    public String readValidationReport(
            String rawLocation) throws IOException {

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(rawLocation + ".validation.json")
                .build();

        try (InputStream input =
                     client.getObject(request).getInputStream()) {

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8);

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
     * Envia um arquivo para a zona de quarentena.
     */
    public String uploadToQuarantineZone(
            MultipartFile file,
            String rawLocation) throws IOException {

        return uploadMultipart(
                file,
                quarantineLocation(rawLocation));
    }

    /**
     * Converte a localização de um arquivo bruto para sua localização
     * correspondente na quarentena.
     */
    public static String quarantineLocation(String rawLocation) {

        if (rawLocation == null
                || !rawLocation.startsWith("bruta/")
                || rawLocation.length() <= "bruta/".length()) {

            throw new IllegalArgumentException(
                    "Localização bruta inválida para quarentena.");
        }

        return "quarentena/"
                + rawLocation.substring("bruta/".length());
    }

    /**
     * Envia um arquivo MultipartFile para uma localização específica.
     */
    private String uploadMultipart(
            MultipartFile file,
            String objectName) throws IOException {

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
     * Abre um stream do objeto sem materializar o arquivo inteiro na memória.
     * O chamador é responsável por fechar o InputStream.
     */
    public InputStream openObjectStream(String objectName) throws IOException {
        validateObjectName(objectName);
        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();
        try {
            return client.getObject(request).getInputStream();
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado no Object Storage.");
            }
            throw e;
        }
    }

    /** Lê somente o prefixo do objeto, com limite rígido de bytes. */
    public byte[] readObjectPrefix(String objectName, int maximumBytes) throws IOException {
        validateObjectName(objectName);
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("O limite de leitura não pode ser negativo.");
        }
        if (maximumBytes == 0) return new byte[0];

        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();
        try (InputStream input = client.getObject(request).getInputStream()) {
            return input.readNBytes(maximumBytes);
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado no Object Storage.");
            }
            throw e;
        }
    }

    /**
     * Compatibilidade com chamadores antigos: a leitura integral agora tem limite.
     * Para arquivos grandes, use openObjectStream ou readObjectPrefix.
     */
    @Deprecated
    public byte[] readObjectContent(String objectName) throws IOException {
        int maximum = Math.toIntExact(Math.min(Integer.MAX_VALUE - 1L,
                Math.max(1L, limits.getMaxOutputBytes())));
        try (InputStream input = openObjectStream(objectName)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "O arquivo excede o limite para leitura integral. Use leitura em fluxo.");
            }
            return bytes;
        }
    }

    /**
     * Envia um artefato de coleta automática.
     */
    public String uploadCollectionArtifact(
            String key,
            byte[] bytes,
            String contentType) {

        try (InputStream input =
                     new java.io.ByteArrayInputStream(bytes)) {

            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(props.bucketName())
                    .objectName(key)
                    .putObjectBody(input)
                    .contentLength((long) bytes.length)
                    .contentType(contentType)
                    .build();

            client.putObject(request);

        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        return key;
    }

    /**
     * Lê os metadados de uma coleta automática.
     */
    public String readCollectionMetadata(
            String rawLocation) throws IOException {

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(rawLocation + ".collection.json")
                .build();

        try (InputStream input =
                     client.getObject(request).getInputStream()) {

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8);

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
     * Lê os metadados geoespaciais extraídos de um arquivo.
     */
    public String readGeospatialMetadata(
            String rawLocation) throws IOException {

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(rawLocation + ".geospatial.json")
                .build();

        try (InputStream input =
                     client.getObject(request).getInputStream()) {

            int maximum =
                    ProcessingProperties.MAX_METADATA_BYTES;

            byte[] bytes = input.readNBytes(maximum + 1);

            if (bytes.length > maximum) {
                throw new IllegalArgumentException(
                        "Metadados geoespaciais excedem o limite.");
            }

            return new String(bytes, StandardCharsets.UTF_8);

        } catch (BmcException e) {

            if (e.getStatusCode() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arquivo sem metadados geoespaciais extraídos.");
            }

            throw e;
        }
    }

    /**
     * Lê o manifesto de uma versão raster aprovada.
     */
    public JsonNode readRasterManifest(
            String location) throws IOException {

        if (location == null
                || !location.startsWith("validada/")
                || !location.endsWith(".raster.json")) {

            throw new IllegalArgumentException(
                    "Informe o manifesto da versão raster aprovada.");
        }

        validateObjectName(location);

        var request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(location)
                .build();

        try (InputStream input =
                     client.getObject(request).getInputStream()) {

            int maximum =
                    ProcessingProperties.MAX_METADATA_BYTES;

            byte[] bytes = input.readNBytes(maximum + 1);

            if (bytes.length > maximum) {
                throw new IllegalArgumentException(
                        "Metadados geoespaciais excedem o limite.");
            }

            return objectMapper.readTree(bytes);
        }
    }

    /**
     * Limita a quantidade de bytes que podem ser lidos de um objeto.
     */
    private InputStream bounded(InputStream input) {

        return new java.io.FilterInputStream(input) {

            private long remaining = limits.getMaxOutputBytes();

            private void consumed(int count) {

                if (count > 0 && (remaining -= count) < 0) {
                    throw new IllegalArgumentException(
                            "Conteúdo vetorial excede o limite de leitura.");
                }
            }

            @Override
            public int read() throws IOException {

                int value = in.read();

                consumed(value == -1 ? 0 : 1);

                return value;
            }

            @Override
            public int read(
                    byte[] bytes,
                    int offset,
                    int length) throws IOException {

                int requested = (int) Math.min(
                        length,
                        Math.max(0, remaining) + 1);

                int count = in.read(bytes, offset, requested);

                consumed(count);

                return count;
            }
        };
    }

    /**
     * Lê um arquivo GeoJSON armazenado no Object Storage.
     */
    public JsonNode readGeoJson(
            String objectName) throws IOException {

        validateObjectName(objectName);

        String normalizedName =
                objectName.toLowerCase(Locale.ROOT);

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

        try (InputStream input =
                     client.getObject(request).getInputStream()) {

            JsonNode root = objectMapper.readTree(bounded(input));

            JsonNode collection =
                    GeoJsonContent.featureCollection(root);

            if (collection.path("features").size()
                    > limits.getMaxFeatures()) {

                throw new IllegalArgumentException(
                        "Limite de feições excedido.");
            }

            return collection;
        }
    }

    /**
     * Lê um CSV e converte seus registros em um GeoJSON FeatureCollection.
     */
    public JsonNode readCsvAsGeoJson(
            String objectName) throws IOException {

        validateObjectName(objectName);

        String normalizedName =
                objectName.toLowerCase(Locale.ROOT);

        if (!normalizedName.endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "O arquivo precisa possuir extensão .csv.");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .build();

        try (InputStream input =
                     client.getObject(request).getInputStream();
             CsvRecordValidator.TableReader reader =
                     new CsvRecordValidator.TableReader(bounded(input))) {

            List<String> headers = reader.readRecord();

            if (headers == null) {
                throw new IllegalArgumentException(
                        "O CSV está vazio.");
            }

            headers = headers.stream()
                    .map(String::trim)
                    .toList();

            if (headers.stream().anyMatch(String::isBlank)
                    || new HashSet<>(headers).size() != headers.size()) {

                throw new IllegalArgumentException(
                        "Cabeçalho CSV inválido.");
            }

            ObjectNode output = objectMapper.createObjectNode();
            output.put("type", "FeatureCollection");

            ArrayNode features = output.putArray("features");

            List<String> row;
            int index = 0;

            while ((row = reader.readRecord()) != null) {

                if (row.size() == 1 && row.getFirst().isBlank()) {
                    continue;
                }

                if (row.size() != headers.size()) {
                    throw new IllegalArgumentException(
                            "Quantidade de colunas inválida no registro "
                                    + (index + 2) + ".");
                }

                ObjectNode record = objectMapper.createObjectNode();

                for (int i = 0; i < headers.size(); i++) {
                    record.put(headers.get(i), row.get(i).trim());
                }

                if (index >= limits.getMaxFeatures()) {
                    throw new IllegalArgumentException(
                            "Limite de feições excedido.");
                }

                features.add(
                        GeoJsonContent.feature(record, index));

                index++;
            }

            return output;
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
     * Valida o nome de um objeto para impedir caminhos inválidos.
     */
    private void validateObjectName(String objectName) {

        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do objeto é obrigatório.");
        }

        if (objectName.contains("..")
                || objectName.startsWith("/")
                || objectName.contains("\\")) {

            throw new IllegalArgumentException(
                    "Nome de objeto inválido.");
        }
    }
}