package catdevs.georuraldatahub.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IndicatorResultCreateDTO(
        @NotBlank @Size(max = 100) String indicatorCode,
        @NotNull Long datasetId,
        Long layerId,
        @Size(max = 150) String propertyReference,
        @Size(max = 50) String unit,
        @NotNull JsonNode resultData) {
}