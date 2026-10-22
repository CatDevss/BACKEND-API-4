package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

public record FileResponseDTO(
        Long id,
        String name,
        String formatFile,
        Long size,
        String hash,
        String location,
        Long ingestionId,
        LocalDateTime receivedDate,
        Long userId,
        catdevs.georuraldatahub.validation.FileValidationReport validation
) {
    public FileResponseDTO(Long id, String name, String formatFile, Long size, String hash, String location,
            Long ingestionId, java.time.LocalDateTime receivedDate, Long userId) {
        this(id, name, formatFile, size, hash, location, ingestionId, receivedDate, userId, null);
    }
}
