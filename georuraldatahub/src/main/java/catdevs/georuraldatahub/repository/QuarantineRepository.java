package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Quarantine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuarantineRepository extends JpaRepository<Quarantine, Long> {

    List<Quarantine> findByFile_Version_Dataset_IdOrderByDateEntryDesc(Long datasetId);

    List<Quarantine> findByStatus(String status);
}