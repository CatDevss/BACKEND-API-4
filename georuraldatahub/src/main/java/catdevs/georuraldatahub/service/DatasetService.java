
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

import java.util.List;

@Service
public class DatasetService {

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

                User user = userService.findEntityById(request.userId());

                Dataset dataset = new Dataset();
                dataset.setName(validName(request.name()));
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
                                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
        }

        @Transactional(readOnly = true)
        public DatasetResponseDTO findById(Long id) {

                return toDto(findEntityById(id));
        }

        @Transactional
        public DatasetResponseDTO update(
                        Long id,
                        DatasetUpdateRequestDTO request) {

                Dataset dataset = findEntityById(id);
                dataset.setName(validName(request.name()));

                return toDto(dataset);
        }

        @Transactional
        public void delete(Long id) {

                Dataset dataset = findLockedEntityById(id);

                if (!dataset.getFiles().isEmpty()) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Conjunto possui arquivos e não pode ser excluído.");
                }

                datasetRepository.delete(dataset);
        }

        private String validName(String value) {
                if (value == null || value.isBlank()) throw new IllegalArgumentException("Nome do conjunto é obrigatório.");
                String name = value.trim();
                if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 100)
                        throw new IllegalArgumentException("Nome do conjunto excede o limite de 100 bytes do banco atual.");
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