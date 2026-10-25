package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import catdevs.georuraldatahub.entity.Quarantine;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;
import catdevs.georuraldatahub.repository.QuarantineRepository;
import catdevs.georuraldatahub.service.QuarantineService;
import catdevs.georuraldatahub.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QuarantineControllerTest {
    private final QuarantineRepository repository = mock(QuarantineRepository.class);
    private final UserService users = mock(UserService.class);
    private final Quarantine quarantine = new Quarantine();
    private MockMvc mvc;

    @BeforeEach void setup() {
        quarantine.setId(1L); quarantine.setStatus("REGISTROS_REJEITADOS"); quarantine.setReason("INVALID_TYPE: id");
        when(repository.findById(1L)).thenReturn(Optional.of(quarantine));
        when(repository.findLockedById(1L)).thenReturn(Optional.of(quarantine));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var user = new User(); user.setId(3L); user.setName("Analista"); user.setUserStatus(1);
        when(users.findEntityById(3L)).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new QuarantineController(new QuarantineService(repository, users)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void volunteerWorkflowBlocksUnverifiedResolutionAndAllowsClosingWithoutCorrection() throws Exception {
        mvc.perform(get("/quarentenas/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTROS_REJEITADOS"))
                .andExpect(jsonPath("$.responsibleId").doesNotExist());
        mvc.perform(post("/quarentenas/1/assumir").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responsibleId").value(3))
                .andExpect(jsonPath("$.status").value("EM_ANALISE"));
        mvc.perform(patch("/quarentenas/1/observacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"treatment\":\"Conferindo os tipos com a fonte\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.treatment").value("Conferindo os tipos com a fonte"));
        mvc.perform(post("/quarentenas/1/resolver").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"treatment\":\"Arquivo corrigido reenviado para validação\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/quarentenas/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANALISE"))
                .andExpect(jsonPath("$.treatment").value("Conferindo os tipos com a fonte"))
                .andExpect(jsonPath("$.dateResolution").doesNotExist());
        mvc.perform(post("/quarentenas/1/encerrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"treatment\":\"Dados insuficientes para corrigir\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENCERRADA_SEM_CORRECAO"))
                .andExpect(jsonPath("$.dateResolution").exists()).andExpect(jsonPath("$.reason").value("INVALID_TYPE: id"));
        mvc.perform(patch("/quarentenas/1/observacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"treatment\":\"Alterar\"}")).andExpect(status().isConflict());
    }
    @Test void listSupportsAvailableQueueAndPagination() throws Exception {
        when(repository.search(isNull(), isNull(), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(quarantine)));
        mvc.perform(get("/quarentenas").param("unassigned", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void invalidOrMissingActorAndEmptyObservationAreRejectedBeforeMutation() throws Exception {
        for (String body : List.of("{}", "{\"userId\":0}", "{\"userId\":-3}"))
            mvc.perform(post("/quarentenas/1/assumir").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        mvc.perform(patch("/quarentenas/1/observacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"treatment\":\" \"}")).andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"userId\":3,\"treatment\":\" \"}",
                "{\"userId\":0,\"treatment\":\"Justificativa\"}"))
            mvc.perform(post("/quarentenas/1/encerrar").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }
    @Test void missingQuarantineReturns404AndDeletionIsUnavailable() throws Exception {
        mvc.perform(get("/quarentenas/99")).andExpect(status().isNotFound());
        mvc.perform(delete("/quarentenas/1")).andExpect(status().isMethodNotAllowed());
        verify(repository, never()).delete(any());
    }
}
