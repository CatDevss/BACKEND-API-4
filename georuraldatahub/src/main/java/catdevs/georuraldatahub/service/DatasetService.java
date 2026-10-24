package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.dto.DatasetUpdateRequestDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.repository.DatasetRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DatasetService {

    private static final int MAX_NAME_BYTES = 100;

    private final DatasetRepository datasetRepository;
    private final UserService userService;

    public DatasetService(DatasetRepository datasetRepository, UserService userService) {
        this.datasetRepository = datasetRepository;
        this.userService = userService;
    }

    @Transactional
    public DatasetResponseDTO create(DatasetCreateRequestDTO request) {
        String name = validateName(request.name());
        User user = userService.findEntityById(request.userId());

        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setUser(user);

        Dataset saved = datasetRepository.save(dataset);
        if (saved == null) {
            throw new IllegalStateException("Não foi possível salvar o conjunto.");
        }
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponseDTO> listAll() {
        return datasetRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Dataset findEntityById(Long id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
    }

    @Transactional
    public Dataset findLockedEntityById(Long id) {
        return datasetRepository.findLockedById(id)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
    }

    @Transactional(readOnly = true)
    public DatasetResponseDTO findById(Long id) {
        return toDto(findEntityById(id));
    }

    @Transactional
    public DatasetResponseDTO update(Long id, DatasetUpdateRequestDTO request) {
        String name = validateName(request.name());
        Dataset dataset = findEntityById(id);
        dataset.setName(name);
        return toDto(dataset);
    }

    @Transactional
    public void delete(Long id) {
        Dataset dataset = findLockedEntityById(id);

        boolean hasFiles = datasetRepository.hasFiles(id)
                || (dataset.getFiles() != null && !dataset.getFiles().isEmpty());

        if (hasFiles) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Conjunto possui arquivos e não pode ser excluído.");
        }

        datasetRepository.delete(dataset);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do conjunto é obrigatório.");
        }

        String normalized = name.trim();
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            throw new IllegalArgumentException(
                    "Nome do conjunto não pode ultrapassar " + MAX_NAME_BYTES + " bytes em UTF-8.");
        }
        return normalized;
    }

    private DatasetResponseDTO toDto(Dataset dataset) {
        return new DatasetResponseDTO(
                dataset.getId(),
                dataset.getName(),
                dataset.getUser() != null ? dataset.getUser().getId() : null);
    }
}
