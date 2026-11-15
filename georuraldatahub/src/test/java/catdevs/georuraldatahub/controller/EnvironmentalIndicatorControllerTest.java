package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository;
import catdevs.georuraldatahub.service.EnvironmentalIndicatorService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EnvironmentalIndicatorControllerTest {
    @Test void allRoutesPassMultipleLayersToOracleWithoutChangingResults() throws Exception {
        var repository = mock(EnvironmentalIndicatorRepository.class);
        var mvc = MockMvcBuilders.standaloneSetup(new EnvironmentalIndicatorController(
            new EnvironmentalIndicatorService(repository))).build();
        String[] names = {"icv", "irl", "iapp", "isap", "iae", "idesmat", "ifc"};
        String[] bodies = {
            "{\"vegetacao\":[28,35]}",
            "{\"reservaLegal\":[30,36],\"rlMinimoPercentual\":20,\"fundamentoRl\":\"Teste\"}",
            "{\"app\":[31,37],\"vegetacao\":[28,35]}",
            "{\"areasProtegidas\":[32,38]}",
            "{\"embargos\":[33,39]}",
            "{\"desmatamento\":[34,40],\"fim\":\"2026-11-15\"}",
            "{\"focos\":[41,42],\"inicio\":\"2021-01-01\",\"fim\":\"2026-11-15\"}"
        };
        String result = "{\"valorOracle\":12.3456789}";
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals("icv")) when(repository.calculateIcv(928, bodies[i])).thenReturn(result);
            else when(repository.calculateIndicator(928, bodies[i], names[i])).thenReturn(result);
            mvc.perform(post("/imoveis/928/indicadores/" + names[i])
                .contentType("application/json").content(bodies[i]))
                .andExpect(status().isOk()).andExpect(content().json(result));
            if (names[i].equals("icv")) verify(repository).calculateIcv(928, bodies[i]);
            else verify(repository).calculateIndicator(928, bodies[i], names[i]);
        }
        when(repository.calculate(928, bodies[0])).thenReturn(result);
        mvc.perform(post("/imoveis/928/indicadores/calcular")
            .contentType("application/json").content(bodies[0]))
            .andExpect(status().isOk()).andExpect(content().json(result));
        verify(repository).calculate(928, bodies[0]);
        mvc.perform(post("/imoveis/928/indicadores/icv").contentType("application/json"))
            .andExpect(status().isBadRequest());
    }
}
