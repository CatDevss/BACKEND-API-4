package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

public record CollectionResponseDTO(
        Long ingestionId,
        Long sourceId,
        Long dictionaryId,
        String status,
        LocalDateTime startTime,
        Long fileId,
        String fileName,
        String fileHash,
        String fileLocation,
        String message
) {
}