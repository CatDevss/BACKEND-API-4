package catdevs.georuraldatahub.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.dto.IndicatorCalculationRequestDTO;
import catdevs.georuraldatahub.dto.IndicatorCalculationResponseDTO;
import catdevs.georuraldatahub.service.EnvironmentalIndicatorCalculationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/indicadores")
public class EnvironmentalIndicatorController {

    private final EnvironmentalIndicatorCalculationService service;

    public EnvironmentalIndicatorController(EnvironmentalIndicatorCalculationService service) {
        this.service = service;
    }

    @PostMapping("/calcular")
    public ResponseEntity<IndicatorCalculationResponseDTO> calculate(
            @Valid @RequestBody IndicatorCalculationRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.calculate(request));
    }

    @GetMapping("/execucoes/{calculationId}")
    public ResponseEntity<IndicatorCalculationResponseDTO> findById(
            @PathVariable Long calculationId) {
        return ResponseEntity.ok(service.findById(calculationId));
    }

    @GetMapping("/feicoes/{featureId}/execucoes")
    public ResponseEntity<List<IndicatorCalculationResponseDTO>> findByFeature(
            @PathVariable Long featureId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findByFeatureId(featureId, page, size));
    }
}