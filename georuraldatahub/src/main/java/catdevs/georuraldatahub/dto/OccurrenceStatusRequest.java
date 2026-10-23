package catdevs.georuraldatahub.dto;

import catdevs.georuraldatahub.entity.enums.OccurrenceStatus;
import jakarta.validation.constraints.NotNull;

public record OccurrenceStatusRequest(
        @NotNull OccurrenceStatus status,
        Long actorUserId,
        String details
) {
}