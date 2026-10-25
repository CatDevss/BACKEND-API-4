package catdevs.georuraldatahub.repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import catdevs.georuraldatahub.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {

    boolean existsByName(String name);

    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Source s where s.id = :id")
    Optional<Source> findLockedById(@Param("id") Long id);

    boolean existsByNameAndIdNot(String name, Long id);
}
