package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.service.SourceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fontes")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    public ResponseEntity<SourceResponseDTO> create(
            @RequestBody SourceCreateRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(sourceService.create(request));
    }
}