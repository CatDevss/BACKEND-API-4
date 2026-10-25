package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Quarantine;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface QuarantineRepository extends JpaRepository<Quarantine, Long> {

    @EntityGraph(attributePaths = {"user", "file", "ingestion", "ingestion.dictionary", "ingestion.dictionary.source"})
    @Query("""
        select q from Quarantine q
        left join q.user responsible
        left join q.file file
        where (:status is null or q.status = :status)
          and (:responsibleId is null or responsible.id = :responsibleId)
          and (:fileId is null or file.id = :fileId)
          and (:unassigned = false or (responsible is null and q.dateResolution is null and q.status <> 'RESOLVIDA'))
        """)
    Page<Quarantine> search(@Param("status") String status, @Param("responsibleId") Long responsibleId,
            @Param("fileId") Long fileId, @Param("unassigned") boolean unassigned, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("select q from Quarantine q where q.id = :id")
    Optional<Quarantine> findLockedById(@Param("id") Long id);
}
