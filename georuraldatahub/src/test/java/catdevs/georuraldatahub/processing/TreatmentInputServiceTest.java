package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TreatmentInputServiceTest {
    private final FileRepository files = mock(FileRepository.class);
    private final VersionRepository versions = mock(VersionRepository.class);
    private final UserService users = mock(UserService.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final TreatmentInputService service = new TreatmentInputService(files,versions,users,dictionaries);

    private void prepare() {
        var user = new User(); user.setUserStatus(1);
        when(users.findEntityById(2L)).thenReturn(user);
        var source = new Source(); source.setId(4L);
        var dictionary = new catdevs.georuraldatahub.entity.Dictionary(); dictionary.setId(3L); dictionary.setVersion(1); dictionary.setSource(source);
        var ingestion = new DataIngestion(); ingestion.setDictionary(dictionary);
        var file = new catdevs.georuraldatahub.entity.File(); file.setId(1L); file.setFormatFile("geojson"); file.setIngestion(ingestion);
        file.setLocation("bruta/original.geojson");
        when(files.findById(1L)).thenReturn(Optional.of(file));
        when(dictionaries.rules(dictionary)).thenReturn(List.of());
    }

    @Test void loadsFirstDictionaryApprovedRevisionInsteadOfReprocessingLatestSpatialResult() {
        prepare();
        var version = new Version(); version.setId(10L); version.setStatus(VersionStatus.TRATADA); version.setLocation("tratada/original.geojson");
        when(versions.findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberAsc(
                eq(1L),eq(Set.of(VersionStatus.TRATADA,VersionStatus.PUBLICADA)),eq("tratada/"))).thenReturn(Optional.of(version));
        var input = service.load(1L,2L);
        assertEquals(10L,input.inputVersionId());
        assertEquals("tratada/original.geojson",input.treatedLocation());
        verify(versions,never()).findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(any(),any());
    }

    @Test void rawOrQuarantinedFileCannotStartSpatialProcessing() {
        prepare();
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> service.load(1L,2L)).getStatusCode().value());
        verify(versions).findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberAsc(
                eq(1L),argThat(statuses -> !statuses.contains(VersionStatus.BRUTA) && !statuses.contains(VersionStatus.QUARENTENA)),eq("tratada/"));
    }
}
