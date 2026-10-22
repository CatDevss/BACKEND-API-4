
package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.dto.DatasetUpdateRequestDTO;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DatasetService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public DatasetResponseDTO create(DatasetCreateRequestDTO request) {

        User user = entityManager.find(User.class, request.userId());

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Usuário não encontrado."
            );
        }

        Dataset dataset = new Dataset();
        dataset.setName(validName(request.name()));
        dataset.setUser(user);

        entityManager.persist(dataset);
        entityManager.flush();

        return toDto(dataset);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponseDTO> listAll() {

        return entityManager.createQuery(
                """
                SELECT d
                FROM Dataset d
                ORDER BY d.id DESC
                """,
                Dataset.class
        )
        .getResultList()
        .stream()
        .map(this::toDto)
        .toList();
    }

    @Transactional(readOnly = true)
    public Dataset findEntityById(Long id) {

        Dataset dataset = entityManager.find(Dataset.class, id);

        if (dataset == null) {
            throw new DataSetNotFoundException(
                    "Conjunto não encontrado."
            );
        }

        return dataset;
    }

    @Transactional
    public Dataset findLockedEntityById(Long id) {

        Dataset dataset = entityManager.find(
                Dataset.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (dataset == null) {
            throw new DataSetNotFoundException(
                    "Conjunto não encontrado."
            );
        }

        return dataset;
    }

    @Transactional(readOnly = true)
    public DatasetResponseDTO findById(Long id) {
        return toDto(findEntityById(id));
    }

    @Transactional
    public DatasetResponseDTO update(
            Long id,
            DatasetUpdateRequestDTO request) {

        Dataset dataset = findLockedEntityById(id);
        dataset.setName(validName(request.name()));

        return toDto(dataset);
    }

    @Transactional
    public void delete(Long id) {

        Dataset dataset = findLockedEntityById(id);

        if (dataset.getFiles() != null && !dataset.getFiles().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Conjunto possui arquivos e não pode ser excluído."
            );
        }

        entityManager.remove(dataset);
    }

    @Transactional(readOnly = true)
    public List<FileResponseDTO> listFiles(Long datasetId) {

        findEntityById(datasetId);

        return buscarArquivosPorDataset(datasetId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private List<File> buscarArquivosPorDataset(Long datasetId) {

        return entityManager.createQuery(
                """
                SELECT f
                FROM Dataset d JOIN d.files f
                WHERE d.id = :datasetId
                ORDER BY f.id DESC
                """,
                File.class
        )
        .setParameter("datasetId", datasetId)
        .getResultList();
    }

    private String validName(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Nome do conjunto é obrigatório."
            );
        }

        String name = value.trim();

        if (name.getBytes(StandardCharsets.UTF_8).length > 100) {
            throw new IllegalArgumentException(
                    "Nome do conjunto excede o limite de 100 bytes do banco atual."
            );
        }

        return name;
    }

    private DatasetResponseDTO toDto(Dataset dataset) {

        return new DatasetResponseDTO(
                dataset.getId(),
                dataset.getName(),
                dataset.getUser() != null
                        ? dataset.getUser().getId()
                        : null
        );
    }

    private FileResponseDTO toDto(File file) {

        return new FileResponseDTO(
                file.getId(),
                file.getName(),
                file.getFormatFile(),
                file.getSize(),
                file.getHash(),
                file.getLocation(),
                file.getIngestion() != null
                        ? file.getIngestion().getId()
                        : null,
                file.getReceivedDate(),
                file.getIngestion() != null
                        && file.getIngestion().getUser() != null
                        ? file.getIngestion().getUser().getId()
                        : null
        );
    }
}
