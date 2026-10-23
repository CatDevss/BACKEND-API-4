package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.DataIngestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRepository
        extends JpaRepository<DataIngestion, Long> {

    List<DataIngestion> findByDictionarySourceIdOrderByStartTimeDesc(
            Long sourceId
    );

    Optional<DataIngestion> findByIdAndDictionarySourceId(
            Long ingestionId,
            Long sourceId
    );
}