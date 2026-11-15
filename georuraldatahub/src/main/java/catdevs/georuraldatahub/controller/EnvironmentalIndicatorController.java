package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository.ImportedLayer;
import catdevs.georuraldatahub.service.EnvironmentalIndicatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@ConditionalOnProperty(name = "environmental.enabled", havingValue = "true")
public class EnvironmentalIndicatorController {
    private final EnvironmentalIndicatorService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnvironmentalIndicatorController(EnvironmentalIndicatorService service) {
        this.service = service;
    }

    @GetMapping("/ambiental/camadas")
    public Object layers(@RequestParam(defaultValue="") String busca,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        checkPage(page,size);
        return service.layers(busca,page,size);
    }

    @GetMapping("/ambiental/camadas/{layerId}/feicoes")
    public Object features(@PathVariable long layerId, @RequestParam(defaultValue="") String busca,
            @RequestParam(required=false) String campo, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        checkPage(page,size);
        return service.features(layerId,busca,campo,page,size);
    }

    private void checkPage(int page,int size) {
        if (page<0 || page>100000 || size<1 || size>100)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginacao invalida.");
    }

    public record CombinedLayerRequest(String nome, java.util.List<Long> camadas) {}

    @PostMapping("/conjuntos/{datasetId}/camadas/combinar")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportedLayer combineLayers(@PathVariable long datasetId,
            @RequestBody CombinedLayerRequest request) {
        return service.combineLayers(datasetId, request.nome(), request.camadas());
    }

    @PostMapping("/conjuntos/{datasetId}/versoes/{versionId}/camada-oracle")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportedLayer importGeoJson(@PathVariable long datasetId, @PathVariable long versionId) {
        return service.importGeoJson(datasetId, versionId);
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/calcular", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculate(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculate(featureId, mapper.valueToTree(parameters)).toString();
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/icv", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIcv(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIcv(featureId, mapper.valueToTree(parameters)).toString();
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/irl", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIrl(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIrl(featureId, mapper.valueToTree(parameters)).toString();
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/iapp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIapp(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIapp(featureId, mapper.valueToTree(parameters)).toString();
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/isap", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIsap(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIsap(featureId, mapper.valueToTree(parameters)).toString();
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/iae", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIae(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIae(featureId, mapper.valueToTree(parameters)).toString();
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/idesmat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIdesmat(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIdesmat(featureId, mapper.valueToTree(parameters)).toString();
    }

    @PostMapping(value = "/imoveis/{featureId}/indicadores/ifc", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String calculateIfc(@PathVariable long featureId, @RequestBody Map<String, Object> parameters) {
        return service.calculateIfc(featureId, mapper.valueToTree(parameters)).toString();
    }
}
