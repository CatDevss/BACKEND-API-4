package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

import catdevs.georuraldatahub.entity.Occurrence;
import catdevs.georuraldatahub.entity.enums.OccurrenceStatus;

public record OccurrenceDTO(
        Long id,
        Long fileId,
        String fileName,
        Integer recordIndex,
        String fieldName,
        Integer position,
        String code,
        String reason,
        OccurrenceStatus status,
        Long assignedUserId,
        String assignedUserName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt,
        Integer validationAttempt
) {
    public static OccurrenceDTO from(Occurrence o) {
        return new OccurrenceDTO(
                o.getId(),
                o.getFile().getId(),
                o.getFile().getName(),
                o.getRecordIndex(),
                o.getFieldName(),
                o.getPosition(),
                o.getCode(),
                o.getReason(),
                o.getStatus(),
                o.getAssignedTo() == null
                        ? null : o.getAssignedTo().getId(),
                o.getAssignedTo() == null
                        ? null : o.getAssignedTo().getName(),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                o.getResolvedAt(),
                o.getValidationAttempt()
        );
    }
}