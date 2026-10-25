package catdevs.georuraldatahub.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import catdevs.georuraldatahub.dto.FileStageDTO;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.VersionStatus;

public interface VersionRepository extends JpaRepository<Version, Long> {

    Optional<Version> findFirstByFile_IdOrderByVersionNumberDesc(
            Long fileId
    );

    Optional<Version> findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
            Long fileId,
            Collection<VersionStatus> statuses
    );

    Optional<Version> findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(
            Long fileId,
            VersionStatus status,
            String locationPrefix
    );

    Optional<Version> findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
            Long fileId,
            Collection<VersionStatus> statuses,
            String locationPrefix
    );

    /**
     * Para cada arquivo, devolve somente a versão mais recente,
     * considerando o maior número de versão.
     *
     * O status dessa versão representa a etapa atual do arquivo.
     */
    @Query("""
        SELECT new catdevs.georuraldatahub.dto.FileStageDTO(
            v.file.id, v.status, v.versionNumber, v.location
        )
        FROM Version v
        WHERE v.versionNumber = (
            SELECT MAX(latest.versionNumber)
            FROM Version latest
            WHERE latest.file.id = v.file.id
        )
        ORDER BY v.file.id DESC
        """)
    List<FileStageDTO> findCurrentStageOfEachFile();
}