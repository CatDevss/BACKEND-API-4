package catdevs.georuraldatahub.repository;

import java.sql.Clob;
import java.sql.Types;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EnvironmentalIndicatorRepository {
    private final JdbcTemplate jdbc;

    public EnvironmentalIndicatorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ImportedLayer(long camadaId, int quantidade) {
    }

    public ImportedLayer importGeoJson(long datasetId, long versionId) {
        return jdbc.execute((ConnectionCallback<ImportedLayer>) connection -> {
            try (var call = connection.prepareCall("{call pkg_carga_ambiental.importar_geojson(?,?,?,?)}")) {
                call.setQueryTimeout(300);
                call.setLong(1, datasetId);
                call.setLong(2, versionId);
                call.registerOutParameter(3, Types.NUMERIC);
                call.registerOutParameter(4, Types.NUMERIC);
                call.execute();
                return new ImportedLayer(call.getLong(3), call.getInt(4));
            }
        });
    }

    public String calculateIcv(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "icv");
    }

    public String calculateIrl(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "irl");
    }

    public String calculateIapp(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "iapp");
    }

    public String calculateIsap(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "isap");
    }

    public String calculateIae(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "iae");
    }

    public String calculateIdesmat(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "idesmat");
    }

    public String calculateIfc(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "ifc");
    }

    public String calculateIndicator(long propertyId, String parameters, String indicator) {
        String function = switch (indicator) {
            case "icv", "irl", "iapp", "isap", "iae", "idesmat", "ifc" -> indicator;
            default -> throw new IllegalArgumentException("Indicador desconhecido.");
        };
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            Clob input = connection.createClob();
            try (var call = connection.prepareCall("{? = call pkg_indicadores_ambientais." + function + "(?,?)}")) {
                input.setString(1, parameters);
                call.setQueryTimeout(300);
                call.registerOutParameter(1, Types.CLOB);
                call.setLong(2, propertyId);
                call.setClob(3, input);
                call.execute();
                Clob output = call.getClob(1);
                try {
                    return output.getSubString(1, Math.toIntExact(output.length()));
                } finally {
                    output.free();
                }
            } finally {
                input.free();
            }
        });
    }

    public String calculate(long propertyId, String parameters) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            Clob input = connection.createClob();
            try (var call = connection.prepareCall("{call pkg_indicadores_ambientais.calcular(?,?,?)}")) {
                input.setString(1, parameters);
                call.setQueryTimeout(300);
                call.setLong(1, propertyId);
                call.setClob(2, input);
                call.registerOutParameter(3, Types.CLOB);
                call.execute();
                Clob output = call.getClob(3);
                try {
                    return output.getSubString(1, Math.toIntExact(output.length()));
                } finally {
                    output.free();
                }
            } finally {
                input.free();
            }
        });
    }
}
