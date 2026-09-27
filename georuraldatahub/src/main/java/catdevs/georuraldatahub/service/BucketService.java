package catdevs.georuraldatahub.service;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import catdevs.georuraldatahub.config.ObjectStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Responsável por conversar com o bucket:
 * listar e enviar arquivos.
 */
@Service
public class BucketService {

    private final ObjectStorageClient client;
    private final ObjectStorageProperties props;

    public BucketService(
            ObjectStorageClient client,
            ObjectStorageProperties props
    ) {
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
                .map(objectSummary -> objectSummary.getName())
                .toList();
    }

    /**
     * Envia o arquivo para a zona bruta do bucket.
     *
     * Estrutura:
     *
     * bruta/{datasetId}/{hash}_{filename}
     *
     * Exemplo:
     *
     * bruta/15/a8f3c2..._imoveis_rurais.geojson
     *
     * O filename deve chegar previamente sanitizado.
     */
    public String uploadToRawZone(
            MultipartFile file,
            Long datasetId,
            String hash,
            String filename
    ) throws IOException {

        String objectName =
                "bruta/" + datasetId + "/" + hash + "_" + filename;

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
}