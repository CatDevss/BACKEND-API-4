package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.dto.*;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.DatasetRepository;

class DatasetServiceTest {
    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final UserService users = mock(UserService.class);
    private final DatasetService service = new DatasetService(datasets, users);

    @Test void createTrimsNameAndStartsWithoutAnyFileAssociation() {
        var user = new User(); user.setId(2L);
        when(users.findEntityById(2L)).thenReturn(user);
        when(datasets.save(any())).thenAnswer(inv -> { Dataset d = inv.getArgument(0); d.setId(1L); return d; });
        var result = service.create(new DatasetCreateRequestDTO("  Paraná  ", 2L));
        assertEquals("Paraná", result.name()); assertEquals(2L, result.userId());
        verify(datasets).save(argThat(d -> d.getFiles().isEmpty()));
    }
    @Test void nameMustFitOracleBytesRatherThanOnlyCharacterCount() {
        var dataset = new Dataset(); dataset.setName("Original");
        when(datasets.findById(1L)).thenReturn(Optional.of(dataset));
        for (String name : java.util.List.of("a".repeat(101), "á".repeat(51), "   ")) {
            assertThrows(IllegalArgumentException.class, () -> service.create(new DatasetCreateRequestDTO(name, 2L)));
            assertThrows(IllegalArgumentException.class, () -> service.update(1L, new DatasetUpdateRequestDTO(name)));
        }
        assertEquals("Original", dataset.getName());
        verify(datasets, never()).save(any());
    }
    @Test void renamePreservesMembership() {
        var dataset = new Dataset(); dataset.setId(1L);
        var file = new File(); dataset.getFiles().add(file);
        when(datasets.findById(1L)).thenReturn(Optional.of(dataset));
        assertEquals("Novo", service.update(1L, new DatasetUpdateRequestDTO(" Novo ")).name());
        assertSame(file, dataset.getFiles().getFirst());
    }
    @Test void nonemptyDatasetCannotBeDeletedAndMembershipIsPreserved() {
        var dataset = new Dataset(); dataset.getFiles().add(new File());
        when(datasets.findLockedById(1L)).thenReturn(Optional.of(dataset));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.delete(1L)).getStatusCode().value());
        assertEquals(1, dataset.getFiles().size()); verify(datasets, never()).delete(any());
    }
    @Test void emptyDatasetCanBeDeleted() {
        var dataset = new Dataset();
        when(datasets.findLockedById(1L)).thenReturn(Optional.of(dataset));
        service.delete(1L); verify(datasets).delete(dataset);
    }
}
