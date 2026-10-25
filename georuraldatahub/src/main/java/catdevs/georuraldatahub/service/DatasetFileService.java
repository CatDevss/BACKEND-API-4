package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatasetFileService {
    private final DatasetRepository datasets;
    private final FileRepository files;

    public DatasetFileService(DatasetRepository datasets, FileRepository files) {
        this.datasets = datasets;
        this.files = files;
    }

    @Transactional
    public void associate(Long datasetId, Long fileId) {
        var dataset = dataset(datasetId);
        var file = file(fileId);
        if (dataset.getFiles().stream().noneMatch(existing -> existing.getId().equals(fileId))) {
            dataset.getFiles().add(file);
        }
    }

    @Transactional
    public void dissociate(Long datasetId, Long fileId) {
        var dataset = dataset(datasetId);
        file(fileId);
        dataset.getFiles().removeIf(existing -> existing.getId().equals(fileId));
    }

    private Dataset dataset(Long datasetId) {
        return datasets.findLockedById(datasetId)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
    }

    private File file(Long fileId) {
        return files.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
    }
}
