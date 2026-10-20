package catdevs.georuraldatahub.repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.Dictionary;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {

    Optional<Dictionary> findFirstBySource_IdOrderByVersionDesc(Long sourceId);

    List<Dictionary> findBySource_IdOrderByVersionDesc(Long sourceId);

    @Query("select d.source.id from Dictionary d where d.id = :id")
    Optional<Long> findSourceIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dictionary d where d.id = :id")
    Optional<Dictionary> findLockedById(@Param("id") Long id);
}
