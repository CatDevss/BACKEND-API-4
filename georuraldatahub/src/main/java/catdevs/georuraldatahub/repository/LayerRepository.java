package catdevs.georuraldatahub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.Layer;

public interface LayerRepository extends JpaRepository<Layer, Long> {
    boolean existsByVersion_File_Id(Long fileId);
}
