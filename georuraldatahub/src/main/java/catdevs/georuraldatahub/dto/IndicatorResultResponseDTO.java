package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;

public record IndicatorResultResponseDTO(
        Long id,
        String indicatorCode,
        Long datasetId,
        Long layerId,
        String propertyReference,
        String unit,
        JsonNode resultData,
        LocalDateTime calculatedAt) {
}