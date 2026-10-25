package catdevs.georuraldatahub.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.Layer;

public interface LayerRepository extends JpaRepository<Layer, Long> {

    List<Layer> findByDataset_IdOrderByIdDesc(Long datasetId);

    List<Layer> findByVersion_Id(Long versionId);
}