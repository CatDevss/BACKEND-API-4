
package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

class DatasetFileServiceTest {

    private EntityManager entityManager;
    private DatasetFileService service;

    private Dataset dataset;
    private File file;

    @BeforeEach
    void setup() {
        entityManager = mock(EntityManager.class);

        service = new DatasetFileService();

        ReflectionTestUtils.setField(
                service, "entityManager", entityManager
        );

        dataset = new Dataset();
        file = new File();

        dataset.setFiles(new ArrayList<>());
        file.setId(6L);

        when(entityManager.find(
                eq(Dataset.class),
                eq(1L),
                eq(LockModeType.PESSIMISTIC_WRITE)
        )).thenReturn(dataset);

        when(entityManager.find(File.class, 6L))
                .thenReturn(file);
    }

    @Test
    void associatesExistingFile() {
        service.associate(1L, 6L);

        assertEquals(1, dataset.getFiles().size());
        assertSame(file, dataset.getFiles().getFirst());
    }

    @Test
    void repeatedAssociationDoesNotDuplicateFile() {
        File previous = new File();
        previous.setId(6L);

        dataset.getFiles().add(previous);

        service.associate(1L, 6L);

        assertEquals(1, dataset.getFiles().size());
        assertSame(previous, dataset.getFiles().getFirst());
    }

    @Test
    void sameFileCanBelongToTwoDatasets() {
        Dataset other = new Dataset();
        other.setFiles(new ArrayList<>());

        when(entityManager.find(
                eq(Dataset.class),
                eq(2L),
                eq(LockModeType.PESSIMISTIC_WRITE)
        )).thenReturn(other);

        service.associate(1L, 6L);
        service.associate(2L, 6L);

        assertSame(file, dataset.getFiles().getFirst());
        assertSame(file, other.getFiles().getFirst());
    }

    @Test
    void dissociationKeepsOtherFilesAndDoesNotDeleteFile() {
        File otherFile = new File();
        otherFile.setId(7L);

        dataset.getFiles().add(file);
        dataset.getFiles().add(otherFile);

        service.dissociate(1L, 6L);

        assertEquals(1, dataset.getFiles().size());
        assertSame(otherFile, dataset.getFiles().getFirst());

        verify(entityManager, never()).remove(any());
    }

    @Test
    void missingDatasetThrowsException() {
        when(entityManager.find(
                eq(Dataset.class),
                eq(99L),
                eq(LockModeType.PESSIMISTIC_WRITE)
        )).thenReturn(null);

        assertThrows(
                DataSetNotFoundException.class,
                () -> service.associate(99L, 6L)
        );

        assertThrows(
                DataSetNotFoundException.class,
                () -> service.dissociate(99L, 6L)
        );
    }

    @Test
    void missingFileThrowsNotFound() {
        when(entityManager.find(File.class, 99L))
                .thenReturn(null);

        ResponseStatusException associateException = assertThrows(
                ResponseStatusException.class,
                () -> service.associate(1L, 99L)
        );

        assertEquals(
                404,
                associateException.getStatusCode().value()
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.dissociate(1L, 99L)
        );

        assertTrue(dataset.getFiles().isEmpty());
    }
}
