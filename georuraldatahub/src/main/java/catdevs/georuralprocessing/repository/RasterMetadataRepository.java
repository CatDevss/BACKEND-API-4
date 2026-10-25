package catdevs.georuralprocessing.repository;

import catdevs.georuralprocessing.entity.RasterMetadata;
import org.locationtech.jts.geom.Geometry;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RasterMetadataRepository extends JpaRepository<RasterMetadata, Long> {
    @Query("select r from RasterMetadata r where intersects(r.footprint, :area) = true")
    Page<RasterMetadata> findIntersecting(@Param("area") Geometry area, Pageable page);
}
