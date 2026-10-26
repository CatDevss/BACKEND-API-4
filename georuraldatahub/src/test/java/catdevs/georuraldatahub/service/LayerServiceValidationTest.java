package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.*;

class LayerServiceValidationTest {
    private final FileRepository files = mock(FileRepository.class);
    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final BucketService bucket = mock(BucketService.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final LayerRepository layers = mock(LayerRepository.class);
    private final LayerService service = new LayerService(files, datasets, bucket, versions, layers);
    private File file(Long id) {
        File file = new File(); file.setId(id); file.setName("test.csv"); file.setFormatFile("csv");
        file.setLocation("bruta/original.csv"); return file;
    }
    @Test void rejectsLayerWithoutApprovedVersion() {
        when(files.findById(1L)).thenReturn(Optional.of(file(1L)));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.getFileLayer(1L)).getStatusCode().value());
        verifyNoInteractions(bucket);
    }
    @Test void readsFilteredVersionInsteadOfOriginal() throws Exception {
        when(files.findById(1L)).thenReturn(Optional.of(file(1L)));
        Version version = new Version(); version.setLocation("validada/filtered.csv");
        when(versions.findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(eq(1L), argThat(statuses -> statuses.contains(VersionStatus.TRATADA)), eq("validada/"))).thenReturn(Optional.of(version));
        service.getFileLayer(1L);
        verify(bucket).readCsvAsGeoJson("validada/filtered.csv");
        verify(bucket, never()).readCsvAsGeoJson("bruta/original.csv");
    }
    @Test void usesTreatedGeoJsonInsteadOfCsvInputWhenAResultExists() throws Exception {
        when(files.findById(1L)).thenReturn(Optional.of(file(1L)));
        var version = new Version(); version.setLocation("tratada/fontes/4/1/execucao-20.geojson");
        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(eq(1L), any()))
                .thenReturn(Optional.of(version));
        service.getFileLayer(1L);
        verify(bucket).readGeoJson(version.getLocation());
        verify(bucket, never()).readCsvAsGeoJson(any());
        verify(versions, never()).findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(any(), any(), any());
    }
    @Test void listsOnlyApprovedFiles() {
        when(datasets.existsById(4L)).thenReturn(true);
        when(files.findByDatasetId(4L)).thenReturn(List.of(file(1L), file(2L)));
        when(versions.findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(eq(1L), argThat(statuses -> statuses.contains(VersionStatus.TRATADA)), eq("validada/"))).thenReturn(Optional.of(new Version()));
        assertEquals(1, service.listLayers(4L).size());
    }

    @Test void historicalLayerReadsItsOwnVersionInsteadOfLatestTreatmentOfFile() throws Exception {
        var historical = new Version(); historical.setId(10L); historical.setStatus(VersionStatus.TRATADA);
        historical.setLocation("tratada/fontes/4/1/execucao-10.geojson");
        var layer = new Layer(); layer.setId(5L); layer.setVersion(historical);
        when(layers.findById(5L)).thenReturn(Optional.of(layer));
        service.getTreatedLayer(5L);
        verify(bucket).readGeoJson(historical.getLocation());
        verifyNoInteractions(files, versions, datasets);
    }

    @Test void refusesUnapprovedAndMissingLayerBeforeReadingStorage() {
        var version = new Version(); version.setStatus(VersionStatus.BRUTA);
        var layer = new Layer(); layer.setVersion(version);
        when(layers.findById(5L)).thenReturn(Optional.of(layer));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.getTreatedLayer(5L)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getTreatedLayer(99L)).getStatusCode().value());
        verifyNoInteractions(bucket);
    }
}