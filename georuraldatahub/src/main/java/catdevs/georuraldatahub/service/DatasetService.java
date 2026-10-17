package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DatasetService {

        private final DatasetRepository datasetRepository;
        private final UserRepository userRepository;
        private final FileRepository fileRepository;

        public DatasetService(
                        DatasetRepository datasetRepository,
                        UserRepository userRepository,
                        FileRepository fileRepository) {
                this.datasetRepository = datasetRepository;
                this.userRepository = userRepository;
                this.fileRepository = fileRepository;
        }

        @Transactional
        public DatasetResponseDTO create(DatasetCreateRequestDTO request) {

                User user = userRepository.findById(request.userId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Usuário não encontrado"));

                Dataset dataset = new Dataset();
                dataset.setName(request.name().trim());
                dataset.setUser(user);

                Dataset saved = datasetRepository.save(dataset);

                return new DatasetResponseDTO(saved.getId(), saved.getName(), user.getId());
        }

        @Transactional(readOnly = true)
        public List<DatasetResponseDTO> listAll() {
                return datasetRepository.findAll()
                                .stream()
                                .map(dataset -> new DatasetResponseDTO(
                                                dataset.getId(),
                                                dataset.getName(),
                                                dataset.getUser() != null ? dataset.getUser().getId() : null))
                                .toList();
        }

        @Transactional(readOnly = true)
        public List<FileResponseDTO> listFiles(Long datasetId) {

                Dataset dataset = datasetRepository.findById(datasetId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Conjunto não encontrado"));

                return fileRepository
                                .findByDatasetId(dataset.getId())
                                .stream()
                                .map(this::toDto)
                                .toList();
        }

        private FileResponseDTO toDto(File file) {
                return new FileResponseDTO(
                                file.getId(),
                                file.getName(),
                                file.getFormatFile(),
                                file.getSize(),
                                file.getHash(),
                                file.getLocation(),
                                file.getIngestion().getId(),
                                file.getReceivedDate(),
                                file.getIngestion().getUser().getId());
        }
}
