package catdevs.georuraldatahub.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import catdevs.georuraldatahub.dto.OccurrenceAssigneeRequest;
import catdevs.georuraldatahub.dto.OccurrenceDTO;
import catdevs.georuraldatahub.dto.OccurrenceHistoryDTO;
import catdevs.georuraldatahub.dto.OccurrenceStatusRequest;
import catdevs.georuraldatahub.entity.enums.OccurrenceStatus;
import catdevs.georuraldatahub.service.OccurrenceService;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@ConditionalOnProperty(name = "features.occurrences.enabled", havingValue = "true", matchIfMissing = true)
@CrossOrigin
@RequestMapping("/ocorrencias")
public class OccurrenceController {

    private final OccurrenceService occurrenceService;

    public OccurrenceController(OccurrenceService occurrenceService) {
        this.occurrenceService = occurrenceService;
    }

    @GetMapping
    public Page<OccurrenceDTO> list(
            @RequestParam(required = false) OccurrenceStatus status,
            @RequestParam(required = false) Long fileId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String field,
            @PageableDefault(size = 20, sort = "createdAt")
            Pageable pageable) {

        return occurrenceService.list(
                status, fileId, code, field, pageable);
    }

    @GetMapping("/{id}")
    public OccurrenceDTO detail(@PathVariable Long id) {
        return occurrenceService.detail(id);
    }

    @GetMapping("/{id}/historico")
    public List<OccurrenceHistoryDTO> history(@PathVariable Long id) {
        return occurrenceService.history(id);
    }

    @PatchMapping("/{id}/responsavel")
    public OccurrenceDTO assign(
            @PathVariable Long id,
            @Valid @RequestBody OccurrenceAssigneeRequest request) {

        return occurrenceService.assign(
                id,
                request.userId(),
                request.actorUserId(),
                request.details());
    }

    @PatchMapping("/{id}/status")
    public OccurrenceDTO changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody OccurrenceStatusRequest request) {

        return occurrenceService.changeStatus(
                id,
                request.status(),
                request.actorUserId(),
                request.details());
    }
}