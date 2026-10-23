package catdevs.georuraldatahub.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import catdevs.georuraldatahub.entity.Dictionary;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {

    @Query("""
        SELECT d
        FROM Dictionary d
        WHERE d.source.id = :sourceId
          AND d.version = (
              SELECT MAX(d2.version)
              FROM Dictionary d2
              WHERE d2.source.id = :sourceId
          )
        """)
    Optional<Dictionary> findFirstBySource_IdOrderByVersionDesc(
            @Param("sourceId") Long sourceId);
}