package catdevs.georuraldatahub.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import catdevs.georuraldatahub.collection.*;
import catdevs.georuraldatahub.exception.GlobalExceptionHandler;
import catdevs.georuraldatahub.service.*;

class SourceIngestionControllerTest {
    private final FileService files = mock(FileService.class);
    private final SourceService sources = mock(SourceService.class);
    private final CollectionService collections = mock(CollectionService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new SourceFileController(files, sources),
                new CollectionController(mock(CollectionCatalog.class), collections, files))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void uploadRouteRequiresOnlySourceUserAndMultipartFile() throws Exception {
        mvc.perform(multipart("/fontes/4/arquivos").file(new MockMultipartFile("file", "test.csv", "text/csv", "id\n1".getBytes()))
                .param("userId", "2")).andExpect(status().isCreated());
        verify(files).uploadToSource(any(), eq(4L), eq(2L));
    }
    @Test void sourceListDoesNotRequireDatasetAndReturnsNotFoundForMissingSource() throws Exception {
        when(files.findBySourceId(4L)).thenReturn(List.of());
        mvc.perform(get("/fontes/4/arquivos")).andExpect(status().isOk()).andExpect(content().json("[]"));
        when(sources.findEntityById(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/fontes/99/arquivos")).andExpect(status().isNotFound());
        verify(files, never()).findBySourceId(99L);
    }
    @Test void collectionJsonMayOmitDatasetButStillRequiresUserAndPositiveIds() throws Exception {
        String valid = "{\"product\":\"IBGE_MUNICIPIOS\",\"userId\":2,\"competence\":\"2022\"}";
        mvc.perform(post("/fontes/4/coletas").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated());
        verify(collections).collect(eq(4L), argThat(r -> r.datasetId() == null && r.userId() == 2L));
        clearInvocations(collections);
        mvc.perform(post("/fontes/4/coletas").contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("\"userId\":2,", ""))).andExpect(status().isBadRequest());
        mvc.perform(post("/fontes/4/coletas").contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("\"userId\":2", "\"userId\":2,\"datasetId\":0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(collections);
    }
}
