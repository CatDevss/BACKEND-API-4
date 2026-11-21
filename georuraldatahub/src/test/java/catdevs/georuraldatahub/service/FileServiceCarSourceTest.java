package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.validation.*;

/** Fonte CAR não valida registro a registro contra o dicionário; as demais fontes continuam validando. */
class FileServiceCarSourceTest {
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final DatasetService datasets = mock(DatasetService.class);
    private final UserService users = mock(UserService.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final BucketService bucket = mock(BucketService.class);
    private final QuarantineRepository quarantine = mock(QuarantineRepository.class);
    private final FileValidationService validator = new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator());
    private final FileService service = new FileService(files, versions, ingestions, datasets, users, dictionaries, bucket, validator, quarantine, mock(catdevs.georuraldatahub.processing.GeospatialReader.class));

    @BeforeEach void setup() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        var user = new User(); user.setId(2L); when(users.findEntityById(2L)).thenReturn(user);
        when(bucket.uploadToSourceRawZone(any(), eq(4L), any(), any())).thenReturn("bruta/test.csv");
        when(bucket.uploadValidationReport(eq("bruta/test.csv"), any())).thenReturn("bruta/test.csv.validation.json");
        when(bucket.uploadSourceTreatedContent(any(), eq(4L), any(), any())).thenReturn("tratada/test.csv");
        when(ingestions.save(any())).thenAnswer(inv -> { DataIngestion i = inv.getArgument(0); i.setId(5L); return i; });
        when(files.save(any())).thenAnswer(inv -> { File f = inv.getArgument(0); f.setId(6L); return f; });
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    private void dictionaryOfSource(String sourceName) {
        var source = new Source(); source.setId(4L); source.setName(sourceName);
        var dictionary = new Dictionary(); dictionary.setId(3L); dictionary.setVersion(2); dictionary.setSource(source);
        when(dictionaries.acquireForValidation(4L, null)).thenReturn(dictionary);
        // Regra exigindo inteiro: a linha "x" do arquivo abaixo a violaria.
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("id", FieldType.INTEGER)));
    }
    private MockMultipartFile csvWithInvalidRecord() {
        return new MockMultipartFile("file", "test.csv", "text/csv", "id\n1\nx".getBytes(StandardCharsets.UTF_8));
    }

    @Test void carSourceSkipsDictionaryValidationAndGoesStraightToTreated() throws Exception {
        dictionaryOfSource("CAR");
        var response = service.uploadToSource(csvWithInvalidRecord(), 4L, 2L);
        assertEquals("VALIDO", response.validation().status());
        verify(bucket).uploadSourceTreatedContent(any(), eq(4L), any(), any());
        verify(quarantine, never()).save(any());
    }
    @Test void otherSourcesStillValidateAgainstTheDictionary() throws Exception {
        dictionaryOfSource("INPE");
        var response = service.uploadToSource(csvWithInvalidRecord(), 4L, 2L);
        assertEquals("REJEITADO", response.validation().status());
        verify(bucket, never()).uploadSourceTreatedContent(any(), any(), any(), any());
        verify(quarantine).save(any());
    }
}