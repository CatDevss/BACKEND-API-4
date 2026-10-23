package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.DataIngestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngestionRepository extends JpaRepository<DataIngestion, Long> {
    List<DataIngestion> findByDictionarySourceIdOrderByStartTimeDesc(Long sourceId);
    Optional<DataIngestion> findByIdAndDictionarySourceId(Long id, Long sourceId);
}
