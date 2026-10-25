package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.enums.*;
import catdevs.georuraldatahub.processing.*;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.util.GeoJsonFeatureExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class SpatialLayerCompatibilityTest {
    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final LayerRepository layers = mock(LayerRepository.class);
    private final FeatureService features = mock(FeatureService.class);
    private final BucketService bucket = mock(BucketService.class);
    private final TreatmentService treatments = mock(TreatmentService.class);
    private final jakarta.persistence.EntityManager manager = mock(jakarta.persistence.EntityManager.class);
    private final LegacySpatialLayerStore legacy = new LegacySpatialLayerStore(datasets, files, versions, layers, features, manager, bucket);
    private final SpatialLayerService service = new SpatialLayerService(legacy, layers, bucket, treatments);

    @BeforeEach void begin() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        when(bucket.readVersionValidationReport(any(), any())).thenReturn(new ObjectMapper().createObjectNode().put("status", "VALIDO"));
    }
    @AfterEach void end() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void existingRouteDelegatesToTrackedTreatmentAfterMigration() throws Exception {
        when(treatments.isEnabled()).thenReturn(true);
        var result = mock(JpaTreatmentStore.View.class);
        when(result.layerId()).thenReturn(30L);
        when(result.outputCount()).thenReturn(2);
        when(treatments.startForDataset(1L, 7L, 3L)).thenReturn(result);
        var layer = new Layer(); layer.setId(30L); layer.setName("tratada"); layer.setGeometryType(GeometryType.POLYGON); layer.setSrid(4326);
        when(layers.findById(30L)).thenReturn(Optional.of(layer));
        var response = service.createLayerFromFile(1L, 7L, 3L);
        assertEquals(30L, response.layerId()); assertEquals(2, response.featureCount());
        verifyNoInteractions(datasets, versions, bucket, features, files);
    }

    private Version prepare() {
        var dataset = new Dataset(); dataset.setId(1L);
        var file = new catdevs.georuraldatahub.entity.File(); file.setId(7L); file.setName("teste.geojson"); file.setFormatFile("geojson");
        dataset.getFiles().add(file); when(datasets.findById(1L)).thenReturn(Optional.of(dataset));
        when(datasets.findLockedById(1L)).thenReturn(Optional.of(dataset));
        when(files.findLockedById(7L)).thenReturn(Optional.of(file));
        file.setLocation("bruta/teste.geojson");
        var version = new Version(); version.setId(10L); version.setFile(file); version.setStatus(VersionStatus.BRUTA);
        version.setLocation("validada/teste.geojson");
        when(versions.findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(7L, VersionStatus.BRUTA, "validada/"))
                .thenReturn(Optional.of(version));
        when(versions.findById(10L)).thenReturn(Optional.of(version));
        when(layers.saveAndFlush(any())).thenAnswer(i -> { Layer layer = i.getArgument(0); layer.setId(30L); return layer; });
        when(features.insertBatch(eq(30L), eq(4326), any())).thenReturn(1);
        return version;
    }

    private com.fasterxml.jackson.databind.JsonNode content() throws Exception {
        return new ObjectMapper().readTree("""
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-51,-25]},"properties":{}}]}
            """);
    }

    @Test void priorVectorLoadStillWorksBeforeTheNewTablesAreEnabled() throws Exception {
        var version = prepare();
        when(bucket.readGeoJson(version.getLocation())).thenReturn(content());
        var result = service.createLayerFromFile(1L, 7L, null);
        assertEquals(1, result.featureCount()); assertEquals(VersionStatus.TRATADA, version.getStatus());
        assertEquals("tratada/teste.geojson", version.getLocation());
        verify(bucket).copyWorkingObject("validada/teste.geojson", "tratada/teste.geojson");
        assertEquals("bruta/teste.geojson", version.getFile().getLocation());
        verify(treatments, never()).startForDataset(any(), any(), any());
        var order = inOrder(bucket, files, features, versions);
        order.verify(bucket).readGeoJson("validada/teste.geojson");
        order.verify(files).findLockedById(7L);
        order.verify(features).insertBatch(any(), anyInt(), any());
        order.verify(versions).saveAndFlush(version);
    }

    @Test void secondLoadOfSameSnapshotCannotCreateDuplicateLayer() throws Exception {
        prepare();
        var input = legacy.load(1L, 7L);
        var extraction = GeoJsonFeatureExtractor.extract(content());
        legacy.complete(input, extraction);
        var error = assertThrows(ResponseStatusException.class, () -> legacy.complete(input, extraction));
        assertEquals(409, error.getStatusCode().value());
        verify(layers, times(1)).saveAndFlush(any());
        verify(features, times(1)).insertBatch(any(), anyInt(), any());
    }

    @Test void readFailureDoesNotStartPersistenceOrApproveVersion() throws Exception {
        var version = prepare();
        when(bucket.readGeoJson(version.getLocation())).thenThrow(new java.io.IOException("Storage offline"));
        assertThrows(java.io.IOException.class, () -> service.createLayerFromFile(1L, 7L, null));
        assertEquals(VersionStatus.BRUTA, version.getStatus());
        verifyNoInteractions(layers, features, files);
        verify(versions, never()).saveAndFlush(any());
    }

    @Test void refreshesStateAfterLockToDetectConcurrentCompletionHiddenByRequestCache() throws Exception {
        var version = prepare();
        var input = legacy.load(1L, 7L);
        doAnswer(call -> { version.setStatus(VersionStatus.TRATADA); return null; }).when(manager).refresh(version);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> legacy.complete(input, GeoJsonFeatureExtractor.extract(content()))).getStatusCode().value());
        verifyNoInteractions(layers, features);
    }
}
