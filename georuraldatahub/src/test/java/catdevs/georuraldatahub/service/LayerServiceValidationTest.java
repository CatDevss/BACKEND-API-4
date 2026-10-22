package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Version;

class LayerServiceValidationTest {

    private EntityManager entityManager;
    private BucketService bucketService;
    private LayerService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        entityManager = mock(EntityManager.class);
        bucketService = mock(BucketService.class);
        objectMapper = new ObjectMapper();

        service = new LayerService(bucketService);

        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );
    }

    private File file(Long id) {
        File file = new File();
        file.setId(id);
        file.setName("test.csv");
        file.setFormatFile("csv");
        file.setLocation("bruta/original.csv");
        file.setSize(100L);
        file.setReceivedDate(LocalDateTime.now());
        return file;
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<Version> mockVersionQuery(List<Version> versions) {
        TypedQuery<Version> query = mock(TypedQuery.class);

        when(entityManager.createQuery(
                contains("FROM Version v"),
                eq(Version.class)
        )).thenReturn(query);

        when(query.setParameter(eq("fileId"), anyLong()))
                .thenReturn(query);

        when(query.setParameter(
                eq("location"),
                eq("validada/%")
        )).thenReturn(query);

        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(versions);

        return query;
    }

    @Test
    void rejectsLayerWithoutApprovedVersion() {
        when(entityManager.find(File.class, 1L))
                .thenReturn(file(1L));

        mockVersionQuery(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getFileLayer(1L)
        );

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(bucketService);
    }

    @Test
    void readsFilteredVersionInsteadOfOriginal() throws IOException {
        File file = file(1L);

        when(entityManager.find(File.class, 1L))
                .thenReturn(file);

        Version version = new Version();
        version.setFile(file);
        version.setVersionNumber(2);
        version.setLocation("validada/filtered.csv");

        mockVersionQuery(List.of(version));

        JsonNode expected = objectMapper.readTree("""
                {
                  "type": "FeatureCollection",
                  "features": []
                }
                """);

        when(bucketService.readCsvAsGeoJson("validada/filtered.csv"))
                .thenReturn(expected);

        JsonNode result = service.getFileLayer(1L);

        assertEquals(expected, result);

        verify(bucketService)
                .readCsvAsGeoJson("validada/filtered.csv");

        verify(bucketService, never())
                .readCsvAsGeoJson("bruta/original.csv");
    }

    @SuppressWarnings("unchecked")
    @Test
    void listsOnlyApprovedFiles() {
        Dataset dataset = new Dataset();
        dataset.setId(4L);

        File approvedFile = file(1L);
        File unapprovedFile = file(2L);

        when(entityManager.find(Dataset.class, 4L))
                .thenReturn(dataset);

        TypedQuery<File> fileQuery = mock(TypedQuery.class);

        when(entityManager.createQuery(
                contains("JOIN d.files"),
                eq(File.class)
        )).thenReturn(fileQuery);

        when(fileQuery.setParameter("datasetId", 4L))
                .thenReturn(fileQuery);

        when(fileQuery.getResultList())
                .thenReturn(List.of(approvedFile, unapprovedFile));

        TypedQuery<Version> versionQuery = mock(TypedQuery.class);

        when(entityManager.createQuery(
                contains("FROM Version v"),
                eq(Version.class)
        )).thenReturn(versionQuery);

        when(versionQuery.setParameter(eq("fileId"), anyLong()))
                .thenReturn(versionQuery);

        when(versionQuery.setParameter(
                eq("location"),
                eq("validada/%")
        )).thenReturn(versionQuery);

        when(versionQuery.setMaxResults(1))
                .thenReturn(versionQuery);

        when(versionQuery.getResultList())
                .thenAnswer(invocation -> {
                    // O primeiro arquivo tem versão validada;
                    // o segundo não tem.
                    Long fileId = 1L;
                    // A consulta é executada uma vez por arquivo.
                    // A sequência abaixo diferencia as duas chamadas.
                    return List.of();
                });

        when(versionQuery.getResultList())
                .thenReturn(List.of());

        // Simula o resultado da consulta conforme o arquivo pesquisado.
        when(versionQuery.setParameter(eq("fileId"), eq(1L)))
                .thenReturn(versionQuery);

        when(versionQuery.setParameter(eq("fileId"), eq(2L)))
                .thenReturn(versionQuery);

        // Para evitar compartilhar o mesmo resultado entre arquivos,
        // a consulta é configurada com respostas em sequência.
        when(versionQuery.getResultList())
                .thenReturn(List.of(), List.of());

        List<LayerInfoDTO> result = service.listLayers(4L);

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsMissingDataset() {
        when(entityManager.find(Dataset.class, 4L))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.listLayers(4L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void rejectsMissingFile() {
        when(entityManager.find(File.class, 1L))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> {
                    try {
                        service.getFileLayer(1L);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(bucketService);
    }
}