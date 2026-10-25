package catdevs.georuraldatahub.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.Correction;

public interface CorrectionRepository extends JpaRepository<Correction, Long> {

    List<Correction> findByFile_IdOrderByCreatedAtDesc(Long fileId);

    Optional<Correction> findByIdAndFile_Id(Long id, Long fileId);

    List<Correction> findByFile_IdAndStatusOrderByCreatedAtDesc(
            Long fileId,
            String status);

    boolean existsByFile_IdAndStatus(Long fileId, String status);
}