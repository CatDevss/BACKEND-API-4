package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Version;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionRepository extends JpaRepository<Version, Long> {
}