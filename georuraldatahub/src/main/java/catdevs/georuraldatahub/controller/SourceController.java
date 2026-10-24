package catdevs.georuraldatahub.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.dto.SourceUpdateRequestDTO;
import catdevs.georuraldatahub.service.SourceService;
import jakarta.validation.Valid;

@RestController
@CrossOrigin
@RequestMapping("/fontes")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    // CADASTRAR FONTE
    @PostMapping
    public ResponseEntity<SourceResponseDTO> create(
            @Valid @RequestBody SourceCreateRequestDTO request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(sourceService.create(request));
    }

    // LISTAR FONTES
    @GetMapping
    public ResponseEntity<List<SourceResponseDTO>> listAll() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(sourceService.listAll());
    }

    // BUSCAR FONTE POR ID
    @GetMapping("/{id}")
    public ResponseEntity<SourceResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(sourceService.findById(id));
    }

    // ALTERAR FONTE
    @PutMapping("/{id}")
    public ResponseEntity<SourceResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody SourceUpdateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(sourceService.update(id, request));
    }

    // DESATIVAR FONTE
    @PatchMapping("/{id}/desativar")
    public ResponseEntity<SourceResponseDTO> deactivate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(sourceService.deactivate(id));
    }

    // ATIVAR FONTE
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<SourceResponseDTO> activate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(sourceService.activate(id));
    }
}