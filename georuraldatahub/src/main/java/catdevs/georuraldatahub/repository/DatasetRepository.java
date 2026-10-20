package catdevs.georuraldatahub.repository;

import java.util.Optional;
import catdevs.georuraldatahub.entity.Dataset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dataset d where d.id = :id")
    Optional<Dataset> findLockedById(@Param("id") Long id);
}
