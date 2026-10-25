package catdevs.georuraldatahub.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.Report;

/** Repositório dos relatórios de inconsistências. */
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByFile_IdOrderByCreatedAtDesc(Long fileId);

    Optional<Report> findByIdAndFile_Id(Long reportId, Long fileId);

    List<Report> findByFile_IdAndStatusOrderByCreatedAtDesc(Long fileId, String status);
}