package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import catdevs.georuraldatahub.collection.*;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CollectionVerificationControllerTest {
    private final PublicDataConnectors connectors = mock(PublicDataConnectors.class);
    private final PublishedResources resources = mock(PublishedResources.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new CollectionVerificationController(resources,connectors))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    @Test void verifiesWithoutUserSourceOrDatasetAndDistinguishesEmptyResults() throws Exception {
        when(connectors.collect(any())).thenReturn(new CollectedData("a.geojson","geojson",
                "{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8),List.of(),null));
        mvc.perform(post("/fontes/conectores/ICMBIO/verificacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"competence\":\"ATUAL\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEM_REGISTROS")).andExpect(jsonPath("$.records").value(0));
        verify(connectors).collect(argThat(r -> r.userId()==null && r.datasetId()==null && r.product()==CollectionRequest.Product.ICMBIO));
    }
    @Test void missingCompetenceAndUnknownProductFailBeforeDownload() throws Exception {
        mvc.perform(post("/fontes/conectores/MMA/verificacao").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/fontes/conectores/UNKNOWN/verificacao").contentType(MediaType.APPLICATION_JSON).content("{\"competence\":\"2024\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(connectors);
    }
    @Test void upstreamFailureRemainsAnErrorInsteadOfReportingAvailable() throws Exception {
        when(connectors.collect(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,"Fonte retornou HTML."));
        mvc.perform(post("/fontes/conectores/MMA/verificacao").contentType(MediaType.APPLICATION_JSON).content("{\"competence\":\"2024\"}"))
                .andExpect(status().isBadGateway());
    }
    @Test void transportFailuresAreReportedAsBadGateway() throws Exception {
        when(connectors.collect(any())).thenThrow(new java.io.IOException("Falha TLS do canal oficial."));
        mvc.perform(post("/fontes/conectores/FUNAI/verificacao").contentType(MediaType.APPLICATION_JSON).content("{\"competence\":\"ATUAL\"}"))
                .andExpect(status().isBadGateway());
        when(resources.availability(any())).thenThrow(new java.io.IOException("Catálogo indisponível."));
        mvc.perform(get("/fontes/conectores/MMA/edicoes")).andExpect(status().isBadGateway());
    }
}
