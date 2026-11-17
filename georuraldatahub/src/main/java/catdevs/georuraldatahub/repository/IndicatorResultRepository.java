package catdevs.georuraldatahub.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import catdevs.georuraldatahub.entity.IndicatorResult;

public interface IndicatorResultRepository extends JpaRepository<IndicatorResult, Long> {

    @Query("""
            select r from IndicatorResult r
            where r.dataset.id = :datasetId
              and (:indicatorCode is null or lower(r.indicatorCode) = lower(:indicatorCode))
              and (:propertyReference is null or r.propertyReference = :propertyReference)
            order by r.calculatedAt desc, r.id desc
            """)
    Page<IndicatorResult> searchByDataset(
            @Param("datasetId") Long datasetId,
            @Param("indicatorCode") String indicatorCode,
            @Param("propertyReference") String propertyReference,
            Pageable pageable);
}