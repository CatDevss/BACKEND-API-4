package catdevs.georuraldatahub.controller;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import catdevs.georuraldatahub.repository.EnvironmentalLayerDataRepository;
import catdevs.georuraldatahub.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class EnvironmentalReportControllerTest {
    @Test
    void removesReportAndCalculationOnlyWithUserParameter() throws Exception {
        var reports = mock(EnvironmentalReportService.class);
        var mvc =
            MockMvcBuilders
                .standaloneSetup(new EnvironmentalReportController(reports,
                    mock(EnvironmentalIndicatorService.class), mock(EnvironmentalLayerDataRepository.class)))
                .build();
        mvc.perform(delete("/ambiental/relatorios/r").param("usuarioId", "4"))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/ambiental/calculos/21").param("usuarioId", "4"))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/ambiental/calculos/21")).andExpect(status().isBadRequest());
        verify(reports).delete("r", 4);
        verify(reports).deleteCalculation(21, 4);
        verifyNoMoreInteractions(reports);
    }
}
