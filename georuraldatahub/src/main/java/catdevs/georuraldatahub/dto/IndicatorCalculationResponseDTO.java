package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

public record IndicatorCalculationResponseDTO(
        Long calculationId,
        Long featureId,
        LocalDateTime calculatedAt,
        String rule,
        JsonNode parameters,
        JsonNode result) {
}
