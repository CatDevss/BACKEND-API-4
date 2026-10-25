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

import catdevs.georuraldatahub.entity.Correction;
import catdevs.georuraldatahub.service.CorrectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Endpoints REST para criar e gerenciar correções propostas para arquivos.
 *
 * Observação: o projeto ainda precisa integrar o userId ao usuário
 * autenticado quando a autenticação/autorização estiver configurada.
 */
@RestController
@Validated
@RequestMapping("/arquivos/{fileId}/correcoes")
public class CorrectionController {

    private final CorrectionService correctionService;

    public CorrectionController(CorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    /**
     * Cria uma correção em rascunho.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CorrectionResponse create(
            @PathVariable Long fileId,
            @Valid @RequestBody CreateCorrectionRequest request) {

        Correction correction = correctionService.create(
                fileId,
                request.userId(),
                request.fieldName(),
                request.originalValue(),
                request.proposedValue());

        return toResponse(correction);
    }

    /**
     * Lista as correções associadas ao arquivo.
     */
    @GetMapping
    public List<CorrectionResponse> list(@PathVariable Long fileId) {
        return correctionService.findByFile(fileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Consulta uma correção específica do arquivo.
     */
    @GetMapping("/{correctionId}")
    public CorrectionResponse get(
            @PathVariable Long fileId,
            @PathVariable Long correctionId) {

        return toResponse(correctionService.findById(fileId, correctionId));
    }

    /**
     * Atualiza o campo e o valor proposto.
     * O valor original permanece preservado.
     */
    @PutMapping("/{correctionId}")
    public CorrectionResponse update(
            @PathVariable Long fileId,
            @PathVariable Long correctionId,
            @Valid @RequestBody UpdateCorrectionRequest request) {

        Correction correction = correctionService.update(
                fileId,
                correctionId,
                request.fieldName(),
                request.proposedValue());

        return toResponse(correction);
    }

    /**
     * Exclui uma correção somente se ela ainda estiver em rascunho.
     */
    @DeleteMapping("/{correctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long fileId,
            @PathVariable Long correctionId) {

        correctionService.delete(fileId, correctionId);
    }

    /**
     * Marca a correção como aplicada, bloqueando futuras edições e exclusões.
     */
    @PostMapping("/{correctionId}/aplicar")
    public CorrectionResponse apply(
            @PathVariable Long fileId,
            @PathVariable Long correctionId) {

        return toResponse(correctionService.markAsApplied(fileId, correctionId));
    }

    private CorrectionResponse toResponse(Correction correction) {
        return new CorrectionResponse(
                correction.getId(),
                correction.getFile() == null ? null : correction.getFile().getId(),
                correction.getUser() == null ? null : correction.getUser().getId(),
                correction.getFieldName(),
                correction.getOriginalValue(),
                correction.getProposedValue(),
                correction.getStatus(),
                correction.getCreatedAt(),
                correction.getUpdatedAt());
    }

    public record CreateCorrectionRequest(
            @NotNull Long userId,
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