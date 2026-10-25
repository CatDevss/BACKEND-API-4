package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;

public record QuarantineResponseDTO(Long id, Long ingestionId, Long fileId, String fileName,
        Long sourceId, String sourceName, String status, String reason, String treatment,
        LocalDateTime dateEntry, LocalDateTime dateResolution, Long responsibleId,
        String responsibleName, String validationReportUrl) {}
