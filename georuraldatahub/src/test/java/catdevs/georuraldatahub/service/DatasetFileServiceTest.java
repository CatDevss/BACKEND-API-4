package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;

class DatasetFileServiceTest {
    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final FileRepository files = mock(FileRepository.class);
    private final DatasetFileService service = new DatasetFileService(datasets, files);
    private final Dataset dataset = new Dataset();
    private final File file = new File();

    @BeforeEach void setup() {
        file.setId(6L);
        when(datasets.findLockedById(1L)).thenReturn(Optional.of(dataset));
        when(files.findById(6L)).thenReturn(Optional.of(file));
    }
    @Test void associatesExistingFileThroughManagedRelationshipAfterAcquiringLock() {
        service.associate(1L, 6L);
        assertEquals(java.util.List.of(file), dataset.getFiles());
        var order = inOrder(datasets, files);
        order.verify(datasets).findLockedById(1L);
        order.verify(files).findById(6L);
        verifyNoMoreInteractions(files);
    }
    @Test void repeatedAssociationIsIdempotentEvenForDifferentEntityInstancesWithSameId() {
        File previous = new File(); previous.setId(6L);
        dataset.getFiles().add(previous);
        service.associate(1L, 6L);
        assertEquals(1, dataset.getFiles().size());
        assertSame(previous, dataset.getFiles().getFirst());
    }
    @Test void sameFileCanBelongToTwoDatasets() {
        var other = new Dataset();
        when(datasets.findLockedById(2L)).thenReturn(Optional.of(other));
        service.associate(1L, 6L);
        service.associate(2L, 6L);
        assertSame(file, dataset.getFiles().getFirst());
        assertSame(file, other.getFiles().getFirst());
        verify(files, never()).save(any());
    }
    @Test void dissociationKeepsOtherMembersAndOtherDatasetsAndNeverDeletesFile() {
        var otherFile = new File(); otherFile.setId(7L);
        var otherDataset = new Dataset(); otherDataset.getFiles().add(file);
        dataset.getFiles().add(file); dataset.getFiles().add(otherFile);
        service.dissociate(1L, 6L);
        service.dissociate(1L, 6L);
        assertEquals(java.util.List.of(otherFile), dataset.getFiles());
        assertEquals(java.util.List.of(file), otherDataset.getFiles());
        verify(files, times(2)).findById(6L);
        verifyNoMoreInteractions(files);
    }
    @Test void missingDatasetPreventsEitherMutation() {
        assertThrows(DataSetNotFoundException.class, () -> service.associate(99L, 6L));
        assertThrows(DataSetNotFoundException.class, () -> service.dissociate(99L, 6L));
        verifyNoInteractions(files);
    }
    @Test void missingFilePreventsEitherMutation() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.associate(1L, 99L)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.dissociate(1L, 99L));
        assertTrue(dataset.getFiles().isEmpty());
    }
}
