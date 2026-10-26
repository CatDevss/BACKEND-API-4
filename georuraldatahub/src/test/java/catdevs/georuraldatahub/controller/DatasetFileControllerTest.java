package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import catdevs.georuraldatahub.service.DatasetFileService;

class DatasetFileControllerTest {
    @Test void associationAndDissociationUseSeparateRoutesWithoutMultipartUpload() throws Exception {
        var membership = mock(DatasetFileService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DatasetFileController(membership)).build();
        mvc.perform(put("/conjuntos/1/arquivos/6")).andExpect(status().isNoContent());
        mvc.perform(delete("/conjuntos/1/arquivos/6")).andExpect(status().isNoContent());
        verify(membership).associate(1L, 6L);
        verify(membership).dissociate(1L, 6L);
    }
}
