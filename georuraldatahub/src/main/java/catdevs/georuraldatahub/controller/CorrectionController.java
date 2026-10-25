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

import catdevs.georuraldatahub.entity.Correction;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.service.CorrectionService;
import catdevs.georuraldatahub.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@CrossOrigin
@Validated
@RequestMapping("/arquivos/{fileId}/correcoes")
public class CorrectionController {

    private final CorrectionService correctionService;
    private final UserService userService;

    public CorrectionController(
            CorrectionService correctionService,
            UserService userService) {
        this.correctionService = correctionService;
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CorrectionResponse create(
            @PathVariable Long fileId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCorrectionRequest request) {

        Long actorUserId = authenticatedUser(jwt).getId();

        Correction correction = correctionService.create(
                fileId,
                actorUserId,
                request.fieldName(),
                request.originalValue(),
                request.proposedValue());

        return toResponse(correction);
    }

    @GetMapping
    public List<CorrectionResponse> list(
            @PathVariable Long fileId) {

        return correctionService.findByFile(fileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{correctionId}")
    public CorrectionResponse get(
            @PathVariable Long fileId,
            @PathVariable Long correctionId) {

        return toResponse(
                correctionService.findById(fileId, correctionId));
    }

    @PutMapping("/{correctionId}")
    public CorrectionResponse update(
            @PathVariable Long fileId,
            @PathVariable Long correctionId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateCorrectionRequest request) {

        Long actorUserId = authenticatedUser(jwt).getId();

        Correction correction = correctionService.update(
                fileId,
                correctionId,
                actorUserId,
                request.fieldName(),
                request.proposedValue());

        return toResponse(correction);
    }

    @DeleteMapping("/{correctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long fileId,
            @PathVariable Long correctionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorUserId = authenticatedUser(jwt).getId();

        correctionService.delete(
                fileId,
                correctionId,
                actorUserId);
    }

    @PostMapping("/{correctionId}/aplicar")
    public CorrectionResponse apply(
            @PathVariable Long fileId,
            @PathVariable Long correctionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorUserId = authenticatedUser(jwt).getId();

        Correction correction = correctionService.markAsApplied(
                fileId,
                correctionId,
                actorUserId);

        return toResponse(correction);
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

    private CorrectionResponse toResponse(Correction correction) {

        return new CorrectionResponse(
                correction.getId(),
                correction.getFile() == null
                        ? null
                        : correction.getFile().getId(),
                correction.getUser() == null
                        ? null
                        : correction.getUser().getId(),
                correction.getFieldName(),
                correction.getOriginalValue(),
                correction.getProposedValue(),
                correction.getStatus(),
                correction.getCreatedAt(),
                correction.getUpdatedAt());
    }

    public record CreateCorrectionRequest(
            @NotBlank String fieldName,
            String originalValue,
            @NotBlank String proposedValue) {
    }

    public record UpdateCorrectionRequest(
            @NotBlank String fieldName,
            @NotBlank String proposedValue) {
    }

    public record CorrectionResponse(
            Long id,
            Long fileId,
            Long userId,
            String fieldName,
            String originalValue,
            String proposedValue,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
