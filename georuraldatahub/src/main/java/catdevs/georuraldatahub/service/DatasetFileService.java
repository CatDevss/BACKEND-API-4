package catdevs.georuraldatahub.service;

import java.util.Objects;

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
        Dataset dataset = findLockedDataset(datasetId);
        File file = findFile(fileId);
        boolean alreadyAssociated = dataset.getFiles().stream()
                .anyMatch(existing -> Objects.equals(existing.getId(), fileId));
        if (!alreadyAssociated) {
            dataset.getFiles().add(file);
        }
    }

    @Transactional
    public void dissociate(Long datasetId, Long fileId) {
        Dataset dataset = findLockedDataset(datasetId);
        findFile(fileId);
        dataset.getFiles().removeIf(existing -> Objects.equals(existing.getId(), fileId));
    }

    private Dataset findLockedDataset(Long datasetId) {
        return datasets.findLockedById(datasetId)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
    }

    private File findFile(Long fileId) {
        return files.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
    }
}
