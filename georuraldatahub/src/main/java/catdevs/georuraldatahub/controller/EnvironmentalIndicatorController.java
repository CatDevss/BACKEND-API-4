package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository.ImportedLayer;
import catdevs.georuraldatahub.service.EnvironmentalIndicatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "environmental.enabled", havingValue = "true")
public class EnvironmentalIndicatorController {
    private final EnvironmentalIndicatorService service;
    private final ObjectMapper mapper = new ObjectMapper();
    public EnvironmentalIndicatorController(EnvironmentalIndicatorService service) { this.service = service; }

    @PostMapping("/conjuntos/{datasetId}/versoes/{versionId}/camada-oracle")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportedLayer importGeoJson(@PathVariable long datasetId, @PathVariable long versionId) {
        return service.importGeoJson(datasetId, versionId);
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/calcular",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculate(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculate(featureId, mapper.valueToTree(parameters)).toString();
    }
    @PostMapping(value = "/imoveis/{featureId}/indicadores/icv",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIcv(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIcv(featureId, mapper.valueToTree(parameters)).toString();
    }
    @PostMapping(value = "/imoveis/{featureId}/indicadores/irl",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIrl(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIndicator(featureId, mapper.valueToTree(parameters), "irl").toString();
    }
    @PostMapping(value = "/imoveis/{featureId}/indicadores/iapp",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIapp(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIndicator(featureId, mapper.valueToTree(parameters), "iapp").toString();
    }
    @PostMapping(value = "/imoveis/{featureId}/indicadores/isap",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIsap(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIndicator(featureId, mapper.valueToTree(parameters), "isap").toString();
    }
    @PostMapping(value = "/imoveis/{featureId}/indicadores/iae",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIae(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIndicator(featureId, mapper.valueToTree(parameters), "iae").toString();
    }
    @PostMapping(value = "/imoveis/{featureId}/indicadores/idesmat",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIdesmat(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIndicator(featureId, mapper.valueToTree(parameters), "idesmat").toString();
    }
    @PostMapping(value = "/imoveis/{featureId}/indicadores/ifc",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIfc(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIndicator(featureId, mapper.valueToTree(parameters), "ifc").toString();
    }
}
