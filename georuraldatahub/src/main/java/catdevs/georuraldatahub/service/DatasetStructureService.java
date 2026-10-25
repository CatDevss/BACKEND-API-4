package catdevs.georuraldatahub.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import catdevs.georuraldatahub.dto.DatasetStructureDTO;
import catdevs.georuraldatahub.dto.DatasetStructureDTO.FileEntry;
import catdevs.georuraldatahub.dto.DatasetStructureDTO.SourceEntry;
import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.repository.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetStructureService {
    private final DatasetService datasets;
    private final FileRepository files;
    private final LayerService layers;

    public DatasetStructureService(DatasetService datasets, FileRepository files, LayerService layers) {
        this.datasets = datasets;
        this.files = files;
        this.layers = layers;
    }

    @Transactional(readOnly = true)
    public DatasetStructureDTO get(Long datasetId) {
        var dataset = datasets.findEntityById(datasetId);
        var associated = files.findByDatasetId(datasetId);
        Map<Long, List<LayerInfoDTO>> previews = layers.listLayers(datasetId).stream()
                .collect(Collectors.groupingBy(LayerInfoDTO::fileId));
        Map<Long, List<File>> grouped = new LinkedHashMap<>();
        for (var file : associated) {
            Long sourceId = file.getIngestion().getDictionary().getSource().getId();
            grouped.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(file);
        }
        List<SourceEntry> sources = grouped.values().stream().map(group -> {
            var source = group.getFirst().getIngestion().getDictionary().getSource();
            List<FileEntry> entries = group.stream().map(file -> {
                var ingestion = file.getIngestion();
                var dictionary = ingestion.getDictionary();
                return new FileEntry(file.getId(), file.getName(), file.getFormatFile(), file.getSize(),
                        file.getReceivedDate(), ingestion.getId(), dictionary.getId(), dictionary.getVersion(),
                        previews.getOrDefault(file.getId(), List.of()));
            }).toList();
            return new SourceEntry(source.getId(), source.getName(), source.getUrl(), entries);
        }).toList();
        return new DatasetStructureDTO(dataset.getId(), dataset.getName(),
                dataset.getUser() == null ? null : dataset.getUser().getId(), sources);
    }
}
