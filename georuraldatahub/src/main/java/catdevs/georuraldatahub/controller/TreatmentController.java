package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import catdevs.georuraldatahub.processing.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class TreatmentController {
    private final TreatmentService service;

    public TreatmentController(TreatmentService service) {
        this.service = service;
    }

    @PreAuthorize(Permissions.INGESTION_RUN)
    @PostMapping("/arquivos/{fileId}/tratamentos")
    public ResponseEntity<JpaTreatmentStore.View> start(@PathVariable Long fileId,
            @Valid @RequestBody TreatmentRequest request) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(fileId, request));
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping("/tratamentos/{id}")
    public ResponseEntity<JpaTreatmentStore.View> get(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(service.enabledStore().get(id));
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping("/arquivos/{fileId}/tratamentos")
    public ResponseEntity<Map<String, Object>> list(@PathVariable Long fileId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.status(HttpStatus.OK).body(service.enabledStore().list(fileId, page, size));
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping("/rasters")
    public ResponseEntity<Map<String, Object>> rasters(@RequestParam(required = false) Long featureId,
            @RequestParam(required = false) java.util.List<Double> bbox,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) throws Exception {
        return ResponseEntity.status(HttpStatus.OK)
                .body(service.enabledStore().searchRasters(featureId, bbox, page, size));
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping("/rasters/{id}")
    public ResponseEntity<Map<String, Object>> raster(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(service.enabledStore().raster(id));
    }
}
