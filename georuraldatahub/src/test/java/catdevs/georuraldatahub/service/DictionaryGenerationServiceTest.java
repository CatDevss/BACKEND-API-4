package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.collection.*;
import catdevs.georuraldatahub.dto.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.enums.UserType;
import catdevs.georuraldatahub.processing.*;
import catdevs.georuraldatahub.validation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DictionaryGenerationServiceTest {
    private final SourceService sources = mock(SourceService.class);
    private final UserService users = mock(UserService.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final PublicDataConnectors connectors = mock(PublicDataConnectors.class);
    private final GeospatialReader geospatial = mock(GeospatialReader.class);
    private final ProcessingProperties limits = new ProcessingProperties();
    private final FileService files = mock(FileService.class);
    private final DictionaryGenerationService service = new DictionaryGenerationService(sources, users, dictionaries, connectors,
            geospatial, limits, new DictionaryInference(), new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator()), files);
    private User manager;
    @BeforeEach void setup() throws Exception {
        manager = new User(); manager.setId(7L); manager.setUserStatus(1); manager.setUserType(UserType.GESTOR);
        when(users.findEntityById(7L)).thenReturn(manager);
        var source = new Source(); source.setId(2L); source.setStatus(1); source.setUrl("https://www.ibge.gov.br/");
        when(sources.findEntityById(2L)).thenReturn(source);
        when(files.calculateHash(any())).thenReturn("a".repeat(64));
        when(dictionaries.create(eq(2L), any())).thenAnswer(call -> {
            DictionaryRequestDTO request = call.getArgument(1);
            return new DictionaryResponseDTO(99L, 2L, request.name(), request.description(), 3, "RASCUNHO", request.rules(), null);
        });
    }
    private MockMultipartFile csv(String content) { return new MockMultipartFile("file", "malha_2022.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)); }
    @Test void generatesAndSavesANewDictionaryWithoutRequiringAnExistingOneOrIngesting() throws Exception {
        manager.setUserType(UserType.OPERADOR);
        var result = service.fromUpload(2L, 7L, csv("codigo,nome\n001,Curitiba\n002,Londrina"));
        assertEquals(99L, result.dictionary().id());
        assertEquals("VALIDO", result.validation().status());
        assertEquals(2, result.validation().validRecords());
        assertTrue(result.dictionary().description().contains("a".repeat(64)));
        verify(dictionaries).create(eq(2L), any()); verifyNoMoreInteractions(dictionaries);
        verify(files).calculateHash(any()); verifyNoMoreInteractions(files);
    }
    @Test void duplicateRecordsRemainRejectedInsteadOfRelaxingRulesToApproveThem() throws Exception {
        var result = service.fromUpload(2L, 7L, csv("id\n1\n1"));
        assertEquals("REJEITADO", result.validation().status());
        assertEquals(1, result.validation().rejectedRecords());
        assertTrue(result.validation().issues().getFirst().errors().contains("DUPLICATE_RECORD"));
        verify(dictionaries).create(eq(2L), any());
    }
    @Test void collectionDownloadsTheRequestedEditionWithoutLookingUpADictionary() throws Exception {
        var request = new CollectionRequest(CollectionRequest.Product.IBGE_MUNICIPIOS, null, 7L, "2022", "4106902", null, null);
        when(connectors.collect(request)).thenReturn(new CollectedData("malha_2022.csv", "csv", "codigo\n4106902".getBytes(StandardCharsets.UTF_8), List.of(), null));
        var result = service.fromCollection(2L, request);
        assertEquals("VALIDO", result.validation().status());
        verify(connectors).collect(request);
        verify(dictionaries).create(eq(2L), any()); verifyNoMoreInteractions(dictionaries);
    }
    @Test void malformedEmptyOversizedAndUnsupportedFilesDoNotSaveDictionaries() {
        assertThrows(IllegalArgumentException.class, () -> service.fromUpload(2L, 7L, csv("")));
        assertThrows(IllegalArgumentException.class, () -> service.fromUpload(2L, 7L, csv("id,id\n1,2")));
        assertThrows(IllegalArgumentException.class, () -> service.fromUpload(2L, 7L, new MockMultipartFile("file", "a.exe", "text/plain", new byte[]{1})));
        limits.setMaxOutputBytes(4);
        assertThrows(IllegalArgumentException.class, () -> service.fromUpload(2L, 7L, csv("id\n12345")));
        verifyNoInteractions(dictionaries);
    }
    @Test void rejectsAuditorsAndMismatchedSourcesBeforeDownloadingOrSaving() {
        manager.setUserType(UserType.AUDITOR);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.fromUpload(2L, 7L, csv("id\n1"))).getStatusCode().value());
        manager.setUserType(UserType.GESTOR);
        assertThrows(IllegalArgumentException.class, () -> service.fromCollection(2L,
                new CollectionRequest(CollectionRequest.Product.MMA, null, 7L, "ATUAL", null, null, null)));
        verifyNoInteractions(connectors, dictionaries);
    }
    @Test void convertsBinaryVectorUsingTheSamePreparationAsIngestion() throws Exception {
        var content = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"id\":1},\"geometry\":{\"type\":\"Point\",\"coordinates\":[-49,-25]}}]}".getBytes(StandardCharsets.UTF_8);
        when(geospatial.read(any(), eq("zip"), eq(0))).thenReturn(new GeospatialReader.Result(content, null));
        var result = service.fromUpload(2L, 7L, new MockMultipartFile("file", "malha.zip", "application/zip", new byte[]{1}));
        assertEquals("VALIDO", result.validation().status());
        assertEquals(FieldType.GEOMETRY, result.dictionary().rules().getLast().type());
    }
    @Test void realShapefileGeoPackageAndRasterMetadataUseTheirOwnObservedFields() throws Exception {
        var real = new DictionaryGenerationService(sources, users, dictionaries, connectors,
                new GeospatialReader(limits), limits, new DictionaryInference(),
                new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator()), files);
        for (String name : List.of("parana-utm-shapefile.zip", "parana-utm.gpkg", "parana-utm.tif")) {
            try (var input = getClass().getResourceAsStream("/processing/" + name)) {
                var result = real.fromUpload(2L, 7L, new MockMultipartFile("file", name, "application/octet-stream", input));
                assertEquals("VALIDO", result.validation().status(), name);
                var names = result.dictionary().rules().stream().map(FieldRule::name).toList();
                assertTrue(names.contains(name.endsWith("tif") ? "footprint" : "geometry"), name);
                assertFalse(names.contains("CD_MUN"), "Não reutiliza campos fixos de um modelo de outro arquivo.");
            }
        }
    }
    @Test void collectionUsesOtherAgenciesAndTheirOwnFields() throws Exception {
        var source = new Source(); source.setId(2L); source.setStatus(1); source.setUrl("https://www.inpe.br/");
        when(sources.findEntityById(2L)).thenReturn(source);
        var request = new CollectionRequest(CollectionRequest.Product.INPE_QUEIMADAS, null, 7L, "2026-10-01", null, null, null);
        when(connectors.collect(request)).thenReturn(new CollectedData("focos.csv", "csv", "satelite,latitude,longitude\nA,-25,-49".getBytes(StandardCharsets.UTF_8), List.of(), null));
        var result = service.fromCollection(2L, request);
        assertEquals(List.of("satelite", "latitude", "longitude"), result.dictionary().rules().stream().map(FieldRule::name).toList());
        assertEquals("VALIDO", result.validation().status());
    }
    @Test void upstreamFailureDoesNotSaveADictionary() throws Exception {
        when(connectors.collect(any())).thenThrow(new java.io.IOException("Indisponível"));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.fromCollection(2L,
                new CollectionRequest(CollectionRequest.Product.IBGE_BIOMAS, null, 7L, "2025", null, null, null))).getStatusCode().value());
        verifyNoInteractions(dictionaries);
    }
}
