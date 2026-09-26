package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

public record FileResponseDTO(
        Long id,
        String name,
        String formatFile,
        Long size,
        String hash,
        String location,
        Long versionId,
        LocalDateTime dateSent,
        Long userId
) {
}