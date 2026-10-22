
package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatasetFileService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void associate(Long datasetId, Long fileId) {
        Dataset dataset = dataset(datasetId);
        File file = file(fileId);

        // A relação JPA existente controla a associação.
        // Não é necessário copiar o arquivo nem o objeto do bucket.
        boolean alreadyAssociated = dataset.getFiles()
                .stream()
                .anyMatch(existing ->
                        existing.getId().equals(fileId));

        if (!alreadyAssociated) {
            dataset.getFiles().add(file);
        }
    }

    @Transactional
    public void dissociate(Long datasetId, Long fileId) {
        Dataset dataset = dataset(datasetId);
        file(fileId);

        dataset.getFiles().removeIf(existing ->
                existing.getId().equals(fileId));
    }

    private Dataset dataset(Long datasetId) {
        Dataset dataset = entityManager.find(
                Dataset.class,
                datasetId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (dataset == null) {
            throw new DataSetNotFoundException(
                    "Conjunto não encontrado."
            );
        }

        return dataset;
    }

    private File file(Long fileId) {
        File file = entityManager.find(File.class, fileId);

        if (file == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Arquivo não encontrado."
            );
        }

        return file;
    }
}
