
package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.VersionStatus;

class LayerServiceTest {

    private EntityManager entityManager;
    private BucketService bucketService;
    private LayerService layerService;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        bucketService = mock(BucketService.class);

        layerService = new LayerService(bucketService);

        ReflectionTestUtils.setField(
                layerService, "entityManager", entityManager
        );
    }

    @Test
    void listLayersShouldThrowWhenDatasetDoesNotExist() {
        when(entityManager.find(Dataset.class, 1L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> layerService.listLayers(1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getFileLayerShouldThrowWhenFileDoesNotExist() throws Exception {
        when(entityManager.find(File.class, 1L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> layerService.getFileLayer(1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getFileLayerShouldThrowWhenLocationIsMissing() {
        File file = new File();
        file.setLocation(" ");

        when(entityManager.find(File.class, 1L)).thenReturn(file);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> layerService.getFileLayer(1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getFileLayerShouldThrowWhenFormatIsMissing() {
        File file = new File();
        file.setLocation("arquivos/dados");
        file.setFormatFile(null);

        when(entityManager.find(File.class, 1L)).thenReturn(file);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> layerService.getFileLayer(1L)
        );

        assertEquals(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                exception.getStatusCode()
        );
    }

    @Test
    void getFileLayerShouldThrowWhenFormatIsUnsupported() {
        File file = new File();
        file.setLocation("arquivos/dados");
        file.setFormatFile("pdf");

        when(entityManager.find(File.class, 1L)).thenReturn(file);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> layerService.getFileLayer(1L)
        );

        assertEquals(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                exception.getStatusCode()
        );
    }
}
