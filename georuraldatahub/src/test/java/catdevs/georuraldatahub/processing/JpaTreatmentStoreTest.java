package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.service.*;
import catdevs.georuralprocessing.entity.*;
import catdevs.georuralprocessing.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.*;

class JpaTreatmentStoreTest {
    private final TreatmentExecutionRepository executions = mock(TreatmentExecutionRepository.class);
    private final RasterMetadataRepository rasters = mock(RasterMetadataRepository.class);
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final LayerRepository layers = mock(LayerRepository.class);
    private final FeatureService features = mock(FeatureService.class);
    private final BucketService bucket = mock(BucketService.class);
    private final JpaTreatmentStore store = new JpaTreatmentStore(executions, rasters, files, versions, datasets, layers, features, bucket, mock(jakarta.persistence.EntityManager.class));
    private final TreatmentExecution execution = new TreatmentExecution();
    private final File file = new File();
    private final Version original = new Version();
    private final TreatmentInputService.Input input = new TreatmentInputService.Input(7L, 10L, "arquivo.csv", "csv", "bruta/original.csv",
            "tratada/original.csv", 5L, "hash", 2L, 1, List.of());
    private final TreatmentRequest request = new TreatmentRequest(1L, 3L, null, false, null);
    private final ObjectMapper mapper = new ObjectMapper();
    @BeforeEach void setup() {
        TransactionSynchronizationManager.initSynchronization();
        execution.setId(20L); execution.setStatus("EM_EXECUCAO"); execution.setParameters("{}");
        when(executions.findById(20L)).thenReturn(Optional.of(execution));
        when(executions.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        file.setId(7L); file.setHash("hash"); file.setLocation("bruta/original.csv");
        original.setId(10L); original.setFile(file); original.setLocation("tratada/original.csv");
        original.setStatus(VersionStatus.TRATADA); original.setVersionNumber(2);
        when(files.findLockedById(7L)).thenReturn(Optional.of(file));
        when(versions.findById(10L)).thenReturn(Optional.of(original));
        when(versions.findFirstByFile_IdOrderByVersionNumberDesc(7L)).thenReturn(Optional.of(original));
        when(versions.saveAndFlush(any())).thenAnswer(i -> { Version v = i.getArgument(0); v.setId(11L); return v; });
        var dataset = new Dataset(); dataset.setId(1L); dataset.getFiles().add(file);
        when(datasets.findLockedById(1L)).thenReturn(Optional.of(dataset));
        when(layers.saveAndFlush(any())).thenAnswer(i -> { Layer layer = i.getArgument(0); layer.setId(30L); return layer; });
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }
    private com.fasterxml.jackson.databind.JsonNode point() throws Exception {
        return mapper.readTree("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[-51,-25]},\"properties\":{}}]}");
    }
    @Test void createsAnotherVersionAndFlushesFeaturesBeforeApprovingResult() throws Exception {
        var result = store.complete(20L, input, request, point(), mapper.createObjectNode(), 2, 1, false);
        assertEquals("CONCLUIDO", result.status()); assertEquals(11L, result.outputVersionId()); assertEquals(30L, result.layerId());
        assertEquals(VersionStatus.TRATADA, original.getStatus()); assertEquals("bruta/original.csv", file.getLocation());
        var argument = ArgumentCaptor.forClass(Version.class); verify(versions, times(2)).saveAndFlush(argument.capture());
        assertEquals(3, argument.getValue().getVersionNumber()); assertEquals(VersionStatus.TRATADA, argument.getValue().getStatus());
        verify(features).insertBatch(eq(30L), eq(4326), any());
        var order = inOrder(files, layers, features, versions);
        order.verify(files).findLockedById(7L); order.verify(versions).findById(10L);
        order.verify(versions).findFirstByFile_IdOrderByVersionNumberDesc(7L); order.verify(versions).saveAndFlush(any());
        order.verify(layers).saveAndFlush(any()); order.verify(features).insertBatch(any(), anyInt(), any());
        order.verify(versions).saveAndFlush(any());
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(bucket).deleteObject("tratada/fontes/5/7/execucao-20.geojson");
        verify(bucket).deleteObject("tratada/fontes/5/7/execucao-20.geojson.processing.json");
    }
    @Test void blocksRewritingCompletedExecutionsBeforeReadingOrWritingFiles() throws Exception {
        execution.setStatus("CONCLUIDO");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> store.complete(20L, input, request, point(), mapper.createObjectNode(), 1, 1, false));
        verifyNoInteractions(files, bucket, layers, features);
        store.fail(20L, "Tentar sobrescrever"); verify(executions, never()).save(any());
    }
    @Test void refusesChangedInputAndMissingMembershipBeforeWritingBucket() throws Exception {
        file.setHash("different");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> store.complete(20L, input, request, point(), mapper.createObjectNode(), 1, 1, false));
        file.setHash("hash");
        var emptyDataset = new Dataset(); when(datasets.findLockedById(1L)).thenReturn(Optional.of(emptyDataset));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> store.complete(20L, input, request, point(), mapper.createObjectNode(), 1, 1, false));
        verifyNoInteractions(bucket, layers, features);
    }
    @Test void rasterStoresCoverageAndGridMetadataWithoutVectorFeaturesOrDataset() throws Exception {
        String location = "tratada/original.tif";
        original.setLocation(location);
        var rasterInput = new TreatmentInputService.Input(7L, 10L, "original.tif", "tif", "bruta/original.tif",
                location, 5L, "hash", 2L, 1, List.of());
        when(rasters.saveAndFlush(any())).thenAnswer(i -> { RasterMetadata raster = i.getArgument(0); raster.setId(40L); return raster; });
        var metadata = mapper.readTree("""
            {"kind":"RASTER","sourceSrid":31982,"summary":{"width":8,"height":6,"bandCount":1,
             "pixelWidth":30,"pixelHeight":30,"footprint":{"type":"Polygon", "coordinates":[[[-51,-25],[-50,-25],[-50,-24],[-51,-24],[-51,-25]]]}}}
            """);
        var result = store.complete(20L, rasterInput, new TreatmentRequest(null, 3L, null, false, null),
                metadata, mapper.createObjectNode().put("pixelProcessing", false), 1, 1, true);
        assertEquals(40L, result.rasterId()); assertNull(result.layerId());
        var captured = ArgumentCaptor.forClass(RasterMetadata.class); verify(rasters).saveAndFlush(captured.capture());
        assertEquals(31982, captured.getValue().getSourceSrid()); assertEquals(4326, captured.getValue().getFootprint().getSRID());
        assertEquals("tratada/fontes/5/7/execucao-20.tif", captured.getValue().getLocation());
        verify(bucket).copyWorkingObject("tratada/original.tif", "tratada/fontes/5/7/execucao-20.tif");
        var output = ArgumentCaptor.forClass(Version.class); verify(versions, times(2)).saveAndFlush(output.capture());
        assertEquals("tratada/fontes/5/7/execucao-20.tif", output.getValue().getLocation());
        assertEquals("bruta/original.csv", file.getLocation());
        verifyNoInteractions(datasets, layers, features);
    }
    @Test void catalogSpatialSearchUsesGeographicFilterAndRejectsInvalidBounds() throws Exception {
        when(rasters.findIntersecting(any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        store.searchRasters(null, List.of(-53.0, -27.0, -48.0, -22.0), 0, 20);
        verify(rasters).findIntersecting(argThat(g -> g.getSRID() == 4326 && g.getEnvelopeInternal().getMinX() == -53), any());
        assertThrows(IllegalArgumentException.class, () -> store.searchRasters(null, List.of(-48.0, -27.0, -53.0, -22.0), 0, 20));
        assertThrows(IllegalArgumentException.class, () -> store.searchRasters(1L, List.of(-53.0, -27.0, -48.0, -22.0), 0, 20));
        when(features.findGeometryAsGeoJson(99L)).thenReturn(Optional.empty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> store.searchRasters(99L, null, 0, 20));
    }
    @Test void failureStoresBoundedMessageAndDoesNotCreateOutput() {
        store.fail(20L, "á".repeat(400));
        assertEquals("FALHOU", execution.getStatus()); assertNotNull(execution.getFinishedAt());
        assertEquals(500, execution.getError().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertNull(execution.getOutputVersionId()); verifyNoInteractions(files, bucket, layers, features);
    }
}
