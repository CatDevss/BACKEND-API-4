package catdevs.georuraldatahub.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

public record EnvironmentalHistoryFilter(Long camadaId, LocalDate inicio, LocalDate fim) {
    public EnvironmentalHistoryFilter {
        if (camadaId != null && camadaId <= 0)
            throw new IllegalArgumentException("Camada inválida.");
        if ((inicio != null && (inicio.getYear() < 1 || inicio.getYear() > 9999))
                || (fim != null && (fim.getYear() < 1 || fim.getYear() > 9999)))
            throw new IllegalArgumentException("Data fora do intervalo permitido.");
        if (inicio != null && fim != null && inicio.isAfter(fim))
            throw new IllegalArgumentException("O início deve ser anterior ou igual ao fim.");
    }

    public static EnvironmentalHistoryFilter empty() {
        return new EnvironmentalHistoryFilter(null, null, null);
    }

    public boolean isEmpty() {
        return camadaId == null && inicio == null && fim == null;
    }

    public String calculations(List<Object> args) {
        return conditions("f.cam_id", "h.calc_parametros", args);
    }

    public String reports(List<Object> args) {
        return conditions("r.cam_id", "r.rel_parametros", args);
    }

    private String conditions(String layer, String parameters, List<Object> args) {
        String where = "";
        if (camadaId != null) {
            where += " AND " + layer + "=?";
            args.add(camadaId);
        }
        if (inicio != null || fim != null) {
            String end = date(parameters, "fim");
            // O filtro consulta o período dos dados, não a data de execução do cálculo.
            where += " AND " + end + " IS NOT NULL";
            if (inicio != null) {
                where += " AND " + end + ">=?";
                args.add(Date.valueOf(inicio));
            }
            if (fim != null) {
                // Desmatamento tem somente fim e começa na primeira janela do indicador.
                where += " AND COALESCE(" + date(parameters, "inicio") + ",DATE '2008-07-22')<=?";
                args.add(Date.valueOf(fim));
            }
        }
        return where;
    }

    private String date(String parameters, String key) {
        return "TO_DATE(JSON_VALUE(" + parameters + ",'$." + key
                + "' RETURNING VARCHAR2(10) NULL ON ERROR) DEFAULT NULL ON CONVERSION ERROR,'FXYYYY-MM-DD')";
    }
}
