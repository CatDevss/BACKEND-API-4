package catdevs.georuraldatahub.dto;

import catdevs.georuraldatahub.entity.enums.VersionStatus;

public record FileStageDTO(
        Long fileId,
        VersionStatus status,
        Integer versionNumber,
        String location) {
}