package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuarantineActorRequestDTO(@NotNull @Positive Long userId) {}
