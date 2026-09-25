package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRepository extends JpaRepository<File, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
        FROM File f
        WHERE f.version.dataset.id = :datasetId
        AND f.hash = :hash
    """)
    boolean existsByHashInDataset(@Param("datasetId") Long datasetId, @Param("hash") String hash);
}