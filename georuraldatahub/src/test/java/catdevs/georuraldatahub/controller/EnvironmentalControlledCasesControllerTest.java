package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository;
import catdevs.georuraldatahub.service.EnvironmentalIndicatorService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EnvironmentalControlledCasesControllerTest {
    @Test void preservaResultadosControladosDosSeteIndicadoresSemCalculosNoJava() throws Exception {
        var repository = mock(EnvironmentalIndicatorRepository.class);
        var mvc = MockMvcBuilders.standaloneSetup(new EnvironmentalIndicatorController(
                new EnvironmentalIndicatorService(repository))).build();
        String[] indicators = {"icv", "irl", "iapp", "isap", "iae", "idesmat", "ifc"};
        // Os mesmos resultados esperados pelo teste espacial da área de 100 ha.
        String[] results = {
            "\"ICV\":{\"areaHa\":60,\"percentual\":60}",
            "\"IRL\":{\"areaHa\":10,\"percentual\":10,\"minimoPercentual\":20,\"deficitHa\":10,\"excedenteHa\":0}",
            "\"IAPP\":{\"areaHa\":20,\"percentual\":50,\"appTotalHa\":40,\"passivoHa\":20}",
            "\"ISAP\":{\"areaHa\":50,\"percentual\":50}",
            "\"IAE\":{\"areaHa\":10,\"percentual\":10}",
            "\"IDesmat2008\":{\"areaHa\":60,\"percentual\":60},\"IDesmat2019\":{\"areaHa\":50,\"percentual\":50},\"IDesmat2020\":{\"areaHa\":30,\"percentual\":30}",
            "\"IFC\":{\"quantidade\":2,\"porMilHa\":20}"
        };
        for (int i = 0; i < indicators.length; i++) {
            String json = "{\"areaImovelHa\":100,\"regra\":\"GEORURAL-1.3\",\"fontes\":[]," + results[i] + "}";
            if (indicators[i].equals("icv")) when(repository.calculateIcv(1, "{}")).thenReturn(json);
            else when(repository.calculateIndicator(1, "{}", indicators[i])).thenReturn(json);
            mvc.perform(post("/imoveis/1/indicadores/" + indicators[i]).contentType("application/json").content("{}"))
                    .andExpect(status().isOk()).andExpect(content().json(json));
        }
        when(repository.calculateIndicator(1, "{}", "ifc"))
                .thenReturn("{\"IFC\":{\"quantidade\":null,\"porMilHa\":null,\"situacao\":\"SEM_DADOS\"}}");
        mvc.perform(post("/imoveis/1/indicadores/ifc").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(content().json(
                    "{\"IFC\":{\"quantidade\":null,\"porMilHa\":null,\"situacao\":\"SEM_DADOS\"}}"));
        verify(repository, never()).calculate(anyLong(), anyString());
        verify(repository, never()).calculateAndSave(anyLong(), anyString(), anyString());
    }
}
