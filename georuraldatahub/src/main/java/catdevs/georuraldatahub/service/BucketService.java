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

    private final ObjectStorageClient cliente;
    private final ObjectStorageProperties propriedades;

    public BucketService(ObjectStorageClient cliente, ObjectStorageProperties propriedades) {
        this.cliente = cliente;
        this.propriedades = propriedades;
    }

    /**
     * Retorna os nomes de todos os arquivos que estão no bucket.
     */

    public List<String> listarObjetos() {
        var requisicao = ListObjectsRequest.builder()
                .namespaceName(propriedades.namespace())
                .bucketName(propriedades.bucketName())
                .build();

        return cliente.listObjects(requisicao)
                .getListObjects()
                .getObjects()
                .stream()
                .map(ObjectSummary::getName)
                .toList();
    }
}