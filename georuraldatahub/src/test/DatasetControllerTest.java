package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.service.DatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DatasetController.class)
class DatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatasetService datasetService;

    @Test
    void deveAceitarUploadMultipart() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dados.csv",
                "text/csv",
                "id,nome\n1,teste".getBytes()
        );

        mockMvc.perform(
                multipart("/conjuntos/10/arquivos")
                        .file(file)
        ).andExpect(status().isOk());
    }
}