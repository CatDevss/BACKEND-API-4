package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.config.ObjectStorageProperties;
import catdevs.georuraldatahub.processing.ProcessingProperties;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BucketGeospatialReadTest {
    private final ObjectStorageClient client = mock(ObjectStorageClient.class);
    private final ProcessingProperties limits = new ProcessingProperties();
    private final BucketService service = new BucketService(client, new ObjectStorageProperties("namespace", "bucket", "compartment"), limits);

    private void response(String content) {
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(call -> GetObjectResponse.builder()
                .inputStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))).build());
    }

    @Test void limitsBytesBeforeBuildingEntireVectorTree() {
        response("{\"type\":\"FeatureCollection\",\"features\":[]}" + " ".repeat(1000));
        limits.setMaxOutputBytes(20);
        assertThrows(IllegalArgumentException.class, () -> service.readGeoJson("validada/test.json"));
        response("lat,lon,nome\n-25,-51," + "a".repeat(1000));
        assertThrows(IllegalArgumentException.class, () -> service.readCsvAsGeoJson("validada/test.csv"));
    }

    @Test void limitsCsvRecordsAndParsesApprovedGeometryColumn() throws Exception {
        response("lat,lon\n-25,-51\n-24,-50\n"); limits.setMaxFeatures(1);
        assertThrows(IllegalArgumentException.class, () -> service.readCsvAsGeoJson("validada/test.csv"));
        response("geometry,nome\n\"{\"\"type\"\":\"\"Point\"\",\"\"coordinates\"\":[-51,-25]}\",teste\n");
        var feature = service.readCsvAsGeoJson("validada/test.csv").path("features").get(0);
        assertEquals("Point", feature.path("geometry").path("type").asText());
        assertEquals("teste", feature.path("properties").path("nome").asText());
        assertFalse(feature.path("properties").has("geometry"));
    }

    @Test void readsApprovedRasterManifestAndRejectsOversizedMetadata() throws Exception {
        response("{\"kind\":\"RASTER\"}");
        assertEquals("RASTER", service.readRasterManifest("validada/test.raster.json").path("kind").asText());
        verify(client).getObject(argThat(r -> r.getObjectName().equals("validada/test.raster.json")));
        assertThrows(IllegalArgumentException.class, () -> service.readRasterManifest("bruta/test.tif"));
        response(" ".repeat(ProcessingProperties.MAX_METADATA_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> service.readRasterManifest("validada/test.raster.json"));
        assertThrows(IllegalArgumentException.class, () -> service.readGeospatialMetadata("bruta/test.tif"));
    }
}
