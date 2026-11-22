package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDate;
import java.util.List;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EnvironmentalHistoryFiltersControllerTest {
    @Test void encaminhaFiltrosAntesDaPaginacaoDoHistorico() throws Exception {
        var repository = mock(EnvironmentalIndicatorRepository.class);
        var filters = new EnvironmentalHistoryFilter(8L, LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-20"));
        when(repository.history("Vera", "ifc", 1, 20, filters))
                .thenReturn(new EnvironmentalIndicatorRepository.Page<>(List.of(), 0, 1, 20));
        var mvc = MockMvcBuilders.standaloneSetup(new EnvironmentalIndicatorController(
                new EnvironmentalIndicatorService(repository))).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/ambiental/calculos").param("busca", "Vera").param("indicador", "ifc")
                .param("camadaId", "8").param("inicio", "2026-10-20").param("fim", "2026-10-20").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        verify(repository).history("Vera", "ifc", 1, 20, filters);
        verifyNoMoreInteractions(repository);
        mvc.perform(get("/ambiental/calculos").param("inicio", "2026-02-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/ambiental/calculos").param("inicio", "2026-10-21").param("fim", "2026-10-20"))
                .andExpect(status().is(422));
        verifyNoMoreInteractions(repository);
    }

    @Test void usaOsMesmosFiltrosNasExecucoesSalvas() throws Exception {
        var service = mock(EnvironmentalReportService.class);
        var filters = new EnvironmentalHistoryFilter(8L, null, LocalDate.parse("2026-10-20"));
        when(service.list("", "ifc", 0, 20, filters)).thenReturn(java.util.Map.of("items", List.of(), "total", 0));
        var mvc = MockMvcBuilders.standaloneSetup(new EnvironmentalReportController(service,
                mock(EnvironmentalIndicatorService.class), mock(EnvironmentalLayerDataRepository.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/ambiental/relatorios").param("indicador", "ifc").param("camadaId", "8").param("fim", "2026-10-20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        verify(service).list("", "ifc", 0, 20, filters);
        verifyNoMoreInteractions(service);
    }
}
