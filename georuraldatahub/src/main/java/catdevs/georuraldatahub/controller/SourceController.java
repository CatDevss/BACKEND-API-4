package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.service.SourceService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
@RequestMapping("/fontes")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    // CREATE: cadastrar uma fonte
    @PostMapping
    public ResponseEntity<SourceResponseDTO> create(
            @Valid @RequestBody SourceCreateRequestDTO request) {

        SourceResponseDTO response = sourceService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // READ: listar todas as fontes
    @GetMapping
    public ResponseEntity<List<SourceResponseDTO>> listAll() {
        return ResponseEntity.ok(sourceService.listAll());
    }

    // READ: buscar uma fonte pelo ID
    @GetMapping("/{id}")
    public ResponseEntity<SourceResponseDTO> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(sourceService.findById(id));
    }

    // UPDATE: atualizar uma fonte
    @PutMapping("/{id}")
    public ResponseEntity<SourceResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody SourceCreateRequestDTO request) {

        return ResponseEntity.ok(sourceService.update(id, request));
    }

    // DELETE: excluir uma fonte
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {

        sourceService.delete(id);

        return ResponseEntity.noContent().build();
    }
}