
package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetUpdateRequestDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

class DatasetServiceTest {

    private EntityManager entityManager;
    private DatasetService service;

    @BeforeEach
    void setup() {
        entityManager = mock(EntityManager.class);
        service = new DatasetService();

        ReflectionTestUtils.setField(
                service, "entityManager", entityManager
        );
    }

    @Test
    void createDatasetWhenUserExists() {
        User user = new User();
        user.setId(1L);

        when(entityManager.find(User.class, 1L)).thenReturn(user);

        DatasetCreateRequestDTO request =
                new DatasetCreateRequestDTO("Conjunto teste", 1L);

        var response = service.create(request);

        assertNotNull(response);
        assertEquals("Conjunto teste", response.name());
        verify(entityManager).persist(any(Dataset.class));
        verify(entityManager).flush();
    }

    @Test
    void createDatasetWhenUserDoesNotExist() {
        when(entityManager.find(User.class, 99L)).thenReturn(null);

        DatasetCreateRequestDTO request =
                new DatasetCreateRequestDTO("Conjunto teste", 99L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void findDatasetByIdWhenItExists() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("Conjunto teste");

        when(entityManager.find(Dataset.class, 1L)).thenReturn(dataset);

        Dataset found = service.findEntityById(1L);

        assertSame(dataset, found);
    }

    @Test
    void findDatasetByIdWhenItDoesNotExist() {
        when(entityManager.find(Dataset.class, 99L)).thenReturn(null);

        assertThrows(
                DataSetNotFoundException.class,
                () -> service.findEntityById(99L)
        );
    }

    @Test
    void findLockedDatasetUsesPessimisticWriteLock() {
        Dataset dataset = new Dataset();

        when(entityManager.find(
                Dataset.class, 1L, LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(dataset);

        assertSame(dataset, service.findLockedEntityById(1L));

        verify(entityManager).find(
                Dataset.class, 1L, LockModeType.PESSIMISTIC_WRITE
        );
    }

    @Test
    void updateDatasetName() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("Nome antigo");

        when(entityManager.find(
                Dataset.class, 1L, LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(dataset);

        DatasetUpdateRequestDTO request =
                new DatasetUpdateRequestDTO("Nome novo");

        var response = service.update(1L, request);

        assertEquals("Nome novo", response.name());
    }

    @Test
    void updateRejectsBlankName() {
        Dataset dataset = new Dataset();

        when(entityManager.find(
                Dataset.class, 1L, LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(dataset);

        DatasetUpdateRequestDTO request =
                new DatasetUpdateRequestDTO("   ");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(1L, request)
        );
    }

    @Test
    void deleteDatasetWithoutFiles() {
        Dataset dataset = new Dataset();

        when(entityManager.find(
                Dataset.class, 1L, LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(dataset);

        service.delete(1L);

        verify(entityManager).remove(dataset);
    }

    @Test
    void deleteDatasetWithFilesIsRejected() {
        Dataset dataset = new Dataset();
        catdevs.georuraldatahub.entity.File file =
                new catdevs.georuraldatahub.entity.File();

        dataset.getFiles().add(file);

        when(entityManager.find(
                Dataset.class, 1L, LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(dataset);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.delete(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(entityManager, never()).remove(any());
    }

    @Test
    void listAllReturnsDatasets() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("Conjunto teste");

        @SuppressWarnings("unchecked")
        TypedQuery<Dataset> query = mock(TypedQuery.class);

        when(entityManager.createQuery(anyString(), eq(Dataset.class)))
                .thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(dataset));

        var result = service.listAll();

        assertEquals(1, result.size());
        assertEquals("Conjunto teste", result.getFirst().name());
    }
}
