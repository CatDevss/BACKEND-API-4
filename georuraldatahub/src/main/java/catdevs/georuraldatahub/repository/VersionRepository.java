package catdevs.georuraldatahub.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import catdevs.georuraldatahub.dto.FileStageDTO;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.VersionStatus;

public interface VersionRepository extends JpaRepository<Version, Long> {
    Optional<Version> findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberAsc(
            Long fileId, Collection<VersionStatus> statuses, String locationPrefix);
    Optional<Version> findFirstByFile_IdOrderByVersionNumberDesc(Long fileId);
    Optional<Version> findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(Long fileId, Collection<VersionStatus> statuses);
    Optional<Version> findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(
            Long fileId, VersionStatus status, String locationPrefix);

    // Uma consulta por conjunto, sem carregar entidades/LOBs nem montar um IN com todos os arquivos.
    @Query("""
        select distinct f.id from Dataset d join d.files f
        where d.id = :datasetId and exists (
            select v.id from Version v where v.file = f and v.status in :statuses)
        """)
    Set<Long> findFileIdsByDatasetAndStatusIn(@Param("datasetId") Long datasetId,
            @Param("statuses") Collection<VersionStatus> statuses);

    // Compatibilidade com a ingestão antiga, que mantinha BRUTA na cópia validada.
    // Apenas a etapa atual pode ser candidata; não reativar cópias já substituídas.
    @Query("""
        select new catdevs.georuraldatahub.dto.FileStageDTO(
            v.file.id, v.status, v.versionNumber, v.location)
        from Version v where v.status = :status and v.versionNumber > 1
            and v.location like 'validada/%'
            and v.file in (select f from Dataset d join d.files f where d.id = :datasetId)
            and v.versionNumber = (select max(latest.versionNumber) from Version latest
                where latest.file = v.file)
        """)
    List<FileStageDTO> findLegacyValidatedStagesByDataset(@Param("datasetId") Long datasetId,
            @Param("status") VersionStatus status);

    /**
     * Para cada arquivo, devolve só a versão mais recente (maior número de versão).
     * O status dessa versão é a etapa atual do arquivo.
     */
    @Query("""
        SELECT new catdevs.georuraldatahub.dto.FileStageDTO(
            v.file.id, v.status, v.versionNumber, v.location)
        FROM Version v
        WHERE v.versionNumber = (
            SELECT MAX(latest.versionNumber)
            FROM Version latest
            WHERE latest.file.id = v.file.id)
        ORDER BY v.file.id DESC
    """)
    List<FileStageDTO> findCurrentStageOfEachFile();
}
