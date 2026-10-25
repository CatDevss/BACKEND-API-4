package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Feature;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    record NewFeature(String originId, String geometryGeoJson, String attributesJson) { }
    long countByLayerId(Long layerId);
    @Query("select f.id from Feature f where f.layer.id = :layerId order by f.id")
    List<Long> findIdsByLayerId(@Param("layerId") Long layerId);
}
