package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.CollectionRequestDTO;
import catdevs.georuraldatahub.dto.CollectionResponseDTO;
import catdevs.georuraldatahub.service.CollectionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fontes/{sourceId}/coletas")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping
    public ResponseEntity<CollectionResponseDTO> collect(
            @PathVariable Long sourceId,
            @Valid @RequestBody CollectionRequestDTO request
    ) {
        CollectionResponseDTO response =
                collectionService.collect(sourceId, request);

        if ("ERRO".equals(response.status())) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<CollectionResponseDTO>> history(
            @PathVariable Long sourceId
    ) {
        return ResponseEntity.ok(
                collectionService.history(sourceId)
        );
    }

    @GetMapping("/{ingestionId}")
    public ResponseEntity<CollectionResponseDTO> status(
            @PathVariable Long sourceId,
            @PathVariable Long ingestionId
    ) {
        return ResponseEntity.ok(
                collectionService.status(sourceId, ingestionId)
        );
    }
}