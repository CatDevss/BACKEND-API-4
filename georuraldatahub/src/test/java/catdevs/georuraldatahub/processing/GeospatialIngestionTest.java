package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.service.*;
import catdevs.georuraldatahub.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GeospatialIngestionTest {
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final DatasetService datasets = mock(DatasetService.class);
    private final UserService users = mock(UserService.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final BucketService bucket = mock(BucketService.class);
    private final QuarantineRepository quarantine = mock(QuarantineRepository.class);
    private final Dictionary dictionary = new Dictionary();
    private final FileService service = new FileService(files, versions, ingestions, datasets, users, dictionaries, bucket,
            new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator()), quarantine, new GeospatialReader(new ProcessingProperties()));
    @BeforeEach void setup() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        dictionary.setId(3L); dictionary.setVersion(1);
        when(dictionaries.acquireForValidation(4L, null)).thenReturn(dictionary);
        var user = new User(); user.setId(2L); when(users.findEntityById(2L)).thenReturn(user);
        when(ingestions.save(any())).thenAnswer(i -> { DataIngestion entity = i.getArgument(0); entity.setId(5L); return entity; });
        when(files.save(any())).thenAnswer(i -> { File entity = i.getArgument(0); entity.setId(6L); return entity; });
        when(bucket.uploadToSourceRawZone(any(), any(), any(), any())).thenReturn("bruta/fontes/4/original");
        when(bucket.uploadValidationReport(any(), any())).thenReturn("bruta/fontes/4/original.validation.json");
        when(bucket.uploadSourceTreatedContent(any(), any(), any(), any())).thenReturn("tratada/fontes/4/result");
        when(bucket.uploadCollectionArtifact(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }
    private MockMultipartFile file(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/processing/" + name)) {
            return new MockMultipartFile("file", name, "application/octet-stream", input);
        }
    }
    @Test void geoPackageUsesDictionaryAndKeepsOriginalAndValidatedGeoJsonWithoutDataset() throws Exception {
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("codarea", FieldType.STRING)));
        var response = service.uploadToSource(file("parana-utm.gpkg"), 4L, 2L, null);
        assertEquals("VALIDO", response.validation().status());
        assertEquals("gpkg", response.formatFile());
        verify(bucket).uploadSourceTreatedContent(any(), eq(4L), any(), eq("parana-utm.gpkg.geojson"));
        verify(bucket).uploadCollectionArtifact(eq("bruta/fontes/4/original.geospatial.json"), any(), eq("application/json"));
        verifyNoInteractions(datasets, quarantine);
    }
    @Test void projectedGeoJsonWithExplicitCrsIsReprojectedBeforeDictionaryValidation() throws Exception {
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("codarea", FieldType.STRING)));
        byte[] data = """
            {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"EPSG:31982"}},
             "features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[700000,7200000]},"properties":{"codarea":"4106902"}}]}
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "projetado.geojson", "application/geo+json", data);
        var response = service.uploadToSource(file, 4L, 2L, null);
        assertEquals("VALIDO", response.validation().status());
        verify(bucket).uploadSourceTreatedContent(any(), eq(4L), any(), eq("projetado.geojson"));
        var metadata = ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadCollectionArtifact(eq("bruta/fontes/4/original.geospatial.json"), metadata.capture(), any());
        assertEquals(31982, new com.fasterxml.jackson.databind.ObjectMapper().readTree(metadata.getValue()).path("sourceSrid").asInt());
    }
    @Test void ibgeShapefileCollectionHasAnExplicitDatumConversionInsteadOfRelabeling() throws Exception {
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("codarea", FieldType.STRING)));
        var metadata = new catdevs.georuraldatahub.collection.CollectedData.Metadata("IBGE_BIOMAS", "2019", "geojson", "shapefile.zip",
                "Paraná", "2026-10-25T00:00:00Z", "https://ibge.gov.br", List.of(), List.of());
        byte[] content = """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-51,-25]},"properties":{"codarea":"4106902"}}]}
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var data = new catdevs.georuraldatahub.collection.CollectedData("bioma.geojson", "geojson", content, List.of(), metadata);
        assertEquals("VALIDO", service.uploadCollectedFile(data, null, 2L, 4L, null, "IBGE_BIOMAS").validation().status());
        var captured = ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadCollectionArtifact(eq("bruta/fontes/4/original.geospatial.json"), captured.capture(), any());
        assertEquals(4674, new com.fasterxml.jackson.databind.ObjectMapper().readTree(captured.getValue()).path("sourceSrid").asInt());
    }
    @Test void rasterValidatesMetadataAndStoresManifestInsteadOfCellFeatures() throws Exception {
        var rules = new ArrayList<FieldRule>();
        for (String field : List.of("sourceSrid", "width", "height", "bandCount")) rules.add(FieldRule.required(field, FieldType.INTEGER));
        for (String field : List.of("pixelWidth", "pixelHeight")) rules.add(FieldRule.required(field, FieldType.NUMBER));
        rules.add(FieldRule.required("pixelUnit", FieldType.STRING)); rules.add(FieldRule.required("dataTypes", FieldType.STRING));
        rules.add(FieldRule.optional("noDataValues", FieldType.STRING)); rules.add(FieldRule.required("footprint", FieldType.GEOMETRY));
        when(dictionaries.rules(dictionary)).thenReturn(rules);
        var response = service.uploadToSource(file("parana-utm.tif"), 4L, 2L, null);
        assertEquals("VALIDO", response.validation().status()); assertEquals(1, response.validation().validRecords());
        var content = ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadSourceTreatedContent(content.capture(), eq(4L), any(), eq("parana-utm.tif.raster.json"));
        var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content.getValue());
        assertEquals("RASTER", manifest.path("kind").asText()); assertEquals(31982, manifest.path("sourceSrid").asInt());
        assertFalse(manifest.has("features")); verifyNoInteractions(datasets, quarantine);
        verify(bucket).copyWorkingObject("bruta/fontes/4/original", "tratada/fontes/4/original");
        var captured = ArgumentCaptor.forClass(Version.class); verify(versions, times(2)).save(captured.capture());
        assertEquals("tratada/fontes/4/original", captured.getValue().getLocation());
        assertEquals("bruta/fontes/4/original", captured.getValue().getFile().getLocation());
    }
    @Test void corruptRasterIsPreservedInRawZoneAndQuarantinedWithoutValidatedVersion() throws Exception {
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("width", FieldType.INTEGER)));
        var corrupt = new MockMultipartFile("file", "corrompido.tif", "image/tiff", "not-a-tiff".getBytes());
        var response = service.uploadToSource(corrupt, 4L, 2L, null);
        assertEquals("REJEITADO", response.validation().status());
        assertTrue(response.validation().records().getFirst().errors().getFirst().startsWith("INVALID_GEOSPATIAL_FILE"));
        verify(bucket).uploadToSourceRawZone(eq(corrupt), eq(4L), any(), any());
        verify(bucket, never()).uploadSourceTreatedContent(any(), any(), any(), any());
        verify(quarantine).save(argThat(q -> "ARQUIVO_REJEITADO".equals(q.getStatus())));
        verify(bucket).uploadToQuarantineZone(same(corrupt), eq("bruta/fontes/4/original"));
        verify(bucket).uploadValidationReport(eq("quarentena/fontes/4/original"), contains("INVALID_GEOSPATIAL_FILE"));
    }
}
