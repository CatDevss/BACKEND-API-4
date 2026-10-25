package catdevs.georuraldatahub.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Report;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.service.ReportService;
import catdevs.georuraldatahub.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@CrossOrigin
@Validated
@RequestMapping("/arquivos/{fileId}/relatorios")
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;

    public ReportController(
            ReportService reportService,
            UserService userService) {

        this.reportService = reportService;
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(
            @PathVariable Long fileId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReportRequest request) {

        Long actorUserId = authenticatedUser(jwt).getId();

        Report report = reportService.create(
                fileId,
                actorUserId,
                request.title(),
                request.content());

        return toResponse(report);
    }

    @GetMapping
    public List<ReportResponse> list(
            @PathVariable Long fileId) {

        return reportService.findByFile(fileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{reportId}")
    public ReportResponse get(
            @PathVariable Long fileId,
            @PathVariable Long reportId) {

        return toResponse(
                reportService.findById(fileId, reportId));
    }

    @PutMapping("/{reportId}")
    public ReportResponse update(
            @PathVariable Long fileId,
            @PathVariable Long reportId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateReportRequest request) {

        Long actorUserId = authenticatedUser(jwt).getId();

        Report report = reportService.update(
                fileId,
                reportId,
                actorUserId,
                request.title(),
                request.content());

        return toResponse(report);
    }

    @DeleteMapping("/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long fileId,
            @PathVariable Long reportId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorUserId = authenticatedUser(jwt).getId();

        reportService.delete(
                fileId,
                reportId,
                actorUserId);
    }

    @PostMapping("/{reportId}/finalizar")
    public ReportResponse finalizeReport(
            @PathVariable Long fileId,
            @PathVariable Long reportId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorUserId = authenticatedUser(jwt).getId();

        Report report = reportService.finalizeReport(
                fileId,
                reportId,
                actorUserId);

        return toResponse(report);
    }

    private User authenticatedUser(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Autenticação obrigatória.");
        }

        String email = jwt.getClaimAsString("email");

        if (email == null || email.isBlank()) {
            String username =
                    jwt.getClaimAsString("preferred_username");

            if (username != null && username.contains("@")) {
                email = username;
            }
        }

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "O token não contém o e-mail necessário para identificar o usuário.");
        }

        return userService.findEntityByEmail(email);
    }

    private ReportResponse toResponse(Report report) {

        return new ReportResponse(
                report.getId(),
                report.getFile() == null
                        ? null
                        : report.getFile().getId(),
                report.getUser() == null
                        ? null
                        : report.getUser().getId(),
                report.getTitle(),
                report.getContent(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                report.getFinalizedAt());
    }

    public record CreateReportRequest(
            @NotBlank String title,
            @NotBlank String content) {
    }

    public record UpdateReportRequest(
            @NotBlank String title,
            @NotBlank String content) {
    }

    public record ReportResponse(
            Long id,
            Long fileId,
            Long userId,
            String title,
            String content,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime finalizedAt) {
    }
}
