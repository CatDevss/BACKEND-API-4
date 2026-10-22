
package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import catdevs.georuraldatahub.dto.DatasetStructureDTO;
import catdevs.georuraldatahub.entity.Dataset;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

class DatasetStructureServiceTest {

    private DatasetService datasets;
    private LayerService layers;
    private EntityManager entityManager;
    private DatasetStructureService service;

    @BeforeEach
    void setup() {
        datasets = mock(DatasetService.class);
        layers = mock(LayerService.class);
        entityManager = mock(EntityManager.class);

        service = new DatasetStructureService(datasets, layers);

        ReflectionTestUtils.setField(
                service, "entityManager", entityManager
        );
    }

    @Test
    void getReturnsDatasetStructureWithoutFiles() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("Conjunto teste");

        when(datasets.findEntityById(1L)).thenReturn(dataset);

        @SuppressWarnings("unchecked")
        TypedQuery<catdevs.georuraldatahub.entity.File> query =
                mock(TypedQuery.class);

        when(entityManager.createQuery(
                anyString(),
                eq(catdevs.georuraldatahub.entity.File.class)
        )).thenReturn(query);

        when(query.setParameter("datasetId", 1L)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(layers.listLayers(1L)).thenReturn(List.of());

        DatasetStructureDTO result = service.get(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("Conjunto teste", result.name());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    void getReturnsEmptySourcesWhenNoFilesAreAssociated() {
        Dataset dataset = new Dataset();
        dataset.setId(2L);
        dataset.setName("Outro conjunto");

        when(datasets.findEntityById(2L)).thenReturn(dataset);

        @SuppressWarnings("unchecked")
        TypedQuery<catdevs.georuraldatahub.entity.File> query =
                mock(TypedQuery.class);

        when(entityManager.createQuery(
                anyString(),
                eq(catdevs.georuraldatahub.entity.File.class)
        )).thenReturn(query);

        when(query.setParameter("datasetId", 2L)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(layers.listLayers(2L)).thenReturn(List.of());

        DatasetStructureDTO result = service.get(2L);

        assertNotNull(result);
        assertEquals(2L, result.id());
        assertTrue(result.sources().isEmpty());
    }

    @Test
    void getSearchesForAssociatedFilesAndLayers() {
        Dataset dataset = new Dataset();
        dataset.setId(3L);
        dataset.setName("Conjunto com camadas");

        when(datasets.findEntityById(3L)).thenReturn(dataset);

        @SuppressWarnings("unchecked")
        TypedQuery<catdevs.georuraldatahub.entity.File> query =
                mock(TypedQuery.class);

        when(entityManager.createQuery(
                anyString(),
                eq(catdevs.georuraldatahub.entity.File.class)
        )).thenReturn(query);

        when(query.setParameter("datasetId", 3L)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(layers.listLayers(3L)).thenReturn(List.of());

        service.get(3L);

        verify(datasets).findEntityById(3L);
        verify(entityManager).createQuery(
                anyString(),
                eq(catdevs.georuraldatahub.entity.File.class)
        );
        verify(query).setParameter("datasetId", 3L);
        verify(layers).listLayers(3L);
    }
}
