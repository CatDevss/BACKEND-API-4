package catdevs.georuraldatahub.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EnvironmentalLayerDataRepository {
    private final JdbcTemplate jdbc;
    public EnvironmentalLayerDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public record Day(String date, long records) {}
    public record Repair(long featureId, String name, String record) {}
    public record Data(long layerId, long records, long undatedRecords, long repairedFeatures, List<Day> days,
        List<Repair> repairs) {}
    public Data data(long layer) {
        var days = new ArrayList<Day>();
        long[] counts = new long[3];
        jdbc.query("""
            SELECT TO_CHAR(dia,'YYYY-MM-DD'),COUNT(*),SUM(reparada) FROM (
              SELECT TO_DATE(JSON_VALUE(f.fei_atributos,'$.data_evento' RETURNING VARCHAR2(100) NULL ON ERROR)
                    DEFAULT NULL ON CONVERSION ERROR,'FXYYYY-MM-DD') AS dia,
                CASE WHEN JSON_EXISTS(f.fei_atributos,'$._tratamento_espacial') THEN 1 ELSE 0 END AS reparada
              FROM feicao f WHERE f.cam_id=?) GROUP BY dia ORDER BY dia NULLS LAST
            """, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            long n = r.getLong(2);
            counts[0] += n;
            counts[2] += r.getLong(3);
            if (r.getString(1) == null)
                counts[1] += n;
            else
                days.add(new Day(r.getString(1), n));
        }, layer);
        var repairs =
            jdbc.query("SELECT f.fei_id," + EnvironmentalIndicatorRepository.featureNameSql("f.fei_id")
                    + (",JSON_QUERY(f.fei_atributos,'$._tratamento_espacial' RETURNING CLOB) FROM feicao f "
                       + "WHERE f.cam_id=? ")
                    + ("AND JSON_EXISTS(f.fei_atributos,'$._tratamento_espacial') ORDER BY f.fei_id FETCH "
                       + "FIRST 20 ROWS ONLY"),
                (r, n) -> new Repair(r.getLong(1), r.getString(2), r.getString(3)), layer);
        return new Data(layer, counts[0], counts[1], counts[2], days, repairs);
    }
}
