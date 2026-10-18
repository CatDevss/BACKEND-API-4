package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.DataIngestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRepository extends JpaRepository<DataIngestion, Long> {
}
