package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.collection.*;
import catdevs.georuraldatahub.service.FileService;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;

class ApiErrorResponseTest {
    private final CollectionService collection = mock(CollectionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CollectionController(new CollectionCatalog(), collection, mock(FileService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final String json = "{\"product\":\"IBGE_MUNICIPIOS\",\"userId\":2,\"competence\":\"2022\"}";

    @Test void collectionSourceFailuresReturnTheirReasonAndStatusInTheHttpBody() throws Exception {
        for (var status : List.of(HttpStatus.NOT_FOUND, HttpStatus.BAD_GATEWAY, HttpStatus.GATEWAY_TIMEOUT)) {
            when(collection.collect(eq(4L), any())).thenThrow(new ResponseStatusException(status, "Canal oficial: competência indisponível ou fonte sem resposta."));
            mvc.perform(post("/fontes/4/coletas").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is(status.value())).andExpect(content().string("Canal oficial: competência indisponível ou fonte sem resposta."));
        }
    }
    @Test void invalidProductIsNotAnEmptyResponseOrInternalServerError() throws Exception {
        mvc.perform(post("/fontes/4/coletas").contentType(MediaType.APPLICATION_JSON).content(json.replace("IBGE_MUNICIPIOS", "IBGE")))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("produto")));
        verifyNoInteractions(collection);
    }
    @Test void dictionaryTemplatesAreReadOnlyAndInvalidProductsHaveClearErrors() throws Exception {
        mvc.perform(get("/fontes/conectores/INPE_PRODES/dicionario")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[?(@.name == 'update_at')].type").value(org.hamcrest.Matchers.hasItem("STRING")));
        mvc.perform(get("/fontes/conectores/IBGE_BIOMAS/dicionario").param("competence", "2025"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rules[0].name").value("CD_BIOMA"));
        mvc.perform(get("/fontes/conectores/IBGE_BIOMAS/dicionario")).andExpect(status().is(422));
        mvc.perform(get("/fontes/conectores/PRODUTO_INVALIDO/dicionario")).andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("produto")));
        verifyNoInteractions(collection);
    }
    @Test void wrongHttpMethodKeeps405WithBodyInsteadOfBeingConvertedTo500() throws Exception {
        mvc.perform(delete("/fontes/conectores")).andExpect(status().isMethodNotAllowed())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.isEmptyString())));
    }
}
