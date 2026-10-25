package catdevs.georuraldatahub.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.dto.DatasetUpdateRequestDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.repository.DatasetRepository;

@Service
public class DatasetService {

    private static final int MAX_NAME_BYTES = 100;

    private final DatasetRepository datasetRepository;
    private final UserService userService;

    public DatasetService(
            DatasetRepository datasetRepository,
            UserService userService) {

        this.datasetRepository = datasetRepository;
        this.userService = userService;
    }

    @Transactional
    public DatasetResponseDTO create(DatasetCreateRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Os dados do conjunto são obrigatórios.");
        }

        String name = validateName(request.name());

        User user = userService.findEntityById(request.userId());

        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setUser(user);

        Dataset saved = datasetRepository.save(dataset);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponseDTO> listAll() {
        return datasetRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Dataset findEntityById(Long id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new DataSetNotFoundException(
                        "Conjunto não encontrado."));
    }

    @Transactional
    public Dataset findLockedEntityById(Long id) {
        return datasetRepository.findLockedById(id)
                .orElseThrow(() -> new DataSetNotFoundException(
                        "Conjunto não encontrado."));
    }

    @Transactional(readOnly = true)
    public DatasetResponseDTO findById(Long id) {
        return toDto(findEntityById(id));
    }

    @Transactional
    public DatasetResponseDTO update(
            Long id,
            DatasetUpdateRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Os dados para atualização são obrigatórios.");
        }

        // Validar antes de alterar a entidade evita mudanças parciais.
        String name = validateName(request.name());

        Dataset dataset = findEntityById(id);
        dataset.setName(name);

        return toDto(dataset);
    }

    @Transactional
    public void delete(Long id) {

        // Bloqueia o conjunto antes de verificar se ele pode ser excluído.
        Dataset dataset = findLockedEntityById(id);

        boolean hasAssociatedFiles =
                dataset.getFiles() != null
                        && !dataset.getFiles().isEmpty();

        // A consulta também protege contra associações que não estejam
        // refletidas na coleção carregada.
        boolean hasFilesInRepository =
                datasetRepository.hasFiles(id);

        if (hasAssociatedFiles || hasFilesInRepository) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Conjunto possui arquivos e não pode ser excluído.");
        }

        datasetRepository.delete(dataset);
    }

    private String validateName(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do conjunto não pode ser vazio.");
        }

        String name = value.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do conjunto não pode ser vazio.");
        }

        int nameBytes = name.getBytes(StandardCharsets.UTF_8).length;

        if (nameBytes > MAX_NAME_BYTES) {
            throw new IllegalArgumentException(
                    "O nome do conjunto não pode exceder 100 bytes.");
        }

        return name;
    }

    private DatasetResponseDTO toDto(Dataset dataset) {
        return new DatasetResponseDTO(
                dataset.getId(),
                dataset.getName(),
                dataset.getUser() != null
                        ? dataset.getUser().getId()
                        : null);
    }
}