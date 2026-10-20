package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import catdevs.georuraldatahub.dto.DatasetStructureDTO;
import catdevs.georuraldatahub.exception.*;
import catdevs.georuraldatahub.service.DatasetStructureService;

class DatasetStructureControllerTest {
    @Test void exposesReadOnlyStructureAndReturns404ForMissingDataset() throws Exception {
        var service = mock(DatasetStructureService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DatasetStructureController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.get(1L)).thenReturn(new DatasetStructureDTO(1L, "Paraná", 2L,
                List.of(new DatasetStructureDTO.SourceEntry(4L, "IBGE", "https://www.ibge.gov.br/", List.of()))));
        mvc.perform(get("/conjuntos/1/estrutura")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.sources[0].id").value(4))
                .andExpect(jsonPath("$.sources[0].files").isArray());
        when(service.get(99L)).thenThrow(new DataSetNotFoundException("Conjunto não encontrado."));
        mvc.perform(get("/conjuntos/99/estrutura")).andExpect(status().isNotFound());
    }
}
