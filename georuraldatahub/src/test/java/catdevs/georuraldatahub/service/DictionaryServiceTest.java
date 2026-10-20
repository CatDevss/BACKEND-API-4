package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.dto.DictionaryRequestDTO;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.validation.*;

class DictionaryServiceTest {
    private final DictionaryRepository repository = mock(DictionaryRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final IngestionRepository ingestions = mock(IngestionRepository.class);
    private final DictionaryService service = new DictionaryService(repository, sources, ingestions);
    private final Dictionary dictionary = new Dictionary();
    private final Source source = new Source();
    private final DictionaryRequestDTO request = new DictionaryRequestDTO("Cadastro", "Descrição",
            List.of(new FieldRule("id", true, FieldType.INTEGER, List.of("1", "2"), true)));

    @BeforeEach void setup() {
        source.setId(3L); dictionary.setId(1L); dictionary.setSource(source); dictionary.setVersion(1);
        dictionary.setName("Original"); dictionary.setStructure("[{\"name\":\"id\",\"type\":\"INTEGER\",\"required\":true}]");
        when(sources.findLockedById(3L)).thenReturn(Optional.of(source));
        when(sources.existsById(3L)).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(dictionary));
        when(repository.findSourceIdById(1L)).thenReturn(Optional.of(3L));
        when(repository.findLockedById(1L)).thenReturn(Optional.of(dictionary));
        when(repository.findFirstBySource_IdOrderByVersionDesc(3L)).thenReturn(Optional.of(dictionary));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
    @Test void createsNextVersionWithoutChangingPrevious() {
        var response = service.create(3L, request);
        assertEquals(2, response.version()); assertEquals("RASCUNHO", response.status());
        assertEquals(request.rules(), response.rules()); assertEquals("Original", dictionary.getName());
        verify(sources).findLockedById(3L);
    }
    @Test void createsFirstVersion() {
        when(repository.findFirstBySource_IdOrderByVersionDesc(3L)).thenReturn(Optional.empty());
        assertEquals(1, service.create(3L, request).version());
    }
    @Test void updatesAndDeletesDraft() {
        assertEquals("Cadastro", service.update(1L, request).name());
        service.delete(1L); verify(repository).delete(dictionary);
    }
    @Test void protectsUsedDictionaryFromUpdateAndDelete() {
        when(ingestions.existsByDictionary_Id(1L)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.update(1L, request)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.delete(1L)).getStatusCode().value());
        verify(repository, never()).save(any()); verify(repository, never()).delete(any());
    }
    @Test void protectsLegacyDictionaryReferencedByIngestion() {
        when(ingestions.existsByDictionary_Id(1L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.update(1L, request));
        assertThrows(ResponseStatusException.class, () -> service.delete(1L));
        assertEquals("UTILIZADO", service.get(1L).status());
    }
    @Test void clonesUsedRulesIntoNewDraftVersion() {
        when(ingestions.existsByDictionary_Id(1L)).thenReturn(true);
        var response = service.newVersion(1L);
        assertEquals(2, response.version()); assertEquals("RASCUNHO", response.status());
        assertEquals(service.rules(dictionary), response.rules());
        assertEquals("UTILIZADO", service.get(1L).status());
    }
    @Test void selectionLocksSourceUntilIngestionIsRecorded() {
        assertSame(dictionary, service.acquireForValidation(3L));
        var order = inOrder(sources, repository);
        order.verify(sources).findLockedById(3L);
        order.verify(repository).findFirstBySource_IdOrderByVersionDesc(3L);
    }
    @Test void rejectsVersionAboveCurrentDatabaseLimit() {
        dictionary.setVersion(99);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create(3L, request)).getStatusCode().value());
        verify(repository, never()).save(any());
    }
    @Test void rejectsRulesAboveVarcharByteLimit() {
        var rule = new FieldRule("campo", true, FieldType.STRING, List.of("á".repeat(6000)), false);
        assertThrows(IllegalArgumentException.class, () -> service.create(3L,
                new DictionaryRequestDTO("Cadastro", null, List.of(rule))));
        verify(repository, never()).save(any());
    }
    @Test void acceptsVersion99AndRejectsInvalidExistingVersion() {
        dictionary.setVersion(98);
        assertEquals(99, service.create(3L, request).version());
        dictionary.setVersion(null);
        assertThrows(ResponseStatusException.class, () -> service.create(3L, request));
    }
    @Test void enforcesExistingNameAndDescriptionByteLimits() {
        for (var body : List.of(new DictionaryRequestDTO("a".repeat(101), null, request.rules()),
                new DictionaryRequestDTO("á".repeat(60), null, request.rules()),
                new DictionaryRequestDTO("Nome", "a".repeat(501), request.rules())))
            assertThrows(IllegalArgumentException.class, () -> service.create(3L, body));
    }
    @Test void rejectsDuplicateAndIncompleteRules() {
        var invalid = List.of(new DictionaryRequestDTO("nome", null, List.of()),
                new DictionaryRequestDTO("nome", null, List.of(new FieldRule("id", false, null))),
                new DictionaryRequestDTO("nome", null, List.of(request.rules().getFirst(), request.rules().getFirst())));
        for (var body : invalid) assertThrows(IllegalArgumentException.class, () -> service.create(3L, body));
        verify(repository, never()).save(any());
    }
    @Test void readsAndListsSnapshots() {
        when(repository.findBySource_IdOrderByVersionDesc(3L)).thenReturn(List.of(dictionary));
        assertEquals(1L, service.get(1L).id()); assertEquals(1, service.list(3L).size());
    }
    @Test void returnsNotFoundForMissingSourceAndDictionary() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.create(99L, request)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(99L)).getStatusCode().value());
    }
    @Test void rejectsInvalidStoredStructure() {
        dictionary.setStructure("{}");
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.rules(dictionary)).getStatusCode().value());
    }
}
