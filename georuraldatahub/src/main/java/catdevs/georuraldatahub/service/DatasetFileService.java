package catdevs.georuraldatahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;

@Service
public class DatasetFileService {

    private final DatasetRepository datasets;
    private final FileRepository files;

    public DatasetFileService(
            DatasetRepository datasets,
            FileRepository files) {

        this.datasets = datasets;
        this.files = files;
    }

    @Transactional
    public void associate(Long datasetId, Long fileId) {

        // Primeiro bloqueia o conjunto.
        Dataset dataset = dataset(datasetId);

        // Só depois procura o arquivo.
        File file = file(fileId);

        // A associação é feita pela coleção gerenciada pelo JPA.
        // O arquivo não é copiado nem salvo novamente.
        boolean alreadyAssociated = dataset.getFiles()
                .stream()
                .anyMatch(existing ->
                        existing.getId() != null
                                && existing.getId().equals(fileId));

        if (!alreadyAssociated) {
            dataset.getFiles().add(file);
        }
    }

    @Transactional
    public void dissociate(Long datasetId, Long fileId) {

        // Primeiro confirma que o conjunto existe e está bloqueado.
        Dataset dataset = dataset(datasetId);

        // Confirma que o arquivo existe antes de modificar a coleção.
        file(fileId);

        // Remove apenas a associação com este conjunto.
        // Não exclui o arquivo nem altera outras associações.
        dataset.getFiles().removeIf(existing ->
                existing.getId() != null
                        && existing.getId().equals(fileId));
    }

    private Dataset dataset(Long datasetId) {
        return datasets.findLockedById(datasetId)
                .orElseThrow(() -> new DataSetNotFoundException(
                        "Conjunto não encontrado."));
    }

    private File file(Long fileId) {
        return files.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado."));
    }
}