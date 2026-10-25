package catdevs.georuraldatahub.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import catdevs.georuraldatahub.entity.Source;

public interface SourceRepository extends JpaRepository<Source, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
        FROM Source s
        WHERE s.name = :name
        """)
    boolean existsByName(@Param("name") String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Source s WHERE s.id = :id")
    Optional<Source> findLockedById(@Param("id") Long id);
}