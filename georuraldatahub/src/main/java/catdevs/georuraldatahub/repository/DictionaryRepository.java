package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Dictionary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {

    List<Dictionary> findBySourceId(Long sourceId);

    @Query("SELECT d FROM Dictionary d WHERE d.id = :dictionaryId AND d.source.id = :sourceId")
    Optional<Dictionary> findByIdAndSourceId(
            @Param("dictionaryId") Long dictionaryId,
            @Param("sourceId") Long sourceId);
}