package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.entity.File;

class LayerServiceValidationTest {

    private final FileService fileService = mock(FileService.class);
    private final BucketService bucketService = mock(BucketService.class);

    private final LayerService service =
            new LayerService(fileService, bucketService);

    private File file(
            Long id,
            String name,
            String format,
            String location
    ) {
        File file = new File();

        file.setId(id);
        file.setName(name);
        file.setFormatFile(format);
        file.setLocation(location);

        return file;
    }

    @Test
    void rejectsLayerWithUnsupportedFormat() throws Exception {
        File unsupportedFile = file(
                1L,
                "documento.pdf",
                "pdf",
                "documentos/documento.pdf"
        );

        when(fileService.findEntityById(1L))
                .thenReturn(unsupportedFile);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getFileLayer(1L)
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        verifyNoInteractions(bucketService);
    }

    @Test
    void readsCsvFromFileLocation() throws Exception {
        File csvFile = file(
                1L,
                "dados.csv",
                "csv",
                "dados/dados.csv"
        );

        when(fileService.findEntityById(1L))
                .thenReturn(csvFile);

        service.getFileLayer(1L);

        verify(bucketService).readCsvAsGeoJson("dados/dados.csv");
        verify(bucketService, never()).readGeoJson(anyString());
    }

    @Test
    void readsGeoJsonFromFileLocation() throws Exception {
        File geoJsonFile = file(
                2L,
                "mapa.geojson",
                "geojson",
                "mapas/mapa.geojson"
        );

        when(fileService.findEntityById(2L))
                .thenReturn(geoJsonFile);

        service.getFileLayer(2L);

        verify(bucketService).readGeoJson("mapas/mapa.geojson");
        verify(bucketService, never()).readCsvAsGeoJson(anyString());
    }

    @Test
    void listsOnlyRenderableFiles() {
        File csvFile = file(
                1L,
                "dados.csv",
                "csv",
                "dados/dados.csv"
        );

        File pdfFile = file(
                2L,
                "relatorio.pdf",
                "pdf",
                "documentos/relatorio.pdf"
        );

        File geoJsonFile = file(
                3L,
                "mapa.geojson",
                "geojson",
                "mapas/mapa.geojson"
        );

        when(fileService.findEntitiesByDatasetId(4L))
                .thenReturn(List.of(csvFile, pdfFile, geoJsonFile));

        List<LayerInfoDTO> result = service.listLayers(4L);

        assertEquals(2, result.size());
        assertTrue(
                result.stream().anyMatch(layer -> layer.fileId().equals(1L))
        );
        assertTrue(
                result.stream().anyMatch(layer -> layer.fileId().equals(3L))
        );
        assertFalse(
                result.stream().anyMatch(layer -> layer.fileId().equals(2L))
        );
    }

    @Test
    void supportsJsonFormat() throws Exception {
        File jsonFile = file(
                5L,
                "dados.json",
                "json",
                "dados/dados.json"
        );

        when(fileService.findEntityById(5L))
                .thenReturn(jsonFile);

        service.getFileLayer(5L);

        verify(bucketService).readGeoJson("dados/dados.json");
    }
}