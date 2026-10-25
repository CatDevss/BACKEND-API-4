package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.*;
import catdevs.georuraldatahub.service.QuarantineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
@RequestMapping("/quarentenas")
public class QuarantineController {
    private final QuarantineService service;
    public QuarantineController(QuarantineService service) { this.service = service; }

    @GetMapping
    public QuarantinePageDTO list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long responsibleId, @RequestParam(required = false) Long fileId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(status, responsibleId, fileId, unassigned, page, size);
    }
    @GetMapping("/{id}")
    public QuarantineResponseDTO get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/{id}/assumir")
    public QuarantineResponseDTO claim(@PathVariable Long id, @Valid @RequestBody QuarantineActorRequestDTO request) {
        return service.claim(id, request.userId());
    }
    @PatchMapping("/{id}/observacao")
    public QuarantineResponseDTO updateTreatment(@PathVariable Long id, @Valid @RequestBody QuarantineTreatmentRequestDTO request) {
        return service.updateTreatment(id, request.userId(), request.treatment());
    }
    @PostMapping("/{id}/resolver")
    public QuarantineResponseDTO resolve(@PathVariable Long id, @Valid @RequestBody QuarantineTreatmentRequestDTO request) {
        return service.resolve(id, request.userId(), request.treatment());
    }
}
