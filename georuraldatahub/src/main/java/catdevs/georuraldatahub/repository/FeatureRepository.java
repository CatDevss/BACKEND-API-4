package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Feature;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    record NewFeature(String originId, String geometryGeoJson, String attributesJson) {
    }

    @Query(value = """
            SELECT 'feicao ' || id || ': ' || diagnostico FROM (
                SELECT f.fei_id AS id,
                    CASE WHEN f.fei_geometria.sdo_srid IS NULL
                              OR f.fei_geometria.sdo_srid <> c.cam_srid
                         THEN 'SRID diferente da camada'
                         ELSE SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(f.fei_geometria, 0.005)
                    END AS diagnostico
                FROM feicao f JOIN camada c ON c.cam_id = f.cam_id
                WHERE f.cam_id = :layerId
            ) WHERE NVL(diagnostico, 'INVALID') <> 'TRUE'
            ORDER BY id FETCH FIRST 1 ROW ONLY
            """, nativeQuery = true)
    List<String> firstSpatialError(@Param("layerId") Long layerId);

    long countByLayerId(Long layerId);

    @Query("select f.id from Feature f where f.layer.id = :layerId order by f.id")
    List<Long> findIdsByLayerId(@Param("layerId") Long layerId);
}
