package catdevs.georuraldatahub.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final IndicatorResultRepository resultRepository;
    private final DatasetRepository datasetRepository;
    private final LayerRepository layerRepository;
    private final ObjectMapper objectMapper;
    private final IndicatorDatasetAccessService accessService;

    public IndicatorResultService(
            IndicatorResultRepository resultRepository,
            DatasetRepository datasetRepository,
            LayerRepository layerRepository,
            ObjectMapper objectMapper,
            IndicatorDatasetAccessService accessService) {
        this.resultRepository = resultRepository;
        this.datasetRepository = datasetRepository;
        this.layerRepository = layerRepository;
        this.objectMapper = objectMapper;
        this.accessService = accessService;
    }

    @Transactional
    public IndicatorResultResponseDTO save(IndicatorResultCreateDTO request) {
        var dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Conjunto não encontrado"));

        accessService.requireOwner(dataset);

        var result = new IndicatorResult();
        result.setIndicatorCode(request.indicatorCode().trim());
        result.setDataset(dataset);
        result.setPropertyReference(blankToNull(request.propertyReference()));
        result.setUnit(blankToNull(request.unit()));

        try {
            result.setResultData(
                    objectMapper.writeValueAsString(request.resultData()));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Resultado do indicador não é um JSON válido",
                    exception);
        }

        if (request.layerId() != null) {
            var layer = layerRepository.findById(request.layerId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Camada não encontrada"));

            if (layer.getDataset() == null
                    || !layer.getDataset().getId().equals(dataset.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A camada informada não pertence ao conjunto indicado");
            }

            result.setLayer(layer);
        }

        return toResponse(resultRepository.save(result));
    }

    @Transactional(readOnly = true)
    public IndicatorResultResponseDTO findById(Long id) {
        IndicatorResult result = resultRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Resultado não encontrado"));

        accessService.requireOwner(result.getDataset());

        return toResponse(result);
    }

    @Transactional(readOnly = true)
    public Page<IndicatorResultResponseDTO> findByDataset(
            Long datasetId,
            String indicatorCode,
            String propertyReference,
            int page,
            int size) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O número da página não pode ser negativo.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O tamanho da página deve estar entre 1 e " + MAX_PAGE_SIZE + ".");
        }

        accessService.requireDatasetAccess(datasetId);

        String normalizedCode = blankToNull(indicatorCode);
        String normalizedReference = blankToNull(propertyReference);

        PageRequest pageable = PageRequest.of(page, size);

        return resultRepository.searchByDataset(
                datasetId,
                normalizedCode,
                normalizedReference,
                pageable
        ).map(this::toResponse);
    }

    private IndicatorResultResponseDTO toResponse(IndicatorResult result) {
        try {
            return new IndicatorResultResponseDTO(
                    result.getId(),
                    result.getIndicatorCode(),
                    result.getDataset().getId(),
                    result.getLayer() == null ? null : result.getLayer().getId(),
                    result.getPropertyReference(),
                    result.getUnit(),
                    objectMapper.readTree(result.getResultData()),
                    result.getCalculatedAt()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Resultado persistido contém JSON inválido",
                    exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}