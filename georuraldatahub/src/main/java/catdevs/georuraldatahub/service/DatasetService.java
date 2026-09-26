package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.SourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final SourceRepository sourceRepository;
    private final FileRepository fileRepository;

    public DatasetService(
            DatasetRepository datasetRepository,
            SourceRepository sourceRepository,
            FileRepository fileRepository
    ) {
        this.datasetRepository = datasetRepository;
        this.sourceRepository = sourceRepository;
        this.fileRepository = fileRepository;
    }

    public DatasetResponseDTO create(DatasetCreateRequestDTO request) {

        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome do conjunto é obrigatório"
            );
        }

        if (request.sourceId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fonte é obrigatória"
            );
        }

        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Fonte não encontrada"
                ));

        Dataset dataset = new Dataset(
                request.name().trim(),
                source
        );

        Dataset savedDataset = datasetRepository.save(dataset);

        return new DatasetResponseDTO(
                savedDataset.getId(),
                savedDataset.getName(),
                savedDataset.getSource().getId()
        );
    }

    public List<FileResponseDTO> listFiles(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conjunto não encontrado"
                ));

        return fileRepository.findByVersion_Dataset_IdOrderByIdDesc(dataset.getId())
                .stream()
                .map(file -> new FileResponseDTO(
                        file.getId(),
                        file.getName(),
                        file.getFormatFile(),
                        file.getHash(),
                        file.getLocation(),
                        file.getVersion().getId(),
                        file.getVersion().getDateCreation(),
                        file.getVersion().getUser() != null
                                ? file.getVersion().getUser().getId()
                                : null
                ))
                .toList();
    }
}
