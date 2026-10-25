package catdevs.georuraldatahub.processing;

import catdevs.georuraldatahub.validation.FieldType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record TreatmentRequest(@Positive Long datasetId, @NotNull @Positive Long userId,
        List<@Valid AttributeChange> attributes, Boolean dissolve, List<String> dissolveBy) {
    public TreatmentRequest {
        dissolve = Boolean.TRUE.equals(dissolve);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        dissolveBy = dissolveBy == null ? List.of() : List.copyOf(dissolveBy);
    }
    public record AttributeChange(@NotBlank String source, @NotBlank String target,
            @NotNull FieldType type, String datePattern, BigDecimal factor, BigDecimal offset, String targetUnit) { }
}
