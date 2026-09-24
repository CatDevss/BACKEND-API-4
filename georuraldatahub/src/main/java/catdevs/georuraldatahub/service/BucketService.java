package catdevs.georuraldatahub.service;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import catdevs.georuraldatahub.config.ObjectStorageProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Responsável por conversar com o bucket: listar, enviar e baixar arquivos.
 */

@Service
public class BucketService {

    private final ObjectStorageClient client;
    private final ObjectStorageProperties props;

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
}