package catdevs.georuralprocessing.repository;

import catdevs.georuralprocessing.entity.TreatmentExecution;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentExecutionRepository extends JpaRepository<TreatmentExecution, Long> {
    Page<TreatmentExecution> findByFileIdOrderByIdDesc(Long fileId, Pageable page);
}
