package catdevs.georuraldatahub.dto; 

import java.time.LocalDateTime; 

public record CollectionResponseDTO(
    Long id,
    Long sourceId,
    String status,
    String hash,
    String objectName,
    Integer httpStatus,
    String error,
    Long requesterId,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
){}
