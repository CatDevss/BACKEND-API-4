package catdevs.georuraldatahub.dto;

import catdevs.georuraldatahub.entity.enums.VersionStatus;

/**
 * Etapa atual de um arquivo: o status e o local no bucket da sua versão mais recente.
 */
public record FileStageDTO(
        Long fileId,
        VersionStatus status,
        Integer versionNumber,
        String location) {
}