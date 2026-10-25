package catdevs.georuraldatahub.repository;

import java.util.Collection;
import java.util.Optional;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.entity.Version;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionRepository extends JpaRepository<Version, Long> {
    Optional<Version> findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(
            Long fileId, VersionStatus status, String locationPrefix);

    Optional<Version> findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
            Long fileId, Collection<VersionStatus> statuses, String locationPrefix);
}