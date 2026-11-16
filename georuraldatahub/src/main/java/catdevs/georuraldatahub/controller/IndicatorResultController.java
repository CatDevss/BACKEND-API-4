package catdevs.georuraldatahub.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.dto.IndicatorResultCreateDTO;
import catdevs.georuraldatahub.dto.IndicatorResultResponseDTO;
import catdevs.georuraldatahub.service.IndicatorResultService;
import jakarta.validation.Valid;

@RestController
@CrossOrigin
@RequestMapping("/indicadores/resultados")
public class IndicatorResultController {

    private final IndicatorResultService service;

    public IndicatorResultController(IndicatorResultService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IndicatorResultResponseDTO> save(
            @Valid @RequestBody IndicatorResultCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IndicatorResultResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<IndicatorResultResponseDTO>> findByDataset(
            @RequestParam Long datasetId,
            @RequestParam(required = false) String indicatorCode,
            @RequestParam(required = false) String propertyReference) {
        return ResponseEntity.ok(service.findByDataset(datasetId, indicatorCode, propertyReference));
    }
}