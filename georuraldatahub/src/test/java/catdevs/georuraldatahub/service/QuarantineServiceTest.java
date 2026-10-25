package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import catdevs.georuraldatahub.repository.QuarantineRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class QuarantineServiceTest {
    private final QuarantineRepository repository = mock(QuarantineRepository.class);
    private final UserService users = mock(UserService.class);
    private final QuarantineService service = new QuarantineService(repository, users);
    private final Quarantine quarantine = new Quarantine();
    private final User user = new User();
    private final User other = new User();
    private final File file = new File();
    private final DataIngestion ingestion = new DataIngestion();

    @BeforeEach void setup() {
        user.setId(3L); user.setName("Analista"); user.setUserStatus(1);
        other.setId(4L); other.setUserStatus(1);
        when(users.findEntityById(3L)).thenReturn(user);
        when(users.findEntityById(4L)).thenReturn(other);
        var source = new Source(); source.setId(5L); source.setName("Fonte");
        var dictionary = new Dictionary(); dictionary.setSource(source);
        ingestion.setId(6L); ingestion.setDictionary(dictionary); ingestion.setIngStatus(IngestionStatus.ERRO);
        file.setId(7L); file.setName("original.csv"); file.setLocation("bruta/original.csv"); file.setIngestion(ingestion);
        quarantine.setId(1L); quarantine.setIngestion(ingestion); quarantine.setFile(file);
        quarantine.setStatus("ARQUIVO_REJEITADO"); quarantine.setReason("REJEITADO; codigos=INVALID_TYPE");
        when(repository.findById(1L)).thenReturn(Optional.of(quarantine));
        when(repository.findLockedById(1L)).thenReturn(Optional.of(quarantine));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void readKeepsUnassignedRecordAndLinksToExistingValidationReport() {
        var result = service.get(1L);
        assertNull(result.responsibleId()); assertNull(result.treatment()); assertNull(result.dateResolution());
        assertEquals(5L, result.sourceId()); assertEquals("Fonte", result.sourceName());
        assertEquals("/arquivos/7/validacao", result.validationReportUrl());
        verify(repository, never()).save(any());
    }
    @Test void claimRegistersOnlyTheVolunteerAndKeepsObservationEmpty() {
        var result = service.claim(1L, 3L);
        assertEquals(3L, result.responsibleId()); assertEquals("Analista", result.responsibleName());
        assertEquals("EM_ANALISE", result.status()); assertNull(result.treatment());
        assertNull(result.dateResolution()); assertEquals("REJEITADO; codigos=INVALID_TYPE", result.reason());
        verify(repository).findLockedById(1L); verify(repository).save(quarantine);
    }
    @Test void repeatedClaimByOwnerIsIdempotentAndAnotherClaimCannotReplaceOwner() {
        quarantine.setUser(user); quarantine.setStatus("EM_ANALISE");
        assertEquals(3L, service.claim(1L, 3L).responsibleId());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.claim(1L, 4L)).getStatusCode().value());
        assertSame(user, quarantine.getUser()); verify(repository, never()).save(any());
    }
    @Test void onlyOwnerCanWriteResolveOrCloseAndUnassignedRecordsMustBeClaimedFirst() {
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.updateTreatment(1L, 3L, "Analisando")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.closeWithoutCorrection(1L, 3L, "Sem correção")).getStatusCode().value());
        quarantine.setUser(other);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.updateTreatment(1L, 3L, "Analisando")).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.resolve(1L, 3L, "Concluído")).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.closeWithoutCorrection(1L, 3L, "Sem correção")).getStatusCode().value());
        assertNull(quarantine.getTreatment()); verify(repository, never()).save(any());
    }
    @Test void observationIsTrimmedAndRespectsOracleByteLimitIncludingAccents() {
        quarantine.setUser(user);
        String maximum = "á".repeat(250);
        assertEquals(500, maximum.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(maximum, service.updateTreatment(1L, 3L, " " + maximum + " ").treatment());
        for (String invalid : List.of(" ", "a".repeat(501), "á".repeat(251)))
            assertThrows(IllegalArgumentException.class, () -> service.updateTreatment(1L, 3L, invalid));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(1L, 3L, null));
        assertEquals(maximum, quarantine.getTreatment()); assertNull(quarantine.getDateResolution());
    }
    @Test void closingWithoutCorrectionPreservesRejectedDataAndPreventsFurtherChanges() {
        quarantine.setUser(user);
        var result = service.closeWithoutCorrection(1L, 3L, " Fonte sem dados suficientes para corrigir. ");
        assertEquals("ENCERRADA_SEM_CORRECAO", result.status());
        assertEquals("Fonte sem dados suficientes para corrigir.", result.treatment()); assertNotNull(result.dateResolution());
        assertEquals(3L, result.responsibleId());
        assertEquals("REJEITADO; codigos=INVALID_TYPE", result.reason());
        assertEquals(IngestionStatus.ERRO, ingestion.getIngStatus());
        assertEquals("bruta/original.csv", file.getLocation());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.updateTreatment(1L, 3L, "Alterar histórico")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.claim(1L, 4L)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.resolve(1L, 3L, "Resolver novamente")).getStatusCode().value());
    }
    @Test void observationCannotResolveQuarantineEvenWhenItClaimsTheFileWasCorrected() {
        quarantine.setUser(user); quarantine.setStatus("EM_ANALISE");
        quarantine.setTreatment("Observação anterior");
        var error = assertThrows(ResponseStatusException.class,
                () -> service.resolve(1L, 3L, "Arquivo corrigido e validado"));
        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("revalidação vinculada"));
        assertEquals("EM_ANALISE", quarantine.getStatus());
        assertEquals("Observação anterior", quarantine.getTreatment());
        assertNull(quarantine.getDateResolution());
        assertEquals(IngestionStatus.ERRO, ingestion.getIngStatus());
        assertEquals("bruta/original.csv", file.getLocation());
        verify(repository, never()).save(any());
    }
    @Test void closingRequiresJustificationWithinByteLimitAndLeavesStateUntouchedOnFailure() {
        quarantine.setUser(user); quarantine.setStatus("EM_ANALISE");
        for (String invalid : List.of(" ", "a".repeat(501), "á".repeat(251)))
            assertThrows(IllegalArgumentException.class,
                    () -> service.closeWithoutCorrection(1L, 3L, invalid));
        assertThrows(IllegalArgumentException.class, () -> service.closeWithoutCorrection(1L, 3L, null));
        assertEquals("EM_ANALISE", quarantine.getStatus());
        assertNull(quarantine.getTreatment()); assertNull(quarantine.getDateResolution());
        verify(repository, never()).save(any());
    }
    @Test void terminalStatusesRemainClosedEvenWhenResolutionDateIsMissing() {
        quarantine.setUser(user);
        for (String terminal : List.of("RESOLVIDA", "ENCERRADA_SEM_CORRECAO")) {
            quarantine.setStatus(terminal);
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> service.claim(1L, 3L)).getStatusCode().value());
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> service.updateTreatment(1L, 3L, "Alterar")).getStatusCode().value());
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> service.closeWithoutCorrection(1L, 3L, "Encerrar novamente")).getStatusCode().value());
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> service.resolve(1L, 3L, "Resolver")).getStatusCode().value());
            assertEquals(terminal, quarantine.getStatus());
        }
        verify(repository, never()).save(any());
    }
    @Test void inactiveUsersCannotClaimOrWrite() {
        user.setUserStatus(0);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.claim(1L, 3L)).getStatusCode().value());
        quarantine.setUser(user);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.updateTreatment(1L, 3L, "Texto")).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.closeWithoutCorrection(1L, 3L, "Sem correção")).getStatusCode().value());
        verify(repository, never()).findLockedById(any()); verify(repository, never()).save(any());
    }
    @Test void listAppliesFiltersPaginationAndStableOrdering() {
        when(repository.search(eq("EM_ANALISE"), eq(3L), eq(7L), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(quarantine)));
        var result = service.list(" em_analise ", 3L, 7L, false, 0, 20);
        assertEquals(1, result.totalElements()); assertEquals(1L, result.content().getFirst().id());
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq("EM_ANALISE"), eq(3L), eq(7L), eq(false), page.capture());
        assertEquals(20, page.getValue().getPageSize());
        assertTrue(page.getValue().getSort().getOrderFor("dateEntry").isDescending());
        assertTrue(page.getValue().getSort().getOrderFor("id").isDescending());
    }
    @Test void invalidFiltersAndMissingRecordsFailWithoutMutations() {
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, null, false, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null, null, false, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, 3L, null, true, 0, 20));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(99L)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.claim(99L, 3L)).getStatusCode().value());
        assertThrows(IllegalArgumentException.class, () -> service.claim(1L, 0L));
        verify(repository, never()).save(any());
    }
    @Test void ingestionLevelQuarantineMayHaveNoFile() {
        quarantine.setFile(null);
        assertNull(service.get(1L).fileId()); assertNull(service.get(1L).validationReportUrl());
    }
}
