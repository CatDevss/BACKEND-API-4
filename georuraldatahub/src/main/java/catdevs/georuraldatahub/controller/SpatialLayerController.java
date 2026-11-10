package catdevs.georuraldatahub.controller;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.service.SpatialLayerService;
import catdevs.georuraldatahub.service.SpatialLayerService.LayerLoadResult;

@RestController
@CrossOrigin
public class SpatialLayerController {

    private final SpatialLayerService spatialLayerService;

    public SpatialLayerController(SpatialLayerService spatialLayerService) {
        this.spatialLayerService = spatialLayerService;
    }

    /** Cria a camada Oracle Spatial a partir de um arquivo já tratado pelo dicionário. */
    @PostMapping("/conjuntos/{datasetId}/arquivos/{fileId}/camada")
    public ResponseEntity<LayerLoadResult> createLayer(@PathVariable Long datasetId,
            @PathVariable Long fileId, @org.springframework.web.bind.annotation.RequestParam(required = false) Long userId) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(spatialLayerService.createLayerFromFile(datasetId, fileId, userId));
    }
}
