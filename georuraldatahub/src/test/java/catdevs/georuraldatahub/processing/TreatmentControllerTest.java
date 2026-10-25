package catdevs.georuraldatahub.processing;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import catdevs.georuraldatahub.controller.TreatmentController;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;
import catdevs.georuraldatahub.service.BucketService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TreatmentControllerTest {
    @Test void unavailableSchemaReturns503AndInvalidBodiesReturn400WithoutStartingWork() throws Exception {
        var input = mock(TreatmentInputService.class);
        var service = new TreatmentService(Optional.empty(), input, mock(VectorTreatment.class), mock(BucketService.class));
        var mvc = MockMvcBuilders.standaloneSetup(new TreatmentController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/arquivos/7/tratamentos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":3,\"datasetId\":1}")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/tratamentos/20")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/arquivos/7/tratamentos")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/rasters/1")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/rasters")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/arquivos/7/tratamentos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":0}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/tratamentos/20")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/tratamentos/20")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(input);
    }
}
