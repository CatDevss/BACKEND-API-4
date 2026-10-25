package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.repository.FileRepository;

class DatasetStructureServiceTest {
    private final DatasetService datasets = mock(DatasetService.class);
    private final FileRepository files = mock(FileRepository.class);
    private final LayerService layers = mock(LayerService.class);
    private final DatasetStructureService service = new DatasetStructureService(datasets, files, layers);

    private void dataset() {
        var dataset = new Dataset(); dataset.setId(1L); dataset.setName("Paraná");
        when(datasets.findEntityById(1L)).thenReturn(dataset);
    }
    private File file(long id, long sourceId, int version) {
        var source = new Source(); source.setId(sourceId); source.setName("Fonte"); source.setUrl("https://www.ibge.gov.br/");
        var dictionary = new Dictionary(); dictionary.setId(id + 100); dictionary.setVersion(version); dictionary.setSource(source);
        var ingestion = new DataIngestion(); ingestion.setId(id + 200); ingestion.setDictionary(dictionary);
        var file = new File(); file.setId(id); file.setName(id + ".geojson"); file.setFormatFile("geojson"); file.setIngestion(ingestion);
        return file;
    }
    @Test void groupsDictionaryVersionsBySourceIdAndKeepsFilesWithoutPreviewVisible() {
        dataset();
        when(files.findByDatasetId(1L)).thenReturn(List.of(file(3, 10, 2), file(2, 20, 1), file(1, 10, 1)));
        when(layers.listLayers(1L)).thenReturn(List.of(new LayerInfoDTO(3L, "3.geojson", "geojson", 50L, null)));
        var result = service.get(1L);
        assertEquals(2, result.sources().size());
        var first = result.sources().getFirst();
        assertEquals(10L, first.id());
        assertEquals(List.of(3L, 1L), first.files().stream().map(f -> f.id()).toList());
        assertEquals(List.of(2, 1), first.files().stream().map(f -> f.dictionaryVersion()).toList());
        assertEquals(103L, first.files().getFirst().dictionaryId());
        assertEquals(1, first.files().getFirst().layers().size());
        assertTrue(first.files().getLast().layers().isEmpty());
        assertTrue(result.sources().getLast().files().getFirst().layers().isEmpty());
    }
    @Test void emptyDatasetHasEmptySourcesInsteadOfInventedGroups() {
        dataset(); when(files.findByDatasetId(1L)).thenReturn(List.of()); when(layers.listLayers(1L)).thenReturn(List.of());
        assertTrue(service.get(1L).sources().isEmpty());
    }
    @Test void missingDatasetStopsBeforeLookingUpFilesAndLayers() {
        when(datasets.findEntityById(99L)).thenThrow(new DataSetNotFoundException("Conjunto não encontrado."));
        assertThrows(DataSetNotFoundException.class, () -> service.get(99L));
        verifyNoInteractions(files, layers);
    }
}
