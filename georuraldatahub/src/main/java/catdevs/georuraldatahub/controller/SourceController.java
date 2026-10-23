package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.service.SourceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@CrossOrigin
@RequestMapping({"/api/fontes", "/fontes"})
public class SourceController {

    private final SourceService sources;

    public SourceController(SourceService sources) {
        this.sources = sources;
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
}