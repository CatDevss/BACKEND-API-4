package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import catdevs.georuraldatahub.collection.CollectedData;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Quarantine;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.exception.DuplicateFileException;
import catdevs.georuraldatahub.validation.CsvRecordValidator;
import catdevs.georuraldatahub.validation.FieldRule;
import catdevs.georuraldatahub.validation.FieldType;
import catdevs.georuraldatahub.validation.FileValidationService;
import catdevs.georuraldatahub.validation.GeoJsonRecordValidator;

class FileServiceValidationTest {

    private EntityManager entityManager;
    private TypedQuery<Long> countQuery;

    private BucketService bucketService;
    private DatasetService datasetService;
    private DictionaryService dictionaryService;
    private FileValidationService validationService;
    private FileService service;

    private User user;
    private Dictionary dictionary;
    private Dataset dataset;

    @SuppressWarnings("unchecked")
    @BeforeEach
        void setup() throws Exception {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }

        TransactionSynchronizationManager.initSynchronization();

        entityManager = mock(EntityManager.class);
        countQuery = mock(TypedQuery.class);

        bucketService = mock(BucketService.class);
        datasetService = mock(DatasetService.class);
        dictionaryService = mock(DictionaryService.class);

        validationService = new FileValidationService(
                new CsvRecordValidator(),
                new GeoJsonRecordValidator()
        );

        service = new FileService(
                bucketService,
                datasetService,
                dictionaryService,
                validationService
        );

        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );

        user = new User();
        user.setId(2L);

        dictionary = new Dictionary();
        dictionary.setId(3L);
        dictionary.setVersion(2);

        dataset = new Dataset();
        dataset.setFiles(new ArrayList<>());

        when(entityManager.find(User.class, 2L))
                .thenReturn(user);

        when(dictionaryService.acquireForValidation(4L))
                .thenReturn(dictionary);

        when(dictionaryService.rules(dictionary))
                .thenReturn(List.of(
                        FieldRule.required("id", FieldType.INTEGER)
                ));

        when(entityManager.createQuery(
                contains("COUNT(f)"),
                eq(Long.class)
        )).thenReturn(countQuery);

        when(countQuery.setParameter(eq("hash"), any()))
                .thenReturn(countQuery);

        when(countQuery.setParameter(eq("sourceId"), eq(4L)))
                .thenReturn(countQuery);

        when(countQuery.getSingleResult())
                .thenReturn(0L);

        when(bucketService.uploadToSourceRawZone(
                any(),
                eq(4L),
                anyString(),
                anyString()
        )).thenReturn("bruta/test.csv");

        when(bucketService.uploadValidationReport(
                eq("bruta/test.csv"),
                anyString()
        )).thenReturn("bruta/test.csv.validation.json");

        when(bucketService.uploadSourceValidatedContent(
                any(byte[].class),
                eq(4L),
                anyString(),
                anyString()
        )).thenReturn("validada/test.csv");

        when(bucketService.uploadCollectionArtifact(
                anyString(),
                any(byte[].class),
                anyString()
        )).thenAnswer(invocation -> invocation.getArgument(0));

        when(datasetService.findLockedEntityById(1L))
                .thenReturn(dataset);

        when(datasetService.findEntityById(1L))
                .thenReturn(dataset);

        // Simula os IDs gerados pelo banco durante a persistência.
        doAnswer(invocation -> {
            Object entity = invocation.getArgument(0);

            if (entity instanceof DataIngestion ingestion) {
                ingestion.setId(5L);
            } else if (entity instanceof File file) {
                file.setId(6L);
            }

            return null;
        }).when(entityManager).persist(any());
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void completeSynchronizations(int status) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }

    @Test
    void sourceUploadDoesNotAssociateDataset() throws Exception {
        FileResponseDTO response =
                service.uploadToSource(csv("id\n1"), 4L, 2L);

        assertEquals("VALIDO", response.validation().status());
        assertEquals(6L, response.id());

        verify(datasetService, never())
                .findLockedEntityById(anyLong());

        assertTrue(dataset.getFiles().isEmpty());

        verify(bucketService).uploadToSourceRawZone(
                any(),
                eq(4L),
                anyString(),
                eq("test.csv")
        );

        verify(entityManager, atLeastOnce())
                .persist(any());
    }

    @Test
    void collectedUploadValidatesAndStoresFile() throws Exception {
        CollectedData data = new CollectedData(
                "test.csv",
                "csv",
                "id\n1".getBytes(StandardCharsets.UTF_8),
                List.of(),
                null
        );

        FileResponseDTO response =
                service.uploadCollectedFile(data, null, 2L, 4L);

        assertEquals("VALIDO", response.validation().status());

        verify(datasetService, never())
                .findLockedEntityById(anyLong());

        verify(bucketService).uploadCollectionArtifact(
                eq("bruta/test.csv.collection.json"),
                any(byte[].class),
                eq("application/json")
        );
    }

    @Test
    void partialValidationPersistsQuarantineAndVersions() throws Exception {
        FileResponseDTO response =
                service.uploadFile(csv("id\n1\nx\n2"), 1L, 2L, 4L);

        assertEquals("PARCIAL", response.validation().status());
        assertEquals(2, response.validation().validRecords());
        assertEquals(3L, response.validation().dictionaryId());
        assertEquals(2, response.validation().dictionaryVersion());

        verify(bucketService).uploadSourceValidatedContent(
                any(byte[].class),
                eq(4L),
                anyString(),
                eq("test.csv")
        );

        ArgumentCaptor<Version> versionCaptor =
                ArgumentCaptor.forClass(Version.class);

        verify(entityManager, times(2))
                .persist(versionCaptor.capture());

        assertEquals(2, versionCaptor.getAllValues().size());

        ArgumentCaptor<Quarantine> quarantineCaptor =
                ArgumentCaptor.forClass(Quarantine.class);

        verify(entityManager).persist(quarantineCaptor.capture());

        Quarantine quarantine = quarantineCaptor.getValue();

        assertEquals("REGISTROS_REJEITADOS", quarantine.getStatus());

        assertTrue(quarantine.getReason().contains("INVALID_TYPE"));

        assertEquals(
                IngestionStatus.CONCLUIDO,
                capturePersistedIngestionStatus()
        );
    }

    private IngestionStatus capturePersistedIngestionStatus() {
        ArgumentCaptor<DataIngestion> captor =
                ArgumentCaptor.forClass(DataIngestion.class);

        verify(entityManager).persist(captor.capture());

        return captor.getValue().getIngStatus();
    }

    @Test
    void entirelyRejectedFileDoesNotUploadValidatedContent()
            throws Exception {

        FileResponseDTO response =
                service.uploadFile(csv("id\nx"), 1L, 2L, 4L);

        assertEquals("REJEITADO", response.validation().status());

        verify(bucketService, never())
                .uploadSourceValidatedContent(
                        any(),
                        anyLong(),
                        anyString(),
                        anyString()
                );

        ArgumentCaptor<Quarantine> captor =
                ArgumentCaptor.forClass(Quarantine.class);

        verify(entityManager).persist(captor.capture());

        assertEquals(
                "ARQUIVO_REJEITADO",
                captor.getValue().getStatus()
        );

        assertEquals(
                IngestionStatus.ERRO,
                capturePersistedIngestionStatus()
        );
    }

    @Test
    void validFileDoesNotCreateQuarantine() throws Exception {
        service.uploadFile(csv("id\n1"), 1L, 2L, 4L);

        verify(entityManager, never())
                .persist(isA(Quarantine.class));
    }

    @Test
    void duplicateUploadFailsBeforeStorage() throws Exception {
        when(countQuery.getSingleResult()).thenReturn(1L);

        assertThrows(
                DuplicateFileException.class,
                () -> service.uploadFile(
                        csv("id\n1"),
                        1L,
                        2L,
                        4L
                )
        );

        // CORRIGIDO: uso de any() no primeiro argumento.
        verify(bucketService, never()).uploadToSourceRawZone(
                any(),
                anyLong(),
                anyString(),
                anyString()
        );

        verify(bucketService, never())
                .uploadValidationReport(anyString(), anyString());
    }

    @Test
    void rejectsFilenameLongerThanDatabaseLimit() throws Exception{
        String filename = "a".repeat(201) + ".csv";

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                filename,
                "text/csv",
                "id\n1".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadFile(
                        multipartFile,
                        1L,
                        2L,
                        4L
                )
        );

        verify(bucketService, never()).uploadToSourceRawZone(
                any(),
                anyLong(),
                anyString(),
                anyString()
        );
    }

    @Test
    void malformedCsvIsRejected() throws Exception {
        FileResponseDTO response = service.uploadFile(
                csv("id\n1\n\"unterminated"),
                1L,
                2L,
                4L
        );

        assertEquals("REJEITADO", response.validation().status());

        verify(bucketService, never())
                .uploadSourceValidatedContent(
                        any(),
                        anyLong(),
                        anyString(),
                        anyString()
                );
    }

    @Test
    void transactionRollbackCleansUploadedObjects() throws Exception {
        service.uploadFile(csv("id\n1"), 1L, 2L, 4L);

        completeSynchronizations(
                TransactionSynchronization.STATUS_ROLLED_BACK
        );

        verify(bucketService).deleteObject("bruta/test.csv");
        verify(bucketService).deleteObject(
                "bruta/test.csv.validation.json"
        );
        verify(bucketService).deleteObject("validada/test.csv");
    }

    @Test
    void committedTransactionKeepsUploadedObjects() throws Exception {
        service.uploadFile(csv("id\n1"), 1L, 2L, 4L);

        completeSynchronizations(
                TransactionSynchronization.STATUS_COMMITTED
        );

        verify(bucketService, never()).deleteObject(anyString());
    }
}