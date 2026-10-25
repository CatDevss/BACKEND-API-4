package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.service.BucketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TreatmentServiceTest {
    private final JpaTreatmentStore store = mock(JpaTreatmentStore.class);
    private final TreatmentInputService inputs = mock(TreatmentInputService.class);
    private final VectorTreatment vectors = mock(VectorTreatment.class);
    private final BucketService bucket = mock(BucketService.class);
    private final TreatmentRequest request = new TreatmentRequest(1L, 3L, null, false, null);
    private final TreatmentInputService.Input input = new TreatmentInputService.Input(7L, 10L, "arquivo.csv", "csv",
            "bruta/original.csv", "validada/arquivo.csv", 5L, "hash", 2L, 1, List.of());
    @Test void disabledPersistenceDoesNotAccessDatabaseBucketOrVectorTreatment() {
        var service = new TreatmentService(Optional.empty(), inputs, vectors, bucket);
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.start(7L, request)).getStatusCode().value());
        verifyNoInteractions(store, inputs, vectors, bucket);
    }
    @Test void failedTransformationRecordsFailureWithoutApprovingOrPersistingAResult() throws Exception {
        when(inputs.load(7L, 3L)).thenReturn(input); when(store.start(input, request)).thenReturn(20L);
        var content = new ObjectMapper().createObjectNode();
        when(bucket.readCsvAsGeoJson("validada/arquivo.csv")).thenReturn(content);
        when(vectors.apply(content, input.rules(), request)).thenThrow(new IllegalArgumentException("Data inválida"));
        var service = new TreatmentService(Optional.of(store), inputs, vectors, bucket);
        assertThrows(IllegalArgumentException.class, () -> service.start(7L, request));
        verify(store).fail(20L, "Data inválida"); verify(store, never()).complete(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyBoolean());
        verify(bucket, never()).readCsvAsGeoJson("bruta/original.csv");
    }
    @Test void successPassesApprovedSubsetAndProvenanceToAtomicPersistence() throws Exception {
        when(inputs.load(7L, 3L)).thenReturn(input); when(store.start(input, request)).thenReturn(20L);
        var content = new ObjectMapper().createObjectNode(); var trace = new ObjectMapper().createObjectNode().put("targetSrid", 4326);
        when(bucket.readCsvAsGeoJson(input.validatedLocation())).thenReturn(content);
        when(vectors.apply(content, input.rules(), request)).thenReturn(new VectorTreatment.Result(content, trace, 4, 2));
        new TreatmentService(Optional.of(store), inputs, vectors, bucket).start(7L, request);
        verify(store).complete(20L, input, request, content, trace, 4, 2, false); verify(store, never()).fail(any(), any());
    }
    @Test void rasterPathVerifiesParanaCoverageAndNeverConvertsPixelsToFeatures() throws Exception {
        var rasterInput = new TreatmentInputService.Input(7L, 10L, "raster.tif", "tif", "bruta/raster.tif",
                "validada/raster.tif.raster.json", 5L, "hash", 2L, 1, List.of());
        var rasterRequest = new TreatmentRequest(null, 3L, null, false, null);
        when(inputs.load(7L, 3L)).thenReturn(rasterInput); when(store.start(rasterInput, rasterRequest)).thenReturn(20L);
        when(bucket.readRasterManifest(rasterInput.validatedLocation())).thenReturn(new ObjectMapper().readTree("""
            {"kind":"RASTER","sourceSrid":31982,"toolVersion":"GeoTools 35.2","summary":{"footprint":{"type":"Polygon","coordinates":[[[-51,-25],[-50,-25],[-50,-24],[-51,-24],[-51,-25]]]}}}
            """));
        var envelope = new ObjectMapper().readTree("""
            {"type":"FeatureCollection","features":[{"geometry":{"type":"Polygon","coordinates":[[[-51,-25],[-50,-25],[-50,-24],[-51,-24],[-51,-25]]]}}]}
            """);
        when(vectors.apply(any(), eq(List.of()), eq(rasterRequest)))
                .thenReturn(new VectorTreatment.Result(envelope, new ObjectMapper().createObjectNode(), 1, 1));
        new TreatmentService(Optional.of(store), inputs, vectors, bucket).start(7L, rasterRequest);
        verify(store).complete(eq(20L), eq(rasterInput), eq(rasterRequest),
                argThat(content -> "RASTER".equals(content.path("kind").asText())),
                argThat(trace -> !trace.path("pixelProcessing").asBoolean(true) && trace.has("paranaIntersection")),
                eq(1), eq(1), eq(true));
        verify(bucket, never()).readGeoJson(any()); verify(bucket, never()).readCsvAsGeoJson(any());
        verify(bucket, never()).readGeospatialMetadata(any());
    }

}
