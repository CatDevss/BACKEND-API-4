
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
                dataset.setName(request.name().trim());
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

        @Transactional(readOnly = true)
        public DatasetResponseDTO findById(Long id) {

                return toDto(findEntityById(id));
        }

        @Transactional
        public DatasetResponseDTO update(
                        Long id,
                        DatasetUpdateRequestDTO request) {

                Dataset dataset = findEntityById(id);
                dataset.setName(request.name().trim());

                // A entidade gerenciada é atualizada no commit da transação.
                return toDto(dataset);
        }

        @Transactional
        public void delete(Long id) {

                Dataset dataset = findEntityById(id);

                if (datasetRepository.hasFiles(id)) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Conjunto possui arquivos e não pode ser excluído.");
                }

                datasetRepository.delete(dataset);
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