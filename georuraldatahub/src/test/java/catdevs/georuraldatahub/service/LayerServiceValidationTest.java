package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.Layer;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.VersionRepository;
import catdevs.georuraldatahub.repository.LayerRepository;

class LayerServiceValidationTest {

    private final FileRepository files = mock(FileRepository.class);
    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final BucketService bucket = mock(BucketService.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final LayerRepository layers = mock(LayerRepository.class);

    private final LayerService service =
            new LayerService(files, datasets, bucket, versions, layers);

    private File file(Long id) {
        File file = new File();
        file.setId(id);
        file.setName("test.csv");
        file.setFormatFile("csv");
        file.setLocation("bruta/original.csv");
        return file;
    }

    private Version version(String location, VersionStatus status) {
        Version version = new Version();
        version.setLocation(location);
        version.setStatus(status);
        return version;
    }

    @Test
    void rejectsLayerWithUnsupportedFormat() throws Exception {
        File unsupportedFile = file(1L);
        unsupportedFile.setName("documento.pdf");
        unsupportedFile.setFormatFile("pdf");
        unsupportedFile.setLocation("documentos/documento.pdf");

        when(files.findById(1L)).thenReturn(Optional.of(unsupportedFile));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getFileLayer(1L)
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getStatusCode()
        );

        verifyNoInteractions(bucket);
        verifyNoInteractions(versions);
    }

    @Test
    void readsCsvFromFileLocation() throws Exception {
        File csvFile = file(1L);
        csvFile.setName("dados.csv");
        csvFile.setLocation("dados/dados.csv");

        when(files.findById(1L)).thenReturn(Optional.of(csvFile));

        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
                eq(1L), any()
        )).thenReturn(Optional.empty());

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(1L), any(), eq("validada/")))
                .thenReturn(Optional.of(
                        version("dados/dados.csv", VersionStatus.BRUTA)
                ));

        service.getFileLayer(1L);

        verify(bucket).readCsvAsGeoJson("dados/dados.csv");
        verify(bucket, never()).readGeoJson(anyString());
    }

    @Test
    void readsGeoJsonFromFileLocation() throws Exception {
        File geoJsonFile = file(2L);
        geoJsonFile.setName("mapa.geojson");
        geoJsonFile.setFormatFile("geojson");
        geoJsonFile.setLocation("mapas/mapa.geojson");

        when(files.findById(2L)).thenReturn(Optional.of(geoJsonFile));

        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
                eq(2L), any()
        )).thenReturn(Optional.empty());

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(2L), any(), eq("validada/")))
                .thenReturn(Optional.of(
                        version("mapas/mapa.geojson", VersionStatus.BRUTA)
                ));

        service.getFileLayer(2L);

        verify(bucket).readGeoJson("mapas/mapa.geojson");
        verify(bucket, never()).readCsvAsGeoJson(anyString());
    }

    @Test
    void supportsJsonFormat() throws Exception {
        File jsonFile = file(5L);
        jsonFile.setName("dados.json");
        jsonFile.setFormatFile("json");
        jsonFile.setLocation("dados/dados.json");

        when(files.findById(5L)).thenReturn(Optional.of(jsonFile));

        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
                eq(5L), any()
        )).thenReturn(Optional.empty());

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(5L), any(), eq("validada/")))
                .thenReturn(Optional.of(
                        version("dados/dados.json", VersionStatus.BRUTA)
                ));

        service.getFileLayer(5L);

        verify(bucket).readGeoJson("dados/dados.json");
        verify(bucket, never()).readCsvAsGeoJson(anyString());
    }

    @Test
    void readsFilteredVersionInsteadOfOriginal() throws Exception {
        when(files.findById(1L)).thenReturn(Optional.of(file(1L)));

        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
                eq(1L), any()
        )).thenReturn(Optional.empty());

        Version filteredVersion = version(
                "validada/filtered.csv",
                VersionStatus.TRATADA
        );

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(1L),
                        argThat(statuses ->
                                statuses.contains(VersionStatus.TRATADA)),
                        eq("validada/")
                ))
                .thenReturn(Optional.of(filteredVersion));

        service.getFileLayer(1L);

        verify(bucket).readCsvAsGeoJson("validada/filtered.csv");
        verify(bucket, never()).readGeoJson(anyString());
    }

    @Test
    void usesTreatedGeoJsonInsteadOfCsvInputWhenAResultExists()
            throws Exception {

        when(files.findById(1L)).thenReturn(Optional.of(file(1L)));

        Version treatedVersion = version(
                "tratada/fontes/4/1/execucao-20.geojson",
                VersionStatus.TRATADA
        );

        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
                eq(1L), any()
        )).thenReturn(Optional.of(treatedVersion));

        service.getFileLayer(1L);

        verify(bucket).readGeoJson(treatedVersion.getLocation());
        verify(bucket, never()).readCsvAsGeoJson(anyString());

        verify(versions, never())
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        any(), any(), anyString()
                );
    }

    @Test
    void listsOnlyRenderableFiles() {
        File csvFile = file(1L);
        csvFile.setName("dados.csv");
        csvFile.setLocation("dados/dados.csv");

        File pdfFile = file(2L);
        pdfFile.setName("relatorio.pdf");
        pdfFile.setFormatFile("pdf");
        pdfFile.setLocation("documentos/relatorio.pdf");

        File geoJsonFile = file(3L);
        geoJsonFile.setName("mapa.geojson");
        geoJsonFile.setFormatFile("geojson");
        geoJsonFile.setLocation("mapas/mapa.geojson");

        when(datasets.existsById(4L)).thenReturn(true);
        when(files.findByDatasetId(4L))
                .thenReturn(List.of(csvFile, pdfFile, geoJsonFile));

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(1L), any(), eq("validada/")))
                .thenReturn(Optional.of(
                        version("validada/dados.csv", VersionStatus.BRUTA)
                ));

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(3L), any(), eq("validada/")))
                .thenReturn(Optional.of(
                        version("validada/mapa.geojson", VersionStatus.BRUTA)
                ));

        List<LayerInfoDTO> result = service.listLayers(4L);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(layer -> layer.fileId().equals(1L)));
        assertTrue(result.stream().anyMatch(layer -> layer.fileId().equals(3L)));
        assertFalse(result.stream().anyMatch(layer -> layer.fileId().equals(2L)));

        verify(versions, never())
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(2L), any(), anyString()
                );
    }

    @Test
    void listsOnlyApprovedFiles() {
        when(datasets.existsById(4L)).thenReturn(true);
        when(files.findByDatasetId(4L))
                .thenReturn(List.of(file(1L), file(2L)));

        Version approvedVersion = version(
                "validada/filtered.geojson",
                VersionStatus.TRATADA
        );

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(1L),
                        argThat(statuses ->
                                statuses.contains(VersionStatus.TRATADA)),
                        eq("validada/")
                ))
                .thenReturn(Optional.of(approvedVersion));

        when(versions
                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        eq(2L),
                        argThat(statuses ->
                                statuses.contains(VersionStatus.TRATADA)),
                        eq("validada/")
                ))
                .thenReturn(Optional.empty());

        assertEquals(1, service.listLayers(4L).size());
    }

    @Test
    void historicalLayerReadsItsOwnVersionInsteadOfLatestTreatmentOfFile()
            throws Exception {

        Version historical = version(
                "tratada/fontes/4/1/execucao-10.geojson",
                VersionStatus.TRATADA
        );
        historical.setId(10L);

        Layer layer = new Layer();
        layer.setId(5L);
        layer.setVersion(historical);

        when(layers.findById(5L)).thenReturn(Optional.of(layer));

        service.getTreatedLayer(5L);

        verify(bucket).readGeoJson(historical.getLocation());
        verifyNoInteractions(files, versions, datasets);
    }

    @Test
    void refusesUnapprovedAndMissingLayerBeforeReadingStorage() {
        Version version = version(
                "bruta/original.csv",
                VersionStatus.BRUTA
        );

        Layer layer = new Layer();
        layer.setVersion(version);

        when(layers.findById(5L)).thenReturn(Optional.of(layer));

        ResponseStatusException unapprovedException = assertThrows(
                ResponseStatusException.class,
                () -> service.getTreatedLayer(5L)
        );

        assertEquals(409, unapprovedException.getStatusCode().value());

        ResponseStatusException missingException = assertThrows(
                ResponseStatusException.class,
                () -> service.getTreatedLayer(99L)
        );

        assertEquals(404, missingException.getStatusCode().value());

        verifyNoInteractions(bucket);
    }
}