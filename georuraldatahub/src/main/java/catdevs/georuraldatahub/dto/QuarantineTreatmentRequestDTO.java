package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuarantineTreatmentRequestDTO(@NotNull @Positive Long userId,
        @NotBlank @Size(max = 500) String treatment) {}
