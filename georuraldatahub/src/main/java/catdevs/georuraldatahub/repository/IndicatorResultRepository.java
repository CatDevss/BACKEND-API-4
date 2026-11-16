package catdevs.georuraldatahub.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.IndicatorResult;

public interface IndicatorResultRepository extends JpaRepository<IndicatorResult, Long> {
    List<IndicatorResult> findByDataset_IdOrderByCalculatedAtDesc(Long datasetId);
    List<IndicatorResult> findByDataset_IdAndIndicatorCodeIgnoreCaseOrderByCalculatedAtDesc(
            Long datasetId, String indicatorCode);
    List<IndicatorResult> findByDataset_IdAndPropertyReferenceOrderByCalculatedAtDesc(
            Long datasetId, String propertyReference);
}