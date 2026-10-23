package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.CollectionRequestDTO;
import catdevs.georuraldatahub.dto.CollectionResponseDTO;
import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.service.CollectionService;
import catdevs.georuraldatahub.service.SourceService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping({"/api/fontes", "/fontes"})
public class SourceController {

    private final SourceService sources;
    private final CollectionService collections;

    public SourceController(
            SourceService sources,
            CollectionService collections) {
        this.sources = sources;
        this.collections = collections;
    }

    @PostMapping
    public ResponseEntity<SourceResponseDTO> create(
            @Valid @RequestBody SourceCreateRequestDTO request) {
        return ResponseEntity.status(201).body(sources.create(request));
    }

    @GetMapping
    public List<SourceResponseDTO> list() {
        return sources.listAll();
    }

    @GetMapping("/{id}")
    public SourceResponseDTO get(@PathVariable Long id) {
    return sources.getById(id);
    }

    @PutMapping("/{id}")
    public SourceResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody SourceCreateRequestDTO request) {
        return sources.update(id, request);
    }

    @DeleteMapping("/{id}")
    public SourceResponseDTO deactivate(@PathVariable Long id) {
        return sources.deactivate(id);
    }

    @PostMapping("/{id}/coletas")
    public ResponseEntity<CollectionResponseDTO> collect(
            @PathVariable Long id,
            @Valid @RequestBody CollectionRequestDTO request) {
        return ResponseEntity.status(202)
                .body(collections.collect(id, request.userId()));
    }

    @GetMapping("/{id}/coletas")
    public List<CollectionResponseDTO> history(@PathVariable Long id) {
        return collections.history(id);
    }

    @GetMapping("/{id}/coletas/{collectionId}")
    public CollectionResponseDTO status(
            @PathVariable Long id,
            @PathVariable Long collectionId) {
        return collections.status(id, collectionId);
    }
}