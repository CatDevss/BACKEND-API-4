package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

public record LayerInfoDTO(Long fileId, String name, String formatFile,
        Long size, LocalDateTime receivedDate) {
}
