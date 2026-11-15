package catdevs.georuraldatahub.repository;

import java.sql.Clob;
import java.sql.Types;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Oracle owns import, spatial operations, formulas and calculation history. */
@Repository
public class EnvironmentalIndicatorRepository {
    private final JdbcTemplate jdbc;

    public EnvironmentalIndicatorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ImportedLayer(long camadaId, int quantidade) { }

    public ImportedLayer importGeoJson(long datasetId, long versionId) {
        return jdbc.execute((ConnectionCallback<ImportedLayer>) connection -> {
            try (var call = connection.prepareCall("{call pkg_carga_ambiental.importar_geojson(?,?,?,?)}")) {
                call.setQueryTimeout(300);
                call.setLong(1, datasetId); call.setLong(2, versionId);
                call.registerOutParameter(3, Types.NUMERIC); call.registerOutParameter(4, Types.NUMERIC);
                call.execute();
                return new ImportedLayer(call.getLong(3), call.getInt(4));
            }
        });
    }

    public String calculate(long propertyId, String parameters) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            Clob input = connection.createClob();
            try (var call = connection.prepareCall("{call pkg_indicadores_ambientais.calcular(?,?,?)}")) {
                input.setString(1, parameters);
                call.setQueryTimeout(300);
                call.setLong(1, propertyId); call.setClob(2, input);
                call.registerOutParameter(3, Types.CLOB); call.execute();
                Clob output = call.getClob(3);
                try { return output.getSubString(1, Math.toIntExact(output.length())); }
                finally { output.free(); }
            } finally { input.free(); }
        });
    }
}
