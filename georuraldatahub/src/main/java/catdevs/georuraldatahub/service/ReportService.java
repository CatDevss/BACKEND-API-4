package catdevs.georuraldatahub.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Report;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.ReportRepository;

/** Regras de negócio do CRUD de relatórios de inconsistências. */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final FileService fileService;
    private final UserService userService;

    public ReportService(ReportRepository reportRepository, FileService fileService, UserService userService) {
        this.reportRepository = reportRepository;
        this.fileService = fileService;
        this.userService = userService;
    }

    @Transactional
    public Report create(Long fileId, Long userId, String title, String content) {
        validate(title, content);
        File file = fileService.findEntityById(fileId);
        User user = userService.findEntityById(userId);

        Report report = new Report();
        report.setFile(file);
        report.setUser(user);
        report.setTitle(title.trim());
        report.setContent(content);
        report.setStatus(Report.STATUS_DRAFT);
        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<Report> findByFile(Long fileId) {
        fileService.findEntityById(fileId);
        return reportRepository.findByFile_IdOrderByCreatedAtDesc(fileId);
    }

    @Transactional(readOnly = true)
    public Report findById(Long fileId, Long reportId) {
        fileService.findEntityById(fileId);
        return reportRepository.findByIdAndFile_Id(reportId, fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Relatório não encontrado para o arquivo informado."));
    }

    @Transactional
    public Report update(Long fileId, Long reportId, String title, String content) {
        validate(title, content);
        Report report = findById(fileId, reportId);
        ensureDraft(report);
        report.setTitle(title.trim());
        report.setContent(content);
        return reportRepository.save(report);
    }

    @Transactional
    public void delete(Long fileId, Long reportId) {
        Report report = findById(fileId, reportId);
        ensureDraft(report);
        reportRepository.delete(report);
    }

    /** Finaliza o relatório e o torna histórico/imutável. */
    @Transactional
    public Report finalizeReport(Long fileId, Long reportId) {
        Report report = findById(fileId, reportId);
        ensureDraft(report);
        report.setStatus(Report.STATUS_HISTORICAL);
        report.setFinalizedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    private void ensureDraft(Report report) {
        if (!Report.STATUS_DRAFT.equalsIgnoreCase(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Somente relatórios em rascunho podem ser editados ou excluídos; relatórios históricos são imutáveis.");
        }
    }

    private void validate(String title, String content) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O título do relatório é obrigatório.");
        }
        if (title.trim().length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O título deve ter no máximo 200 caracteres.");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O conteúdo do relatório é obrigatório.");
        }
    }
}