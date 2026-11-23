package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.service.LayerService;

@RestController
public class LayerController {
    private final LayerService layerService;

    public LayerController(LayerService layerService) {
        this.layerService = layerService;
    }

    @PreAuthorize(Permissions.DATASETS_READ)
    @GetMapping("/conjuntos/{id}/camadas")
    public List<LayerInfoDTO> listLayers(@PathVariable Long id) {
        return layerService.listLayers(id);
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping(value = "/arquivos/{id}/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getLayer(@PathVariable Long id) throws IOException {
        JsonNode geoJson = layerService.getFileLayer(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(geoJson.toString());
    }

    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping(value = "/camadas/{id}/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTreatedLayer(@PathVariable Long id) throws IOException {
        return ResponseEntity.status(org.springframework.http.HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(layerService.getTreatedLayer(id).toString());
    }
}
