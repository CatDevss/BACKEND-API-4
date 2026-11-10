package catdevs.georuraldatahub.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.service.*;
import catdevs.georuraldatahub.validation.*;

/** Exercises multipart -> real validation/service -> response; only database/OCI dependencies are mocked. */
class ManualJsonUploadTest {
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final BucketService bucket = mock(BucketService.class);
    private final UserService users = mock(UserService.class);
    private final DatasetService datasets = mock(DatasetService.class);
    private MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        var service = new FileService(files, versions, ingestions, datasets, users, dictionaries, bucket,
                new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator()), mock(QuarantineRepository.class),
                mock(catdevs.georuraldatahub.processing.GeospatialReader.class));
        mvc = MockMvcBuilders.standaloneSetup(new SourceFileController(service, mock(SourceService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user = new User(); user.setId(2L); when(users.findEntityById(2L)).thenReturn(user);
        var dictionary = new Dictionary(); dictionary.setId(3L); dictionary.setVersion(1);
        when(dictionaries.acquireForValidation(4L, null)).thenReturn(dictionary);
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("id", FieldType.INTEGER)));
        when(ingestions.save(any())).thenAnswer(inv -> { DataIngestion i = inv.getArgument(0); i.setId(5L); return i; });
        when(files.save(any())).thenAnswer(inv -> { File f = inv.getArgument(0); f.setId(6L); return f; });
        when(bucket.uploadToSourceRawZone(any(), eq(4L), any(), any())).thenReturn("bruta/fontes/4/test.JSON");
        when(bucket.uploadSourceTreatedContent(any(), eq(4L), any(), any())).thenReturn("tratada/fontes/4/test.JSON");
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void manualJsonMultipartIsAcceptedWithoutDatasetIncludingUppercaseExtension() throws Exception {
        mvc.perform(multipart("/fontes/4/arquivos").param("userId", "2")
                .file(new MockMultipartFile("file", "test.JSON", "application/json", "[{\"id\":1}]".getBytes())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.formatFile").value("json"))
                .andExpect(jsonPath("$.validation.status").value("VALIDO"))
                .andExpect(jsonPath("$.validation.validRecords").value(1));
        verifyNoInteractions(datasets);
        var content = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadSourceTreatedContent(content.capture(), eq(4L), any(), any());
        assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().readTree(content.getValue()).isArray());
    }
    @Test void manualGeoJsonPreservesAllDeclaredPropertiesAndGeometry() throws Exception {
        when(dictionaries.rules(any())).thenReturn(List.of(
                FieldRule.required("id", FieldType.INTEGER),
                FieldRule.required("NM_MUN", FieldType.STRING),
                FieldRule.required("AREA_KM2", FieldType.NUMBER)));
        String input = """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                "properties":{"id":1,"NM_MUN":"Curitiba","AREA_KM2":435.277},
                "geometry":{"type":"Point","coordinates":[-49.27,-25.43]}}]}
                """;
        mvc.perform(multipart("/fontes/4/arquivos").param("userId", "2")
                .file(new MockMultipartFile("file", "municipio.geojson", "application/geo+json", input.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validation.status").value("VALIDO"));
        var content = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadSourceTreatedContent(content.capture(), eq(4L), any(), any());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(mapper.readTree(input), mapper.readTree(content.getValue()));
        verifyNoInteractions(datasets);
    }
    @Test void selectedDictionaryIsUsedForValidationAndRecordedInIngestion() throws Exception {
        var selected = new Dictionary(); selected.setId(77L); selected.setVersion(1);
        when(dictionaries.acquireForValidation(4L, 77L)).thenReturn(selected);
        when(dictionaries.rules(selected)).thenReturn(List.of(FieldRule.required("id", FieldType.INTEGER)));
        mvc.perform(multipart("/fontes/4/arquivos").param("userId", "2").param("dictionaryId", "77")
                .file(new MockMultipartFile("file", "test.json", "application/json", "[{\"id\":1}]".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validation.status").value("VALIDO"))
                .andExpect(jsonPath("$.validation.dictionaryId").value(77))
                .andExpect(jsonPath("$.validation.dictionaryVersion").value(1));
        var ingestion = org.mockito.ArgumentCaptor.forClass(DataIngestion.class);
        verify(ingestions).save(ingestion.capture());
        assertSame(selected, ingestion.getValue().getDictionary());
        verify(dictionaries, never()).acquireForValidation(4L, null);
    }

    @Test void explicitStatusReasonReachesClientInsteadOfEmptyErrorBody() throws Exception {
        when(dictionaries.acquireForValidation(4L, null)).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Fonte demorou a responder."));
        mvc.perform(multipart("/fontes/4/arquivos").param("userId", "2")
                .file(new MockMultipartFile("file", "test.json", "application/json", "{\"id\":1}".getBytes())))
                .andExpect(status().isGatewayTimeout()).andExpect(content().string("Fonte demorou a responder."));
    }
}
