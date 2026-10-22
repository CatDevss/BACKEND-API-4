
package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.DictionaryRequestDTO;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.validation.FieldRule;
import catdevs.georuraldatahub.validation.FieldType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

class DictionaryServiceTest {

    private EntityManager entityManager;
    private DictionaryService service;

    @BeforeEach
    void setup() {
        entityManager = mock(EntityManager.class);
        service = new DictionaryService();

        ReflectionTestUtils.setField(
                service, "entityManager", entityManager
        );
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<Dictionary> mockDictionaryQuery() {
        TypedQuery<Dictionary> query = mock(TypedQuery.class);

        when(entityManager.createQuery(
                anyString(), eq(Dictionary.class)
        )).thenReturn(query);

        when(query.setParameter(anyString(), any()))
                .thenReturn(query);

        when(query.setMaxResults(anyInt()))
                .thenReturn(query);

        when(query.getResultList()).thenReturn(List.of());

        when(query.getResultStream())
                .thenReturn(Stream.<Dictionary>empty());

        return query;
    }

    @SuppressWarnings("unchecked")
    private void mockUsageCount(Long count) {
        TypedQuery<Long> query = mock(TypedQuery.class);

        when(entityManager.createQuery(
                anyString(), eq(Long.class)
        )).thenReturn(query);

        when(query.setParameter(anyString(), any()))
                .thenReturn(query);

        when(query.getSingleResult()).thenReturn(count);
    }

    private DictionaryRequestDTO validRequest(String name) {
        return new DictionaryRequestDTO(
                name,
                "Descrição de teste",
                List.of(
                        FieldRule.required("nome", FieldType.STRING)
                )
        );
    }

    @Test
    void findLatestBySourceIdReturnsLatestDictionary() {
        Dictionary dictionary = new Dictionary();
        Source source = new Source();

        source.setId(1L);
        dictionary.setSource(source);
        dictionary.setVersion(2);

        TypedQuery<Dictionary> query = mockDictionaryQuery();

        when(query.getResultStream())
                .thenReturn(Stream.of(dictionary));

        assertSame(dictionary, service.findLatestBySourceId(1L));
    }

    @Test
    void findLatestBySourceIdThrowsWhenNoDictionaryExists() {
        mockDictionaryQuery();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.findLatestBySourceId(1L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void createDictionaryWhenSourceExists() {
        Source source = new Source();
        source.setId(1L);

        when(entityManager.find(
                eq(Source.class),
                eq(1L),
                eq(LockModeType.PESSIMISTIC_WRITE)
        )).thenReturn(source);

        TypedQuery<Dictionary> query = mockDictionaryQuery();

        when(query.getResultList()).thenReturn(List.of());

        mockUsageCount(0L);

        var response = service.create(1L, validRequest("Dicionário teste"));

        assertNotNull(response);
        verify(entityManager).persist(any(Dictionary.class));
        verify(entityManager).flush();
    }

    @Test
    void createDictionaryWhenSourceDoesNotExist() {
        when(entityManager.find(
                eq(Source.class),
                eq(99L),
                eq(LockModeType.PESSIMISTIC_WRITE)
        )).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(99L, validRequest("Dicionário teste"))
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void updateRejectsBlankDictionaryName() {
        Source source = new Source();
        source.setId(1L);

        Dictionary dictionary = new Dictionary();
        dictionary.setId(10L);
        dictionary.setSource(source);
        dictionary.setVersion(1);

        when(entityManager.find(Dictionary.class, 10L))
                .thenReturn(dictionary);

        when(entityManager.find(
                eq(Dictionary.class),
                eq(10L),
                eq(LockModeType.PESSIMISTIC_WRITE)
        )).thenReturn(dictionary);

        when(entityManager.find(
                eq(Source.class),
                eq(1L),
                eq(LockModeType.PESSIMISTIC_WRITE)
        )).thenReturn(source);

        mockUsageCount(0L);

        DictionaryRequestDTO request = new DictionaryRequestDTO(
                " ",
                "Descrição",
                List.of(
                        FieldRule.required("nome", FieldType.STRING)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(10L, request)
        );
    }
}
