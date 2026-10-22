package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
        FROM Dataset d JOIN d.files f
        WHERE d.id = :datasetId
        """)
    boolean hasFiles(@Param("datasetId") Long datasetId);
}