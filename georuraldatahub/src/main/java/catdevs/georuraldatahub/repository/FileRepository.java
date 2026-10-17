package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FileRepository extends JpaRepository<File, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
        FROM Dataset d JOIN d.files f
        WHERE d.id = :datasetId
        AND f.hash = :hash
    """)
    boolean existsByHashInDataset(@Param("datasetId") Long datasetId, @Param("hash") String hash);

    @Query("""
        SELECT f
        FROM Dataset d JOIN d.files f
        WHERE d.id = :datasetId
        ORDER BY f.id DESC
    """)
    List<File> findByDatasetId(@Param("datasetId") Long datasetId);
}
