package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.service.EnvironmentalExportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@CrossOrigin
@ConditionalOnProperty(name = "environmental.enabled", havingValue = "true")
public class EnvironmentalExportController {
    private final EnvironmentalExportService service;

    public EnvironmentalExportController(EnvironmentalExportService service) {
        this.service = service;
    }

    public record Report(java.util.List<java.util.List<Object>> rows) {
    }

    @PostMapping("/ambiental/exportacoes/xlsx")
    public ResponseEntity<byte[]> export(@RequestBody Report report) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=indicador-ambiental.xlsx")
                .body(service.workbook(report.rows()));
    }
}
