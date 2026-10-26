package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.service.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TreatmentInputServiceTest {
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final UserService users = mock(UserService.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final TreatmentInputService service = new TreatmentInputService(files, versions, users, dictionaries);
    private final Version current = new Version();

    @BeforeEach void prepare() {
        var user = new User(); user.setId(3L); user.setUserStatus(1);
        when(users.findEntityById(3L)).thenReturn(user);
        var source = new Source(); source.setId(5L);
        var dictionary = new catdevs.georuraldatahub.entity.Dictionary(); dictionary.setId(2L); dictionary.setVersion(1); dictionary.setSource(source);
        var ingestion = new DataIngestion(); ingestion.setDictionary(dictionary);
        var file = new catdevs.georuraldatahub.entity.File(); file.setId(7L); file.setName("pontos.csv"); file.setFormatFile("csv");
        file.setLocation("bruta/pontos.csv"); file.setIngestion(ingestion);
        when(files.findById(7L)).thenReturn(Optional.of(file));
        current.setId(10L); current.setFile(file); current.setStatus(VersionStatus.TRATADA); current.setLocation("tratada/pontos.csv");
        when(versions.findFirstByFile_IdOrderByVersionNumberDesc(7L)).thenReturn(Optional.of(current));
    }

    @Test void readsCurrentTreatedVersionAndPreservesOriginalPointer() {
        var input = service.load(7L, 3L);
        assertEquals(10L, input.inputVersionId());
        assertEquals("tratada/pontos.csv", input.workingLocation());
        assertEquals("bruta/pontos.csv", input.rawLocation());
    }

    @Test void blocksRawOrQuarantinedLatestVersionInsteadOfSelectingAnOlderApprovedCopy() {
        for (var status : List.of(VersionStatus.BRUTA, VersionStatus.QUARENTENA)) {
            current.setStatus(status);
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.load(7L, 3L)).getStatusCode().value());
        }
        current.setStatus(VersionStatus.TRATADA); current.setLocation("bruta/pontos.csv");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.load(7L, 3L)).getStatusCode().value());
        verifyNoInteractions(dictionaries);
    }
}
