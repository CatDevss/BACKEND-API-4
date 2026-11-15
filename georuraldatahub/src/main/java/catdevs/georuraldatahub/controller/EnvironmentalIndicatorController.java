package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository.ImportedLayer;
import catdevs.georuraldatahub.service.EnvironmentalIndicatorService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "environmental.enabled", havingValue = "true")
public class EnvironmentalIndicatorController {
    private final EnvironmentalIndicatorService service;
    public EnvironmentalIndicatorController(EnvironmentalIndicatorService service) { this.service = service; }

    @PostMapping("/conjuntos/{datasetId}/versoes/{versionId}/camada-oracle")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportedLayer importGeoJson(@PathVariable long datasetId, @PathVariable long versionId) {
        return service.importGeoJson(datasetId, versionId);
    }

    @PostMapping("/imoveis/{featureId}/indicadores/calcular")
    public JsonNode calculate(@PathVariable long featureId, @RequestBody JsonNode parameters) {
        return service.calculate(featureId, parameters);
    }
}
