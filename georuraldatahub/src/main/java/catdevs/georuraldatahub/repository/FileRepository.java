package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FileRepository extends JpaRepository<File, Long> {

    boolean existsByHashAndIngestion_Dictionary_Source_Id(String hash, Long sourceId);

    @EntityGraph(attributePaths = {"ingestion", "ingestion.user"})
    List<File> findByIngestion_Dictionary_Source_IdOrderByIdDesc(Long sourceId);


    @EntityGraph(attributePaths = {"ingestion.dictionary.source", "ingestion.user"})
    @Query("""
        SELECT f
        FROM Dataset d JOIN d.files f
        WHERE d.id = :datasetId
        ORDER BY f.id DESC
    """)
    List<File> findByDatasetId(@Param("datasetId") Long datasetId);
}
