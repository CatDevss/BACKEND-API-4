package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.config.ObjectStorageProperties;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Service
public class BucketService {

    private final ObjectStorageClient client;
    private final ObjectStorageProperties props;

    public BucketService(ObjectStorageClient client, ObjectStorageProperties props) {
        this.client = client;
        this.props = props;
    }

    public List<String> listObjectsBucket() {
        var request = ListObjectsRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .build();

        return client.listObjects(request).getListObjects().getObjects()
                .stream().map(ObjectSummary::getName).toList();
    }

    public String uploadToRawZone(MultipartFile file, Long datasetId,
                                  String hash, String sanitizedFilename) throws IOException {
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

    public String uploadBytesToRawZone(byte[] content, Long sourceId,
                                       String hash, String sanitizedFilename) {
        String objectName = "bruta/fontes/" + sourceId + "/" + hash + "_" + sanitizedFilename;
        PutObjectRequest request = PutObjectRequest.builder()
                .namespaceName(props.namespace())
                .bucketName(props.bucketName())
                .objectName(objectName)
                .putObjectBody(new ByteArrayInputStream(content))
                .contentLength((long) content.length)
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
}