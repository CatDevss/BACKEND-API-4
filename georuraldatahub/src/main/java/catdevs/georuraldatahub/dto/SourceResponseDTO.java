package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

public record SourceResponseDTO(
        Long id,
        String name,
        LocalDateTime dateCreation,
        String url,
        Long userId
) {
}