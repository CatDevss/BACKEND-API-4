package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import catdevs.georuraldatahub.config.ObjectStorageProperties;

class BucketServiceValidationReportTest {
    private final ObjectStorageClient client = mock(ObjectStorageClient.class);
    private final BucketService service = new BucketService(client, new ObjectStorageProperties("namespace", "bucket", "compartment"));

    @Test void newFilesUseSourcePathsWithoutCollidingWithHistoricalDatasetPaths() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "test.csv", "text/csv", new byte[]{1});
        assertEquals("bruta/fontes/4/hash_test.csv", service.uploadToSourceRawZone(file, 4L, "hash", "test.csv"));
        assertEquals("validada/fontes/4/hash_test.csv", service.uploadSourceValidatedContent(new byte[]{1}, 4L, "hash", "test.csv"));
        assertEquals("bruta/fontes/5/hash_test.csv", service.uploadToSourceRawZone(file, 5L, "hash", "test.csv"));
        verify(client, times(3)).putObject(any(PutObjectRequest.class));
    }
    @Test void reportIsStoredAlongsideRawObjectWithUtf8AndJsonContentType() throws Exception {
        String json = "{\"erro\":\"Campo obrigatório\"}";
        assertEquals("bruta/test.csv.validation.json", service.uploadValidationReport("bruta/test.csv", json));
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture());
        assertEquals("bruta/test.csv.validation.json", request.getValue().getObjectName());
        assertEquals("application/json", request.getValue().getContentType());
        assertEquals((long) json.getBytes(StandardCharsets.UTF_8).length, request.getValue().getContentLength());
        assertEquals(json, new String(request.getValue().getPutObjectBody().readAllBytes(), StandardCharsets.UTF_8));
    }
    @Test void reportIsReadFromDeterministicObjectKey() throws Exception {
        String json = "{\"status\":\"PARCIAL\"}";
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(GetObjectResponse.builder()
                .inputStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))).build());
        assertEquals(json, service.readValidationReport("bruta/test.csv"));
        verify(client).getObject(argThat(r -> r.getObjectName().equals("bruta/test.csv.validation.json")));
    }
    @Test void missingReportReturnsNotFoundForHistoricalFiles() {
        BmcException missing = mock(BmcException.class); when(missing.getStatusCode()).thenReturn(404);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(missing);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.readValidationReport("bruta/old.csv")).getStatusCode().value());
    }
    @Test void storageFailureIsNotReportedAsMissingReport() {
        BmcException failure = mock(BmcException.class); when(failure.getStatusCode()).thenReturn(500);
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(failure);
        assertSame(failure, assertThrows(BmcException.class, () -> service.readValidationReport("bruta/test.csv")));
    }
}
