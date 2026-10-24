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
        when(datasets.findLockedEntityById(1L)).thenReturn(dataset);
        var user = new User(); user.setId(2L); when(users.findEntityById(2L)).thenReturn(user);
        var dictionary = new Dictionary(); dictionary.setId(3L); dictionary.setVersion(2);
        when(dictionaries.acquireForValidation(4L, null)).thenReturn(dictionary);
        when(dictionaries.rules(dictionary)).thenReturn(List.of(FieldRule.required("id", FieldType.INTEGER)));
        when(bucket.uploadToSourceRawZone(any(), eq(4L), any(), any())).thenReturn("bruta/test.csv");
        when(bucket.uploadValidationReport(eq("bruta/test.csv"), any())).thenReturn("bruta/test.csv.validation.json");
        when(bucket.uploadSourceValidatedContent(any(), eq(4L), any(), any())).thenReturn("validada/test.csv");
        when(ingestions.save(any())).thenAnswer(inv -> { DataIngestion i = inv.getArgument(0); i.setId(5L); return i; });
        when(files.save(any())).thenAnswer(inv -> { File f = inv.getArgument(0); f.setId(6L); return f; });
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }
    private MockMultipartFile csv(String text) { return new MockMultipartFile("file", "test.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8)); }

    @Test void isolatedModeKeepsValidationQuarantineAndApprovedSubsetWithoutOccurrenceTables() throws Exception {
        var isolated = new FileService(files, versions, ingestions, datasets, users, dictionaries, bucket, validator, quarantine);
        var response = isolated.uploadToSource(csv("id\n1\nx"), 4L, 2L);
        assertEquals("PARCIAL", response.validation().status());
        assertEquals(1, response.validation().validRecords());
        assertEquals(1, response.validation().rejectedRecords());
        verify(quarantine).save(any());
        verify(bucket).uploadValidationReport(any(), any());
        verify(bucket).uploadSourceValidatedContent(any(), eq(4L), any(), any());
    }
    @Test void sourceUploadValidatesAndStoresWithoutLookingUpOrAssociatingDataset() throws Exception {
        var response = service.uploadToSource(csv("id\n1"), 4L, 2L);
        assertEquals("VALIDO", response.validation().status());
        assertEquals(6L, response.id());
        verifyNoInteractions(datasets);
        assertTrue(dataset.getFiles().isEmpty());
        verify(files).save(argThat(file -> file.getIngestion().getDictionary().getId().equals(3L)));
        verify(bucket).uploadToSourceRawZone(any(), eq(4L), any(), eq("test.csv"));
        verify(bucket).uploadSourceValidatedContent(any(), eq(4L), any(), eq("test.csv"));
    }
    @Test void sourceCollectionValidatesAndStoresWithoutDataset() throws Exception {
        var data = new catdevs.georuraldatahub.collection.CollectedData("test.csv", "csv",
                "id\n1".getBytes(StandardCharsets.UTF_8), List.of(), null);
        assertEquals("VALIDO", service.uploadCollectedFile(data, null, 2L, 4L).validation().status());
        verifyNoInteractions(datasets);
        assertTrue(dataset.getFiles().isEmpty());
        verify(bucket).uploadCollectionArtifact(eq("bruta/test.csv.collection.json"), any(), eq("application/json"));
    }
    @Test void sourceListingIncludesFilesWithoutAnyDataset() {
        var user = new User(); user.setId(2L);
        var ingestion = new DataIngestion(); ingestion.setId(5L); ingestion.setUser(user);
        var file = new File(); file.setId(6L); file.setIngestion(ingestion); file.setName("test.csv");
        when(files.findByIngestion_Dictionary_Source_IdOrderByIdDesc(4L)).thenReturn(List.of(file));
        var result = service.findBySourceId(4L);
        assertEquals(6L, result.getFirst().id());
        assertEquals(5L, result.getFirst().ingestionId());
        verifyNoInteractions(datasets);
    }
    @Test void collectedUploadPreservesRawResponseAndMetadataAndUsesManualValidation() throws Exception {
        byte[] csv = "id\n1\nx".getBytes(StandardCharsets.UTF_8);
        var original = new catdevs.georuraldatahub.collection.CollectedData.Original(
                "original.csv", "https://dataserver-coids.inpe.br/example.csv", "text/csv", csv);
        var metadata = new catdevs.georuraldatahub.collection.CollectedData.Metadata(
                "INPE_QUEIMADAS", "2026-10-18", "csv", "csv", "Brasil", "2026-10-20T00:00:00Z",
                "https://dataserver-coids.inpe.br/", List.of("Todos os satélites"), List.of(
                    new catdevs.georuraldatahub.collection.CollectedData.OriginalMetadata(
                        original.filename(), original.origin(), original.contentType(), csv.length, original.sha256())));
        var data = new catdevs.georuraldatahub.collection.CollectedData("test.csv", "csv", csv, List.of(original), metadata);
        when(bucket.uploadCollectionArtifact(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        var response = service.uploadCollectedFile(data, 1L, 2L, 4L);
        assertEquals("PARCIAL", response.validation().status());
        verify(bucket).uploadCollectionArtifact(eq("bruta/test.csv.originals/00000_original.csv"), eq(csv), eq("text/csv"));
        var json = ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadCollectionArtifact(eq("bruta/test.csv.collection.json"), json.capture(), eq("application/json"));
        String stored = new String(json.getValue(), StandardCharsets.UTF_8);
        assertTrue(stored.contains(original.sha256())); assertTrue(stored.contains("INPE_QUEIMADAS"));
        when(files.findById(6L)).thenReturn(Optional.of(dataset.getFiles().getFirst()));
        when(bucket.readCollectionMetadata("bruta/test.csv")).thenReturn(stored);
        assertEquals(metadata, service.collectionMetadata(6L));
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(bucket).deleteObject("bruta/test.csv.originals/00000_original.csv");
        verify(bucket).deleteObject("bruta/test.csv.collection.json");
    }
    @Test void failedProvenanceUploadDoesNotPersistAnIngestion() throws Exception {
        byte[] csv = "id\n1".getBytes(StandardCharsets.UTF_8);
        var data = new catdevs.georuraldatahub.collection.CollectedData("test.csv","csv",csv,List.of(),null);
        when(bucket.uploadCollectionArtifact(any(), any(), any())).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> service.uploadCollectedFile(data,1L,2L,4L));
        verifyNoInteractions(ingestions,versions);
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(bucket).deleteObject("bruta/test.csv");
        verify(bucket).deleteObject("bruta/test.csv.validation.json");
    }
    @Test void uploadsOnlyValidRowsAndPersistsPartialReport() throws Exception {
        var response = service.uploadFile(csv("id\n1\nx\n2"), 1L, 2L, 4L);
        assertEquals("PARCIAL", response.validation().status()); assertEquals(2, response.validation().validRecords());
        assertEquals(3L, response.validation().dictionaryId()); assertEquals(2, response.validation().dictionaryVersion());
        var accepted = ArgumentCaptor.forClass(byte[].class);
        verify(bucket).uploadSourceValidatedContent(accepted.capture(), eq(4L), any(), any());
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
        verify(bucket, never()).uploadSourceValidatedContent(any(), any(), any(), any());
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
        verify(bucket, never()).uploadSourceValidatedContent(any(), any(), any(), any());
    }
    @Test void duplicateUploadFailsBeforeStorageAndValidation() throws Exception {
        when(files.existsByHashAndIngestion_Dictionary_Source_Id(any(), eq(4L))).thenReturn(true);
        assertThrows(DuplicateFileException.class, () -> service.uploadFile(csv("id\n1"), 1L, 2L, 4L));
        verifyNoInteractions(bucket);
        verify(dictionaries).acquireForValidation(4L, null);
    }
    @Test void transactionRollbackCleansBothArtifacts() throws Exception {
        service.uploadFile(csv("id\n1"), 1L, 2L, 4L);
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(bucket).deleteObject("bruta/test.csv"); verify(bucket).deleteObject("validada/test.csv");
        verify(bucket).deleteObject("bruta/test.csv.validation.json");
    }
    @Test void failedValidatedUploadLeavesNoApprovedVersion() throws Exception {
        when(bucket.uploadSourceValidatedContent(any(), any(), any(), any())).thenThrow(new IllegalStateException("storage offline"));
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
