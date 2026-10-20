package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.enums.*;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.validation.*;

class FileServiceValidationTest {
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final DatasetService datasets = mock(DatasetService.class);
    private final UserService users = mock(UserService.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final BucketService bucket = mock(BucketService.class);
    private final QuarantineRepository quarantine = mock(QuarantineRepository.class);
    private final FileValidationService validator = new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator());
    private final FileService service = new FileService(files, versions, ingestions, datasets, users, dictionaries, bucket, validator, quarantine);
    private final Dataset dataset = new Dataset();

    @BeforeEach void setup() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        dataset.setFiles(new ArrayList<>());
        when(datasets.findEntityById(1L)).thenReturn(dataset);
        var user = new User(); user.setId(2L); when(users.findEntityById(2L)).thenReturn(user);
        var dictionary = new Dictionary(); dictionary.setId(3L); dictionary.setVersion(2);
        when(dictionaries.acquireForValidation(4L)).thenReturn(dictionary);
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("id", FieldType.INTEGER)));
        when(bucket.uploadToRawZone(any(), eq(1L), any(), any())).thenReturn("bruta/test.csv");
        when(bucket.uploadValidationReport(eq("bruta/test.csv"), any())).thenReturn("bruta/test.csv.validation.json");
        when(bucket.uploadValidatedContent(any(), eq(1L), any(), any())).thenReturn("validada/test.csv");
        when(ingestions.save(any())).thenAnswer(inv -> { DataIngestion i = inv.getArgument(0); i.setId(5L); return i; });
        when(files.save(any())).thenAnswer(inv -> { File f = inv.getArgument(0); f.setId(6L); return f; });
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }
    private MockMultipartFile csv(String text) { return new MockMultipartFile("file", "test.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8)); }

    @Test void uploadsOnlyValidRowsAndPersistsPartialReport() throws Exception {
        var response = service.uploadFile(csv("id\n1\nx\n2"), 1L, 2L, 4L);
        assertEquals("PARCIAL", response.validation().status()); assertEquals(2, response.validation().validRecords());
        assertEquals(3L, response.validation().dictionaryId()); assertEquals(2, response.validation().dictionaryVersion());
        var accepted = ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadValidatedContent(accepted.capture(), eq(1L), any(), any());
        assertFalse(new String(accepted.getValue(), StandardCharsets.UTF_8).contains("x"));
        var capturedVersions = ArgumentCaptor.forClass(Version.class);
        verify(versions, times(2)).save(capturedVersions.capture());
        assertEquals(List.of(VersionStatus.BRUTA, VersionStatus.BRUTA), capturedVersions.getAllValues().stream().map(Version::getStatus).toList());
        var q = ArgumentCaptor.forClass(Quarantine.class); verify(quarantine).save(q.capture());
        assertEquals("REGISTROS_REJEITADOS", q.getValue().getStatus()); assertTrue(q.getValue().getReason().contains("INVALID_TYPE"));
        assertEquals(IngestionStatus.CONCLUIDO, dataset.getFiles().getFirst().getIngestion().getIngStatus());
        var reportJson = ArgumentCaptor.forClass(String.class);
        verify(bucket).uploadValidationReport(eq("bruta/test.csv"), reportJson.capture());
        assertTrue(reportJson.getValue().contains("PARCIAL"));
        assertTrue(q.getValue().getReason().getBytes(StandardCharsets.UTF_8).length <= 500);
    }
    @Test void entirelyRejectedFileNeverProducesValidatedVersion() throws Exception {
        var response = service.uploadFile(csv("id\nx"), 1L, 2L, 4L);
        assertEquals("REJEITADO", response.validation().status());
        verify(bucket, never()).uploadValidatedContent(any(), any(), any(), any());
        verify(versions).save(argThat(v -> v.getStatus() == VersionStatus.BRUTA));
        verify(quarantine).save(argThat(q -> q.getStatus().equals("ARQUIVO_REJEITADO")));
        assertEquals(IngestionStatus.ERRO, dataset.getFiles().getFirst().getIngestion().getIngStatus());
    }
    @Test void rejectsMetadataThatDoesNotFitExistingFileColumns() {
        for (String filename : List.of("a".repeat(201) + ".csv", "arquivo", "arquivo.abcdefghijkl")) {
            var file = new MockMultipartFile("file", filename, "text/plain", "id\n1".getBytes(StandardCharsets.UTF_8));
            assertThrows(IllegalArgumentException.class, () -> service.uploadFile(file, 1L, 2L, 4L));
        }
        verifyNoInteractions(bucket);
    }
    @Test void validFileSkipsQuarantine() throws Exception {
        assertEquals("VALIDO", service.uploadFile(csv("id\n1"), 1L, 2L, 4L).validation().status());
        verifyNoInteractions(quarantine);
    }
    @Test void malformedFileRejectsAllRows() throws Exception {
        var report = service.uploadFile(csv("id\n1\n\"unterminated"), 1L, 2L, 4L).validation();
        assertEquals("REJEITADO", report.status()); assertEquals(0, report.validRecords());
        verify(bucket, never()).uploadValidatedContent(any(), any(), any(), any());
    }
    @Test void duplicateUploadFailsBeforeStorageAndValidation() throws Exception {
        when(files.existsByHashInDataset(eq(1L), any())).thenReturn(true);
        assertThrows(DuplicateFileException.class, () -> service.uploadFile(csv("id\n1"), 1L, 2L, 4L));
        verifyNoInteractions(bucket, dictionaries);
    }
    @Test void transactionRollbackCleansBothArtifacts() throws Exception {
        service.uploadFile(csv("id\n1"), 1L, 2L, 4L);
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(bucket).deleteObject("bruta/test.csv"); verify(bucket).deleteObject("validada/test.csv");
        verify(bucket).deleteObject("bruta/test.csv.validation.json");
    }
    @Test void failedValidatedUploadLeavesNoApprovedVersion() throws Exception {
        when(bucket.uploadValidatedContent(any(), any(), any(), any())).thenThrow(new IllegalStateException("storage offline"));
        assertThrows(IllegalStateException.class, () -> service.uploadFile(csv("id\n1"), 1L, 2L, 4L));
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(bucket).deleteObject("bruta/test.csv");
        verify(versions, never()).save(argThat(v -> v.getVersionNumber() == 2));
    }
    @Test void failedReportUploadRollsBackAndCleansRawObject() throws Exception {
        when(bucket.uploadValidationReport(any(), any())).thenThrow(new IllegalStateException("storage offline"));
        assertThrows(IllegalStateException.class, () -> service.uploadFile(csv("id\n1"), 1L, 2L, 4L));
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(bucket).deleteObject("bruta/test.csv");
        verifyNoInteractions(versions, ingestions);
    }
    @Test void committedTransactionKeepsAllArtifacts() throws Exception {
        service.uploadFile(csv("id\n1"), 1L, 2L, 4L);
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(bucket, never()).deleteObject(any());
    }
    @Test void reportCanBeReadAfterUpload() throws Exception {
        var response = service.uploadFile(csv("id\n1"), 1L, 2L, 4L);
        when(files.findById(6L)).thenReturn(Optional.of(dataset.getFiles().getFirst()));
        var reportJson = ArgumentCaptor.forClass(String.class);
        verify(bucket).uploadValidationReport(eq("bruta/test.csv"), reportJson.capture());
        when(bucket.readValidationReport("bruta/test.csv")).thenReturn(reportJson.getValue());
        assertEquals(response.validation(), service.validationReport(6L));
    }
}
