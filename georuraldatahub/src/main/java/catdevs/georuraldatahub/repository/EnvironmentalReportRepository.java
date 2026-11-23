package catdevs.georuraldatahub.repository;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EnvironmentalReportRepository {
    private final JdbcTemplate jdbc;
    public EnvironmentalReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public record Area(long id, String name) {}
    public record Header(String id, String name, String indicator, String parameters, String areas, int total,
        String createdAt, long completed, long failed) {}
    public record
        Item(long featureId, String name, String state, String error, Long calculationId, String result) {}
    private static final String SELECT =
        "SELECT r.rel_id,r.rel_nome,r.rel_indicador,r.rel_parametros,r.rel_areas,r.rel_total,"
        + "TO_CHAR(r.rel_criado AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'),"
        + "(SELECT COUNT(*) FROM relatorio_ambiental_item i WHERE i.rel_id=r.rel_id AND "
        + "i.item_estado='CONCLUIDO'),"
        + "(SELECT COUNT(*) FROM relatorio_ambiental_item i WHERE i.rel_id=r.rel_id AND "
        + "i.item_estado='ERRO') FROM relatorio_ambiental r ";
    private Header row(ResultSet r, int n) throws SQLException {
        return new Header(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5),
            r.getInt(6), r.getString(7), r.getLong(8), r.getLong(9));
    }
    public boolean available() {
        return Integer.valueOf(2).equals(
            jdbc.queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name IN "
                    + "('RELATORIO_AMBIENTAL','RELATORIO_AMBIENTAL_ITEM')",
                Integer.class));
    }
    private boolean exclusionTableExists() {
        return Integer.valueOf(1).equals(jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_tables WHERE table_name='RELATORIO_AMBIENTAL_EXCLUSAO'",
            Integer.class));
    }
    public boolean deletionAvailable() {
        return exclusionTableExists()
            && Integer.valueOf(3).equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name "
                + "AND o.object_type='TRIGGER' WHERE t.status='ENABLED' AND o.status='VALID' AND "
                + "t.trigger_name IN "
                + "('TRG_REL_EXCLUSAO_VALIDAR','TRG_REL_EXCLUSAO_IMUTAVEL','TRG_AUD_REL_EXCLUSAO')",
                Integer.class));
    }
    private String active(String alias) {
        return exclusionTableExists()
            ? " AND NOT EXISTS (SELECT 1 FROM relatorio_ambiental_exclusao x WHERE x.rel_id=" + alias
                + ".rel_id)"
            : "";
    }
    public boolean isDeleted(String id) {
        return exclusionTableExists()
            && jdbc.queryForObject(
                   "SELECT COUNT(*) FROM relatorio_ambiental_exclusao WHERE rel_id=?", Long.class, id)
            > 0;
    }
    public boolean canDelete(long user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE usr_id=? AND usr_status=1 AND "
                       + "usr_tipo IN ('ADMIN','GESTOR','OPERADOR','ANALISTA')",
                   Long.class, user)
            == 1;
    }
    public void delete(String id, long user) {
        jdbc.update("INSERT INTO relatorio_ambiental_exclusao(rel_id,usr_id) VALUES(?,?)", id, user);
    }
    public Header get(String id, boolean lock) {
        if (lock)
            jdbc.queryForObject(
                "SELECT rel_id FROM relatorio_ambiental WHERE rel_id=? FOR UPDATE", String.class, id);
        return jdbc.queryForObject(SELECT + " WHERE r.rel_id=?" + active("r"), this::row, id);
    }
    public EnvironmentalIndicatorRepository.Page<Header> list(
        String search, String indicator, int page, int size) {
        return list(search, indicator, page, size, EnvironmentalHistoryFilter.empty());
    }
    public EnvironmentalIndicatorRepository.Page<Header> list(
        String search, String indicator, int page, int size, EnvironmentalHistoryFilter filters) {
        String where =
            " WHERE (LOWER(r.rel_nome) LIKE ? OR EXISTS (SELECT 1 FROM JSON_TABLE(r.rel_areas,'$[*]' "
            + "COLUMNS(nome VARCHAR2(1000) PATH '$.name')) p WHERE LOWER(p.nome) LIKE ?))";
        where += active("r");
        var args = new ArrayList<Object>();
        String term = "%" + search.toLowerCase(Locale.ROOT) + "%";
        args.add(term);
        args.add(term);
        if (!indicator.isBlank()) {
            where += " AND r.rel_indicador=?";
            args.add(indicator);
        }
        where += filters.reports(args);
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM relatorio_ambiental r" + where, Long.class, args.toArray());
        args.add(page * size);
        args.add(size);
        return new EnvironmentalIndicatorRepository.Page<>(
            jdbc.query(
                SELECT + where + " ORDER BY r.rel_criado DESC,r.rel_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                this::row, args.toArray()),
            total, page, size);
    }
    public List<Area> resolveAreas(long layer, String ids) {
        return jdbc.execute((ConnectionCallback<List<Area>>) connection -> {
            Clob clob = connection.createClob();
            clob.setString(1, ids);
            try (var statement = connection.prepareStatement("SELECT f.fei_id,"
                     + EnvironmentalIndicatorRepository.featureNameSql("f.fei_id")
                     + (" FROM JSON_TABLE(?,'$[*]' COLUMNS(id NUMBER PATH '$')) p JOIN feicao f ON "
                         + "f.fei_id=p.id JOIN camada c ON c.cam_id=f.cam_id JOIN versao v ON "
                         + "v.ver_id=c.ver_id ")
                     + ("WHERE f.cam_id=? AND MOD(f.fei_geometria.sdo_gtype,100) IN (3,7) AND v.ver_status "
                         + "IN ('TRATADA','PUBLICADA') ORDER BY 2,f.fei_id"))) {
                statement.setClob(1, clob);
                statement.setLong(2, layer);
                try (var rows = statement.executeQuery()) {
                    var areas = new ArrayList<Area>();
                    while (rows.next()) areas.add(new Area(rows.getLong(1), rows.getString(2)));
                    return areas;
                }
            } finally {
                clob.free();
            }
        });
    }
    public void create(
        String id, long layer, String name, String indicator, String parameters, String areas, int total) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Clob config = connection.createClob(), plan = connection.createClob();
            try (var statement = connection.prepareStatement("INSERT INTO "
                     + "relatorio_ambiental(rel_id,cam_id,rel_nome,rel_indicador,rel_parametros,rel_areas,"
                     + "rel_total) VALUES(?,?,?,?,?,?,?)")) {
                config.setString(1, parameters);
                plan.setString(1, areas);
                statement.setString(1, id);
                statement.setLong(2, layer);
                statement.setString(3, name);
                statement.setString(4, indicator);
                statement.setClob(5, config);
                statement.setClob(6, plan);
                statement.setInt(7, total);
                statement.executeUpdate();
                return null;
            } finally {
                config.free();
                plan.free();
            }
        });
    }
    public List<Item> items(String id, int page, int size) {
        return jdbc.query("""
            SELECT p.fei_id,p.nome,i.item_estado,i.item_erro,i.calc_id,h.calc_resultado
            FROM relatorio_ambiental r CROSS JOIN
              JSON_TABLE(r.rel_areas,'$[*]' COLUMNS(seq FOR ORDINALITY,fei_id NUMBER PATH '$.id',nome VARCHAR2(1000) PATH '$.name')) p
              LEFT JOIN relatorio_ambiental_item i ON i.rel_id=r.rel_id AND i.fei_id=p.fei_id
              LEFT JOIN calculo_ambiental h ON h.calc_id=i.calc_id
            WHERE r.rel_id=? ORDER BY p.seq OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """,
            (r, n)
                -> new Item(r.getLong(1), r.getString(2), r.getString(3), r.getString(4),
                    (Long) r.getObject(5, Long.class), r.getString(6)),
            id, page * size, size);
    }
    public Item item(String id, long feature) {
        var items =
            jdbc.query("SELECT i.fei_id,'' AS nome,i.item_estado,i.item_erro,i.calc_id,h.calc_resultado FROM "
                    + "relatorio_ambiental_item i LEFT JOIN calculo_ambiental h ON h.calc_id=i.calc_id "
                    + "WHERE i.rel_id=? AND i.fei_id=?",
                (r, n)
                    -> new Item(r.getLong(1), r.getString(2), r.getString(3), r.getString(4),
                        r.getObject(5, Long.class), r.getString(6)),
                id, feature);
        return items.isEmpty() ? null : items.getFirst();
    }
    public void complete(String id, long feature, long calculation) {
        jdbc.update("INSERT INTO relatorio_ambiental_item(rel_id,fei_id,calc_id,item_estado) "
                + "VALUES(?,?,?,'CONCLUIDO')",
            id, feature, calculation);
    }
    public void fail(String id, long feature, String error) {
        jdbc.update(
            "INSERT INTO relatorio_ambiental_item(rel_id,fei_id,item_estado,item_erro) VALUES(?,?,'ERRO',?)",
            id, feature, error);
    }
}
