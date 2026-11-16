package catdevs.georuraldatahub.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.dto.IndicatorResultCreateDTO;
import catdevs.georuraldatahub.dto.IndicatorResultResponseDTO;
import catdevs.georuraldatahub.entity.IndicatorResult;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.IndicatorResultRepository;
import catdevs.georuraldatahub.repository.LayerRepository;

@Service
public class IndicatorResultService {

    private final IndicatorResultRepository resultRepository;
    private final DatasetRepository datasetRepository;
    private final LayerRepository layerRepository;
    private final ObjectMapper objectMapper;

    public IndicatorResultService(IndicatorResultRepository resultRepository,
            DatasetRepository datasetRepository, LayerRepository layerRepository,
            ObjectMapper objectMapper) {
        this.resultRepository = resultRepository;
        this.datasetRepository = datasetRepository;
        this.layerRepository = layerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IndicatorResultResponseDTO save(IndicatorResultCreateDTO request) {
        var dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto não encontrado"));

        var result = new IndicatorResult();
        result.setIndicatorCode(request.indicatorCode().trim());
        result.setDataset(dataset);
        result.setPropertyReference(blankToNull(request.propertyReference()));
        result.setUnit(blankToNull(request.unit()));
        try {
            result.setResultData(objectMapper.writeValueAsString(request.resultData()));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resultado do indicador não é um JSON válido", e);
        }

        if (request.layerId() != null) {
            var layer = layerRepository.findById(request.layerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Camada não encontrada"));
            if (!layer.getDataset().getId().equals(dataset.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A camada informada não pertence ao conjunto indicado");
            }
            result.setLayer(layer);
        }

        return toResponse(resultRepository.save(result));
    }

    @Transactional(readOnly = true)
    public IndicatorResultResponseDTO findById(Long id) {
        return toResponse(resultRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resultado não encontrado")));
    }

    @Transactional(readOnly = true)
    public List<IndicatorResultResponseDTO> findByDataset(Long datasetId, String indicatorCode,
            String propertyReference) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto não encontrado");
        }
        List<IndicatorResult> results;
        if (indicatorCode != null && !indicatorCode.isBlank()) {
            results = resultRepository.findByDataset_IdAndIndicatorCodeIgnoreCaseOrderByCalculatedAtDesc(
                    datasetId, indicatorCode.trim());
        } else if (propertyReference != null && !propertyReference.isBlank()) {
            results = resultRepository.findByDataset_IdAndPropertyReferenceOrderByCalculatedAtDesc(
                    datasetId, propertyReference.trim());
        } else {
            results = resultRepository.findByDataset_IdOrderByCalculatedAtDesc(datasetId);
        }
        return results.stream().map(this::toResponse).toList();
    }

    private IndicatorResultResponseDTO toResponse(IndicatorResult result) {
        try {
            return new IndicatorResultResponseDTO(result.getId(), result.getIndicatorCode(),
                    result.getDataset().getId(), result.getLayer() == null ? null : result.getLayer().getId(),
                    result.getPropertyReference(), result.getUnit(),
                    objectMapper.readTree(result.getResultData()), result.getCalculatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Resultado persistido contém JSON inválido", e);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}