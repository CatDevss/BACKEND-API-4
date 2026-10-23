package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.*;

@ExtendWith(MockitoExtension.class)
class LayerServiceValidationTest {
    @Mock private FileRepository files;
    @Mock private DatasetRepository datasets;
    @Mock private BucketService bucket;
    @Mock private VersionRepository versions;
    @InjectMocks private LayerService service;
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
        when(versions.findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(1L, VersionStatus.BRUTA, "validada/")).thenReturn(Optional.of(version));
        service.getFileLayer(1L);
        verify(bucket).readCsvAsGeoJson("validada/filtered.csv");
        verify(bucket, never()).readCsvAsGeoJson("bruta/original.csv");
    }
    @Test void listsOnlyApprovedFiles() {
        when(datasets.existsById(4L)).thenReturn(true);
        when(files.findByDatasetId(4L)).thenReturn(List.of(file(1L), file(2L)));
        when(versions.findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(1L, VersionStatus.BRUTA, "validada/")).thenReturn(Optional.of(new Version()));
        assertEquals(1, service.listLayers(4L).size());
    }
}
