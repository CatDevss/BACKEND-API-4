package catdevs.georuraldatahub.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import catdevs.georuraldatahub.collection.CollectionRequest;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;
import catdevs.georuraldatahub.service.DictionaryGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DictionaryGenerationControllerTest {
    private final DictionaryGenerationService service = mock(DictionaryGenerationService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DictionaryGenerationController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void collectionAcceptsAnEditionWithoutAnExistingDictionary() throws Exception {
        mvc.perform(post("/fontes/2/dicionarios/gerar-coleta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product\":\"IBGE_MUNICIPIOS\",\"userId\":7,\"competence\":\"2022\"}"))
                .andExpect(status().isCreated());
        verify(service).fromCollection(eq(2L), argThat(r -> r.dictionaryId() == null && r.product() == CollectionRequest.Product.IBGE_MUNICIPIOS && r.competence().equals("2022")));
    }
    @Test void uploadedFileUsesTheSourceAndSessionActor() throws Exception {
        mvc.perform(multipart("/fontes/2/dicionarios/gerar-arquivo").param("userId", "7")
                .file(new MockMultipartFile("file", "malha.csv", "text/csv", "id\n1".getBytes())))
                .andExpect(status().isCreated());
        verify(service).fromUpload(eq(2L), eq(7L), argThat(f -> f.getOriginalFilename().equals("malha.csv")));
    }
    @Test void missingEditionUserAndFileStopBeforeGeneration() throws Exception {
        mvc.perform(post("/fontes/2/dicionarios/gerar-coleta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"product\":\"IBGE_MUNICIPIOS\",\"userId\":7}"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/fontes/2/dicionarios/gerar-arquivo").param("userId", "7"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
