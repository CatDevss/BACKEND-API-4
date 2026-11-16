package catdevs.georuraldatahub.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record IndicatorCalculationRequestDTO(
        @NotNull Long featureId,
        JsonNode config) {
}