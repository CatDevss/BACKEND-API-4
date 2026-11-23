package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import catdevs.georuraldatahub.repository.EnvironmentalLayerDataRepository;
import catdevs.georuraldatahub.repository.EnvironmentalHistoryFilter;
import catdevs.georuraldatahub.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "environmental.enabled", havingValue = "true")
public class EnvironmentalReportController {
    private final EnvironmentalReportService reports;
    private final EnvironmentalIndicatorService indicators;
    private final EnvironmentalLayerDataRepository data;
    public EnvironmentalReportController(EnvironmentalReportService reports,
        EnvironmentalIndicatorService indicators, EnvironmentalLayerDataRepository data) {
        this.reports = reports;
        this.indicators = indicators;
        this.data = data;
    }
    public record Request(
        String id, long layerId, String name, String indicator, JsonNode parameters, List<Long> featureIds) {}
    public record Failure(String message) {}
    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping("/ambiental/relatorios/recursos")
    public Object capabilities() {
        return Map.of("enabled", reports.available(), "deletionEnabled", reports.deletionAvailable(),
            "calculationDeletionEnabled", reports.calculationDeletionAvailable());
    }
    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping("/ambiental/camadas/{id}/dados")
    public Object data(@PathVariable long id) {
        indicators.layer(id);
        return data.data(id);
    }
    @PreAuthorize(Permissions.MAP_READ)
    @PostMapping("/ambiental/relatorios")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@RequestBody Request r) {
        return reports.create(r.id(), r.layerId(), r.name(), r.indicator(), r.parameters(), r.featureIds());
    }
    @PreAuthorize(Permissions.MAP_READ)
    @PostMapping("/ambiental/relatorios/{id}/feicoes/{feature}/calcular")
    public JsonNode calculate(@PathVariable String id, @PathVariable long feature) {
        return reports.calculate(id, feature);
    }
    @PreAuthorize(Permissions.MAP_READ)
    @PostMapping("/ambiental/relatorios/{id}/feicoes/{feature}/falha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@PathVariable String id, @PathVariable long feature, @RequestBody Failure r) {
        reports.fail(id, feature, r.message());
    }
    private void page(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Paginacao invalida.");
    }
    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping("/ambiental/relatorios")
    public Object list(@RequestParam(defaultValue = "") String busca,
        @RequestParam(defaultValue = "") String indicador,
        @RequestParam(required = false) Long camadaId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        page(page, size);
        var filters = new EnvironmentalHistoryFilter(camadaId, inicio, fim);
        return filters.isEmpty() ? reports.list(busca, indicador, page, size)
            : reports.list(busca, indicador, page, size, filters);
    }
    @PreAuthorize(Permissions.REPORTS_DELETE)
    @DeleteMapping("/ambiental/calculos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCalculation(@PathVariable long id, @RequestParam long usuarioId) {
        reports.deleteCalculation(id, usuarioId);
    }
    @PreAuthorize(Permissions.REPORTS_DELETE)
    @DeleteMapping("/ambiental/relatorios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestParam long usuarioId) {
        reports.delete(id, usuarioId);
    }
    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping("/ambiental/relatorios/{id}")
    public Object detail(@PathVariable String id, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        page(page, size);
        return reports.detail(id, page, size);
    }
}
