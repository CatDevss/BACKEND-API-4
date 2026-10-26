package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.config.ObjectStorageProperties;
import catdevs.georuraldatahub.processing.ProcessingProperties;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.objectstorage.responses.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class OriginalAndWorkingCopyTest {
    private final ObjectStorageClient client = mock(ObjectStorageClient.class);
    private final BucketService bucket = new BucketService(client, new ObjectStorageProperties("ns", "bucket", "compartment"), new ProcessingProperties());
    private final MockMultipartFile file = new MockMultipartFile("file", "a.csv", "text/csv", "abc".getBytes(StandardCharsets.UTF_8));
    private final String hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test void rawUploadUsesConditionalCreationAndCannotDeleteOrChangeOriginalPointer() throws Exception {
        bucket.uploadToSourceRawZone(file, 4L, hash, "a.csv");
        verify(client).putObject(argThat(request -> "*".equals(request.getIfNoneMatch())
                && hash.equals(request.getOpcMeta().get("sha256"))));
        assertThrows(IllegalArgumentException.class, () -> bucket.deleteObject("bruta/fontes/4/original.csv"));
        assertThrows(IllegalArgumentException.class, () -> bucket.copyWorkingObject("tratada/test.csv", "bruta/test.csv"));
        verify(client, never()).deleteObject(any());
        assertThrows(IllegalArgumentException.class, () -> BucketService.workingLocation("bruta/test.csv", "validada/"));
        var original = new catdevs.georuraldatahub.entity.File();
        original.setLocation("bruta/original.csv");
        assertThrows(IllegalStateException.class, () -> original.setLocation("bruta/outro.csv"));
        assertThrows(IllegalArgumentException.class, () -> original.setLocation("quarentena/original.csv"));
        assertEquals("bruta/original.csv", original.getLocation());
    }

    @Test void existingIdenticalRawObjectIsReusedWithoutOverwritingItsBytes() throws Exception {
        var duplicate = mock(com.oracle.bmc.model.BmcException.class); when(duplicate.getStatusCode()).thenReturn(412);
        when(client.putObject(any())).thenThrow(duplicate);
        when(client.headObject(any())).thenReturn(HeadObjectResponse.builder().contentLength(3L).opcMeta(Map.of("sha256", hash)).build());
        assertEquals("bruta/fontes/4/" + hash + "_a.csv", bucket.uploadToSourceRawZone(file, 4L, hash, "a.csv"));
        verify(client, times(1)).putObject(argThat(request -> "*".equals(request.getIfNoneMatch())));
        verify(client, never()).deleteObject(any());
        when(client.headObject(any())).thenReturn(HeadObjectResponse.builder().contentLength(4L).opcMeta(Map.of("sha256", hash)).build());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> bucket.uploadToSourceRawZone(file, 4L, hash, "a.csv")).getStatusCode().value());
    }

    @Test void legacyRawObjectsWithoutMetadataAreHashedBeforeReuse() throws Exception {
        var duplicate = mock(com.oracle.bmc.model.BmcException.class); when(duplicate.getStatusCode()).thenReturn(412);
        when(client.putObject(any())).thenThrow(duplicate);
        when(client.headObject(any())).thenReturn(HeadObjectResponse.builder().contentLength(3L).build());
        when(client.getObject(any())).thenReturn(GetObjectResponse.builder().inputStream(new ByteArrayInputStream(file.getBytes())).build());
        assertTrue(bucket.uploadToSourceRawZone(file, 4L, hash, "a.csv").startsWith("bruta/"));
        verify(client).getObject(any());
        verify(client, never()).deleteObject(any());
    }

    @Test void workingCopyIsStreamedIntoNextZoneAndKeepsOriginalUntouched() throws Exception {
        when(client.getObject(any())).thenReturn(GetObjectResponse.builder().inputStream(new ByteArrayInputStream(file.getBytes()))
                .contentLength(3L).contentType("text/csv").build());
        assertEquals("tratada/revisao.csv", bucket.copyWorkingObject("quarentena/revisao.csv", "tratada/revisao.csv"));
        verify(client).getObject(argThat(request -> "quarentena/revisao.csv".equals(request.getObjectName())));
        var request = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class); verify(client).putObject(request.capture());
        assertEquals("tratada/revisao.csv", request.getValue().getObjectName());
        assertEquals("text/csv", request.getValue().getContentType());
        assertArrayEquals(file.getBytes(), request.getValue().getPutObjectBody().readAllBytes());
        verify(client, never()).deleteObject(any());
    }
}
