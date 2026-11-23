package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import catdevs.georuraldatahub.service.EnvironmentalExportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name = "environmental.enabled", havingValue = "true")
public class EnvironmentalExportController {
    private final EnvironmentalExportService service;

    public EnvironmentalExportController(EnvironmentalExportService service) {
        this.service = service;
    }

    public record Report(java.util.List<java.util.List<Object>> rows) {
    }

    @PreAuthorize(Permissions.MAP_READ)
    @PostMapping("/ambiental/exportacoes/xlsx")
    public ResponseEntity<byte[]> export(@RequestBody Report report) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=indicador-ambiental.xlsx")
                .body(service.workbook(report.rows()));
    }
}
