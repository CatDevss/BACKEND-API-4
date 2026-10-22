package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

import catdevs.georuraldatahub.entity.OccurrenceHistory;
import catdevs.georuraldatahub.entity.enums.OccurrenceStatus;

public record OccurrenceHistoryDTO(
        Long id,
        Long occurrenceId,
        OccurrenceStatus previousStatus,
        OccurrenceStatus newStatus,
        Long actorUserId,
        String actorName,
        String action,
        String details,
        LocalDateTime createdAt
) {
    public static OccurrenceHistoryDTO from(OccurrenceHistory h) {
        return new OccurrenceHistoryDTO(
                h.getId(),
                h.getOccurrence().getId(),
                h.getPreviousStatus(),
                h.getNewStatus(),
                h.getActor() == null ? null : h.getActor().getId(),
                h.getActor() == null ? null : h.getActor().getName(),
                h.getAction(),
                h.getDetails(),
                h.getCreatedAt()
        );
    }
}