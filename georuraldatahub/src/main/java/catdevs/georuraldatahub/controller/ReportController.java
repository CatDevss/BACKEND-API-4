package catdevs.georuraldatahub.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.entity.Report;
import catdevs.georuraldatahub.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Endpoints REST para o CRUD e a finalização de relatórios. */
@RestController
@Validated
@RequestMapping("/arquivos/{fileId}/relatorios")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@PathVariable Long fileId, @Valid @RequestBody CreateReportRequest request) {
        return toResponse(reportService.create(fileId, request.userId(), request.title(), request.content()));
    }

    @GetMapping
    public List<ReportResponse> list(@PathVariable Long fileId) {
        return reportService.findByFile(fileId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{reportId}")
    public ReportResponse get(@PathVariable Long fileId, @PathVariable Long reportId) {
        return toResponse(reportService.findById(fileId, reportId));
    }

    @PutMapping("/{reportId}")
    public ReportResponse update(@PathVariable Long fileId, @PathVariable Long reportId,
            @Valid @RequestBody UpdateReportRequest request) {
        return toResponse(reportService.update(fileId, reportId, request.title(), request.content()));
    }

    @DeleteMapping("/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long fileId, @PathVariable Long reportId) {
        reportService.delete(fileId, reportId);
    }

    @PostMapping("/{reportId}/finalizar")
    public ReportResponse finalizeReport(@PathVariable Long fileId, @PathVariable Long reportId) {
        return toResponse(reportService.finalizeReport(fileId, reportId));
    }

    private ReportResponse toResponse(Report report) {
        return new ReportResponse(report.getId(), report.getFile() == null ? null : report.getFile().getId(),
                report.getUser() == null ? null : report.getUser().getId(), report.getTitle(), report.getContent(),
                report.getStatus(), report.getCreatedAt(), report.getUpdatedAt(), report.getFinalizedAt());
    }

    public record CreateReportRequest(@NotNull Long userId, @NotBlank String title, @NotBlank String content) { }
    public record UpdateReportRequest(@NotBlank String title, @NotBlank String content) { }
    public record ReportResponse(Long id, Long fileId, Long userId, String title, String content, String status,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime finalizedAt) { }
}