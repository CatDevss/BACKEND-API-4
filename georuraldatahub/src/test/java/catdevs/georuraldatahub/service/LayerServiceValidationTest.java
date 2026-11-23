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
    @Test void readsDictionaryApprovedVersionInsteadOfOriginal() throws Exception {
        when(files.findById(1L)).thenReturn(Optional.of(file(1L)));
        Version version = new Version(); version.setLocation("tratada/approved.csv");
        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(eq(1L), argThat(statuses -> statuses.contains(VersionStatus.TRATADA) && !statuses.contains(VersionStatus.BRUTA)))).thenReturn(Optional.of(version));
        service.getFileLayer(1L);
        verify(bucket).readCsvAsGeoJson("tratada/approved.csv");
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
    }
    @Test void listsOnlyApprovedFiles() {
        when(datasets.existsById(4L)).thenReturn(true);
        when(files.findByDatasetId(4L)).thenReturn(List.of(file(1L), file(2L)));
        when(versions.findFileIdsByDatasetAndStatusIn(4L, Set.of(VersionStatus.TRATADA, VersionStatus.PUBLICADA)))
                .thenReturn(Set.of(1L));
        assertEquals(1, service.listLayers(4L).size());
    }

    @Test void listsManyFilesWithBulkQueriesAndKeepsOrderAndSupportedFormats() {
        when(datasets.existsById(4L)).thenReturn(true);
        var associated = java.util.stream.LongStream.rangeClosed(1, 1200)
                .mapToObj(this::file).toList();
        associated.getFirst().setFormatFile("GEOJSON");
        associated.get(1).setFormatFile("tif");
        associated.get(2).setFormatFile(null);
        when(files.findByDatasetId(4L)).thenReturn(associated);
        when(versions.findFileIdsByDatasetAndStatusIn(4L, Set.of(VersionStatus.TRATADA, VersionStatus.PUBLICADA)))
                .thenReturn(Set.of(1200L, 3L, 2L, 1L));
        assertEquals(List.of(1L, 1200L), service.listLayers(4L).stream().map(l -> l.fileId()).toList());
        verify(versions).findFileIdsByDatasetAndStatusIn(4L, Set.of(VersionStatus.TRATADA, VersionStatus.PUBLICADA));
        verify(versions).findLegacyValidatedStagesByDataset(4L, VersionStatus.BRUTA);
        verifyNoMoreInteractions(versions);
        verifyNoInteractions(bucket);
    }

    @Test void skipsApprovalQueryWhenThereAreNoRenderableFiles() {
        when(datasets.existsById(4L)).thenReturn(true);
        when(files.findByDatasetId(4L)).thenReturn(List.of());
        assertTrue(service.listLayers(4L).isEmpty());
        var raster = file(1L); raster.setFormatFile("tiff");
        when(files.findByDatasetId(4L)).thenReturn(List.of(raster));
        assertTrue(service.listLayers(4L).isEmpty());
        verifyNoInteractions(versions, bucket);
    }

    @Test void missingDatasetStopsBeforeQueryingFilesOrApprovals() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.listLayers(99L)).getStatusCode().value());
        verifyNoInteractions(files, versions, bucket);
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

    private File legacyFile(long id, String format) {
        var file = file(id);
        file.setFormatFile(format);
        file.setLocation("bruta/fontes/10/hash_arquivo." + format);
        var dictionary = new catdevs.georuraldatahub.entity.Dictionary();
        dictionary.setId(11L); dictionary.setVersion(3);
        var ingestion = new DataIngestion(); ingestion.setDictionary(dictionary);
        file.setIngestion(ingestion);
        return file;
    }

    private Version legacyVersion(File file) {
        var version = new Version(); version.setFile(file); version.setVersionNumber(2);
        version.setStatus(VersionStatus.BRUTA);
        version.setLocation(file.getLocation().replaceFirst("bruta/", "validada/")
                + (Set.of("zip", "gpkg").contains(file.getFormatFile()) ? ".geojson" : ""));
        return version;
    }

    private static final String VALID_REPORT = """
        {"status":"VALIDO","dictionaryId":11,"dictionaryVersion":3,"validRecords":273,"rejectedRecords":0}
        """;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"csv", "geojson", "json", "zip", "gpkg"})
    void listsAndReadsLegacyPreviewOnlyAfterConfirmingTheReport(String format) throws Exception {
        var file = legacyFile(4L, format);
        var version = legacyVersion(file);
        when(datasets.existsById(1L)).thenReturn(true);
        when(files.findByDatasetId(1L)).thenReturn(List.of(file));
        when(files.findById(4L)).thenReturn(Optional.of(file));
        when(versions.findFirstByFile_IdOrderByVersionNumberDesc(4L)).thenReturn(Optional.of(version));
        when(versions.findLegacyValidatedStagesByDataset(1L, VersionStatus.BRUTA)).thenReturn(List.of(
                new catdevs.georuraldatahub.dto.FileStageDTO(4L, VersionStatus.BRUTA, 2, version.getLocation())));
        when(bucket.readValidationReport(file.getLocation())).thenReturn(VALID_REPORT);

        assertEquals(List.of(4L), service.listLayers(1L).stream().map(l -> l.fileId()).toList());
        service.getFileLayer(4L);
        if (format.equals("csv")) verify(bucket).readCsvAsGeoJson(version.getLocation());
        else verify(bucket).readGeoJson(version.getLocation());
        verify(bucket, never()).readGeoJson(file.getLocation());
        verify(bucket, never()).readCsvAsGeoJson(file.getLocation());
        verify(versions, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{}", "null", "invalid json",
        "{\"status\":\"PARCIAL\",\"validRecords\":273,\"rejectedRecords\":1}",
        "{\"status\":\"REJEITADO\",\"validRecords\":0,\"rejectedRecords\":273}",
        "{\"status\":\"VALIDO\",\"dictionaryId\":11,\"dictionaryVersion\":3,\"validRecords\":0,\"rejectedRecords\":0}",
        "{\"status\":\"VALIDO\",\"dictionaryId\":11,\"dictionaryVersion\":3,\"validRecords\":273,\"rejectedRecords\":1}",
        "{\"status\":\"VALIDO\",\"dictionaryId\":11,\"dictionaryVersion\":3,\"validRecords\":\"273\",\"rejectedRecords\":0}",
        "{\"status\":\"VALIDO\",\"dictionaryId\":12,\"dictionaryVersion\":3,\"validRecords\":273,\"rejectedRecords\":0}",
        "{\"status\":\"VALIDO\",\"dictionaryId\":11,\"dictionaryVersion\":4,\"validRecords\":273,\"rejectedRecords\":0}"
    })
    void refusesUnconfirmedLegacyReportsForListingAndReading(String report) throws Exception {
        var file = legacyFile(4L, "csv");
        var version = legacyVersion(file);
        when(files.findById(4L)).thenReturn(Optional.of(file));
        when(versions.findFirstByFile_IdOrderByVersionNumberDesc(4L)).thenReturn(Optional.of(version));
        when(versions.findLegacyValidatedStagesByDataset(1L, VersionStatus.BRUTA)).thenReturn(List.of(
                new catdevs.georuraldatahub.dto.FileStageDTO(4L, VersionStatus.BRUTA, 2, version.getLocation())));
        when(bucket.readValidationReport(file.getLocation())).thenReturn(report);
        assertTrue(service.listLayers(1L, List.of(file)).isEmpty());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.getFileLayer(4L)).getStatusCode().value());
        verify(bucket, never()).readCsvAsGeoJson(any());
        verify(bucket, never()).readGeoJson(any());
    }

    @Test void missingLegacyReportDoesNotBreakOtherWorkspaceLayers() throws Exception {
        var file = legacyFile(4L, "csv");
        when(versions.findFileIdsByDatasetAndStatusIn(eq(1L), any())).thenReturn(Set.of(5L));
        when(versions.findLegacyValidatedStagesByDataset(1L, VersionStatus.BRUTA)).thenReturn(List.of(
                new catdevs.georuraldatahub.dto.FileStageDTO(4L, VersionStatus.BRUTA, 2, legacyVersion(file).getLocation())));
        when(bucket.readValidationReport(file.getLocation())).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertEquals(List.of(5L), service.listLayers(1L, List.of(file, file(5L)))
                .stream().map(l -> l.fileId()).toList());
    }

    @Test void currentApprovedVersionTakesPrecedenceWithoutReadingLegacyReport() throws Exception {
        var file = legacyFile(4L, "csv");
        var treated = new Version(); treated.setLocation("tratada/latest.geojson");
        when(files.findById(4L)).thenReturn(Optional.of(file));
        when(versions.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(eq(4L), any()))
                .thenReturn(Optional.of(treated));
        when(versions.findFileIdsByDatasetAndStatusIn(eq(1L), any())).thenReturn(Set.of(4L));
        assertEquals(1, service.listLayers(1L, List.of(file)).size());
        service.getFileLayer(4L);
        verify(bucket).readGeoJson("tratada/latest.geojson");
        verify(bucket, never()).readValidationReport(any());
        verify(versions, never()).findLegacyValidatedStagesByDataset(any(), any());
        verify(versions, never()).findFirstByFile_IdOrderByVersionNumberDesc(any());
    }

    @Test void refusesRawUnrelatedOrSupersededCopiesWithoutReadingStorage() {
        var file = legacyFile(4L, "csv");
        when(files.findById(4L)).thenReturn(Optional.of(file));
        for (String location : List.of(file.getLocation(), "validada/another-file.csv", "quarentena/rejected.csv")) {
            var version = legacyVersion(file); version.setLocation(location);
            when(versions.findFirstByFile_IdOrderByVersionNumberDesc(4L)).thenReturn(Optional.of(version));
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> service.getFileLayer(4L)).getStatusCode().value());
        }
        var version = legacyVersion(file); version.setStatus(VersionStatus.QUARENTENA);
        when(versions.findFirstByFile_IdOrderByVersionNumberDesc(4L)).thenReturn(Optional.of(version));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.getFileLayer(4L)).getStatusCode().value());
        version.setStatus(VersionStatus.BRUTA); version.setVersionNumber(1);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.getFileLayer(4L)).getStatusCode().value());
        verifyNoInteractions(bucket);
    }
}
