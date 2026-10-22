package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotNull;

public record OccurrenceAssigneeRequest(
        @NotNull Long userId,
        Long actorUserId,
        String details
) {
}