package catdevs.georuraldatahub.repository;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
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
            @Param("sourceId") Long sourceId
    );

    List<Dictionary> findAllBySource_IdOrderByVersionDesc(Long sourceId);

    @Query("""
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
        FROM Dictionary d JOIN d.files f
        WHERE d.id = :datasetId
        """)
    boolean hasFiles(@Param("datasetId") Long datasetId);

    @Nullable
    Object findSourceIdById(long l);
}