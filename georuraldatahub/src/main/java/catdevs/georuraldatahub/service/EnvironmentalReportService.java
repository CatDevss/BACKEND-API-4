package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.repository.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EnvironmentalReportService {
    private final EnvironmentalReportRepository reports;
    private final EnvironmentalIndicatorRepository calculations;
    private final EnvironmentalIndicatorService indicators;
    private final ObjectMapper mapper = new ObjectMapper();
    public EnvironmentalReportService(EnvironmentalReportRepository reports,
        EnvironmentalIndicatorRepository calculations, EnvironmentalIndicatorService indicators) {
        this.reports = reports;
        this.calculations = calculations;
        this.indicators = indicators;
    }
    public boolean available() {
        return reports.available();
    }
    public boolean deletionAvailable() {
        return reports.deletionAvailable();
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(String id, long user) {
        id(id);
        installed();
        if (!deletionAvailable())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Instale a migração de exclusão de relatórios neste ambiente.");
        if (user <= 0 || !reports.canDelete(user))
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Usuário sem permissão para excluir relatório.");
        header(id, true);
        reports.delete(id, user);
    }
    public boolean calculationDeletionAvailable() {
        return calculations.calculationDeletionAvailable();
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteCalculation(long id, long user) {
        if (id <= 0)
            throw new IllegalArgumentException("Identificador de cálculo inválido.");
        if (!calculationDeletionAvailable())
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Instale a migração de exclusão de cálculos.");
        if (user <= 0 || !reports.canDelete(user))
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Usuário sem permissão para excluir cálculo.");
        try {
            calculations.lockCalculation(id);
            calculations.historyDetail(id);
            calculations.excludeCalculation(id, user);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cálculo não encontrado ou já excluído.");
        }
    }
    private void installed() {
        if (!available())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Agrupamento de relatorios ainda nao instalado neste ambiente.");
    }
    private void id(String id) {
        try {
            if (!UUID.fromString(id).toString().equals(id))
                throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new IllegalArgumentException("Identificador de relatorio invalido.");
        }
    }
    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Relatorio contem JSON invalido.", e);
        }
    }
    private EnvironmentalReportRepository.Header header(String id, boolean lock) {
        id(id);
        installed();
        try {
            return reports.get(id, lock);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Relatorio nao encontrado.");
        }
    }
    private Map<String, Object> view(EnvironmentalReportRepository.Header h) {
        return Map.of("id", h.id(), "name", h.name(), "indicator", h.indicator(), "parameters",
            json(h.parameters()), "total", h.total(), "createdAt", h.createdAt(), "completed", h.completed(),
            "failed", h.failed());
    }
    @Transactional
    public Object create(
        String id, long layer, String name, String indicator, JsonNode parameters, List<Long> ids) {
        id(id);
        installed();
        if (layer <= 0 || indicator == null
            || !Set.of("icv", "irl", "iapp", "isap", "iae", "idesmat", "ifc").contains(indicator)
            || parameters == null || !parameters.isObject() || ids == null || ids.isEmpty()
            || ids.size() > 100000 || ids.stream().anyMatch(n -> n == null || n <= 0)
            || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("Informe indicador, parametros e areas distintas da camada.");
        if (reports.isDeleted(id))
            throw new ResponseStatusException(HttpStatus.GONE, "Relatório excluído; crie outro relatório.");
        var areas = reports.resolveAreas(layer, mapper.valueToTree(ids).toString());
        if (areas.size() != ids.size())
            throw new IllegalArgumentException("Alguma area nao pertence a camada poligonal aprovada.");
        String title = name == null || name.isBlank()
            ? indicator.toUpperCase() + " · " + areas.size() + " areas"
            : name.trim();
        if (title.length() > 200)
            throw new IllegalArgumentException("Nome de relatorio deve ter ate 200 caracteres.");
        try {
            var existing = reports.get(id, false);
            if (!existing.indicator().equals(indicator) || !json(existing.parameters()).equals(parameters)
                || !json(existing.areas()).equals(mapper.valueToTree(areas)))
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Identificador ja usado por outro relatorio.");
            return view(existing);
        } catch (EmptyResultDataAccessException ignored) { /* Primeira solicitacao deste relatorio. */
        }
        reports.create(id, layer, title, indicator, parameters.toString(),
            mapper.valueToTree(areas).toString(), areas.size());
        return view(reports.get(id, false));
    }
    private String plannedName(EnvironmentalReportRepository.Header h, long feature) {
        for (var area : json(h.areas()))
            if (area.path("id").asLong() == feature)
                return area.path("name").asText();
        throw new IllegalArgumentException("Area nao prevista neste relatorio.");
    }
    private JsonNode result(EnvironmentalReportRepository.Item item) {
        try {
            var result = (ObjectNode) indicators.withSources(item.result());
            result.put("calculoId", item.calculationId());
            return result;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Resultado salvo invalido.", e);
        }
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode calculate(String id, long feature) {
        // O bloqueio e a leitura apos espera usam a mesma transacao do calculo e do vinculo.
        var h = header(id, true);
        plannedName(h, feature);
        var existing = reports.item(id, feature);
        if (existing != null) {
            if (existing.calculationId() != null)
                return result(existing);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Area ja registrada com falha; inicie outro relatorio para tentar novamente.");
        }
        var params = (ObjectNode) json(h.parameters());
        params.put("relatorioId", id);
        try {
            var output = (ObjectNode) indicators.withSources(
                calculations.calculateAndSave(feature, h.indicator(), params.toString()));
            long calculation = output.path("calculoId").asLong();
            if (calculation <= 0)
                throw new IllegalStateException("Oracle nao confirmou o calculo salvo.");
            reports.complete(id, feature, calculation);
            return output;
        } catch (DataAccessException e) {
            throw indicators.translate(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Oracle retornou JSON invalido.", e);
        }
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void fail(String id, long feature, String error) {
        var h = header(id, true);
        plannedName(h, feature);
        if (reports.item(id, feature) != null)
            return;
        if (error == null || error.isBlank())
            throw new IllegalArgumentException("Informe o motivo da falha.");
        reports.fail(id, feature, error.substring(0, Math.min(error.length(), 2000)));
    }
    @Transactional(readOnly = true)
    public Object list(String search, String indicator, int page, int size) {
        return list(search, indicator, page, size, EnvironmentalHistoryFilter.empty());
    }
    @Transactional(readOnly = true)
    public Object list(String search, String indicator, int page, int size, EnvironmentalHistoryFilter filters) {
        installed();
        var result = filters.isEmpty() ? reports.list(search, indicator, page, size)
            : reports.list(search, indicator, page, size, filters);
        return Map.of("items", result.items().stream().map(this::view).toList(), "total", result.total(),
            "page", page, "size", size);
    }
    @Transactional(readOnly = true)
    public Object detail(String id, int page, int size) {
        var h = header(id, false);
        var items = new ArrayList<Map<String, Object>>();
        for (var row : reports.items(id, page, size)) {
            var item = new LinkedHashMap<String, Object>();
            item.put("area", Map.of("id", row.featureId(), "name", row.name()));
            if (row.calculationId() != null)
                item.put("snapshot",
                    Map.of("featureId", row.featureId(), "name", row.name(), "indicator", h.indicator(),
                        "parameters", json(h.parameters()), "result", result(row)));
            else if (row.error() != null)
                item.put("error", row.error());
            items.add(item);
        }
        return Map.of("report", view(h), "items", items, "total", h.total(), "page", page, "size", size);
    }
}
