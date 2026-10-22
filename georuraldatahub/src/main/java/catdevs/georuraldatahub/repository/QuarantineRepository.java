package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Quarantine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuarantineRepository extends JpaRepository<Quarantine, Long> {

    @Query("""
        SELECT q
        FROM Quarantine q
        WHERE q.status = :status
        ORDER BY q.dateEntry DESC
        """)
    List<Quarantine> findByStatus(@Param("status") String status);
}
