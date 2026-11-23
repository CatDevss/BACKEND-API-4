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

    public record Page<T>(java.util.List<T> items, long total, int page, int size) {
    }

    public record LayerOption(long id, String name, long datasetId, String dataset,
            long versionId, String file, String source, String geometry, long features, long polygons,
            int srid, boolean composed) {
    }

    public record FeatureOption(long id, String name, String attributes) {
    }

    public record VersionOption(long id, int number, long fileId, String file, String source,
            String format, long importedLayers) {
    }

    private static final String LAYER_FROM = " FROM camada c JOIN conjunto co ON co.con_id=c.con_id "
            + "JOIN versao v ON v.ver_id=c.ver_id JOIN arquivo a ON a.arq_id=v.arq_id "
            + "JOIN ingestao i ON i.ing_id=a.ing_id JOIN dicionario d ON d.dic_id=i.dic_id "
            + "JOIN fonte s ON s.fon_id=d.fon_id ";
    private static final String GEOMETRY = "(SELECT CASE WHEN MIN(MOD(f.fei_geometria.sdo_gtype,100)) "
            + "<> MAX(MOD(f.fei_geometria.sdo_gtype,100)) THEN 'MIXED' ELSE "
            + "CASE MIN(MOD(f.fei_geometria.sdo_gtype,100)) WHEN 1 THEN 'POINT' WHEN 2 THEN 'LINESTRING' "
            + "WHEN 3 THEN 'POLYGON' WHEN 5 THEN 'MULTIPOINT' WHEN 6 THEN 'MULTILINESTRING' "
            + "WHEN 7 THEN 'MULTIPOLYGON' ELSE 'MIXED' END END FROM feicao f WHERE f.cam_id=c.cam_id)";
    private static final String LAYER_SELECT = "SELECT c.cam_id,c.cam_nome,c.con_id,co.con_nome,c.ver_id,a.arq_nome,"
            + "CASE WHEN EXISTS (SELECT 1 FROM camada_origem o WHERE o.cam_id=c.cam_id) "
            + "THEN 'Composta (origens nos detalhes)' ELSE s.fon_nome END," + GEOMETRY + ","
            + "(SELECT COUNT(*) FROM feicao f WHERE f.cam_id=c.cam_id),"
            + "(SELECT COUNT(*) FROM feicao f WHERE f.cam_id=c.cam_id AND MOD(f.fei_geometria.sdo_gtype,100) IN (3,7)),"
            + "c.cam_srid,(SELECT COUNT(*) FROM camada_origem o WHERE o.cam_id=c.cam_id)";

    private LayerOption layerRow(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new LayerOption(r.getLong(1), r.getString(2), r.getLong(3), r.getString(4), r.getLong(5),
                r.getString(6), r.getString(7), r.getString(8), r.getLong(9), r.getLong(10), r.getInt(11),
                r.getLong(12) > 0);
    }

    public Page<LayerOption> layers(String search, int page, int size) {
        return layers(search, page, size, null, "", "", false);
    }

    public Page<LayerOption> layers(String search, int page, int size, Long dataset, String type, String source,
            boolean polygon) {
        String where = " WHERE v.ver_status IN ('TRATADA','PUBLICADA') AND EXISTS (SELECT 1 FROM feicao f WHERE f.cam_id=c.cam_id) "
                + "AND (LOWER(c.cam_nome) LIKE ? OR LOWER(co.con_nome) LIKE ?)";
        var args = new java.util.ArrayList<Object>();
        String term = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
        args.add(term);
        args.add(term);
        if (dataset != null) {
            where += " AND c.con_id=?";
            args.add(dataset);
        }
        if (!type.isBlank()) {
            where += " AND " + GEOMETRY + "=?";
            args.add(type);
        }
        if (polygon)
            where += " AND EXISTS (SELECT 1 FROM feicao f WHERE f.cam_id=c.cam_id AND MOD(f.fei_geometria.sdo_gtype,100) IN (3,7))";
        if (!source.isBlank()) {
            where += " AND (LOWER(s.fon_nome) LIKE ? OR EXISTS (SELECT 1 FROM camada_origem o "
                    + "JOIN camada oc ON oc.cam_id=o.cam_origem_id JOIN versao ov ON ov.ver_id=oc.ver_id "
                    + "JOIN arquivo oa ON oa.arq_id=ov.arq_id JOIN ingestao oi ON oi.ing_id=oa.ing_id "
                    + "JOIN dicionario od ON od.dic_id=oi.dic_id JOIN fonte os ON os.fon_id=od.fon_id "
                    + "WHERE o.cam_id=c.cam_id AND LOWER(os.fon_nome) LIKE ?))";
            String filter = "%" + source.toLowerCase(java.util.Locale.ROOT) + "%";
            args.add(filter);
            args.add(filter);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + LAYER_FROM + where, Long.class, args.toArray());
        args.add(page * size);
        args.add(size);
        var items = jdbc.query(
                LAYER_SELECT + LAYER_FROM + where
                        + " ORDER BY c.cam_nome,c.cam_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                this::layerRow, args.toArray());
        return new Page<>(items, total, page, size);
    }

    public LayerOption layer(long id) {
        return jdbc.queryForObject(
                LAYER_SELECT + LAYER_FROM + " WHERE c.cam_id=? AND v.ver_status IN ('TRATADA','PUBLICADA')",
                this::layerRow, id);
    }

    public java.util.List<LayerOption> origins(long id) {
        return jdbc.query(LAYER_SELECT + LAYER_FROM
                + " WHERE c.cam_id IN (SELECT cam_origem_id FROM camada_origem WHERE cam_id=?) ORDER BY c.cam_id",
                this::layerRow, id);
    }

    public Page<VersionOption> versions(long dataset, String search, int page, int size) {
        String from = " FROM conjunto_arquivo ca JOIN arquivo a ON a.arq_id=ca.arq_id JOIN versao v ON v.arq_id=a.arq_id "
                + "JOIN ingestao i ON i.ing_id=a.ing_id JOIN dicionario d ON d.dic_id=i.dic_id JOIN fonte s ON s.fon_id=d.fon_id "
                + "WHERE ca.con_id=? AND v.ver_status='TRATADA' AND v.ver_localizacao LIKE 'tratada/%' AND LOWER(a.arq_nome) LIKE ? "
                + "AND (LOWER(v.ver_localizacao) LIKE '%.geojson' OR LOWER(v.ver_localizacao) LIKE '%.json' OR LOWER(v.ver_localizacao) LIKE '%.csv') "
                + "AND NOT EXISTS (SELECT 1 FROM camada c WHERE c.ver_id=v.ver_id)";
        String term = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, dataset, term);
        var items = jdbc.query("SELECT v.ver_id,v.ver_numero,a.arq_id,a.arq_nome,s.fon_nome,"
                + "CASE WHEN LOWER(v.ver_localizacao) LIKE '%.csv' THEN 'CSV' ELSE 'GeoJSON' END,"
                + "(SELECT COUNT(*) FROM camada c JOIN versao cv ON cv.ver_id=c.ver_id WHERE c.con_id=ca.con_id AND cv.arq_id=a.arq_id AND cv.ver_localizacao=v.ver_localizacao)"
                + from + " ORDER BY a.arq_nome,v.ver_numero DESC,v.ver_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (r, n) -> new VersionOption(r.getLong(1), r.getInt(2), r.getLong(3), r.getString(4), r.getString(5),
                        r.getString(6), r.getLong(7)),
                dataset, term, page * size, size);
        return new Page<>(items, total, page, size);
    }

    public String map(long feature, String indicator, String parameters) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            Clob input = connection.createClob();
            try (var call = connection.prepareCall("{? = call fn_ambiental_mapa(?,?,?)}")) {
                input.setString(1, parameters);
                call.setQueryTimeout(300);
                call.registerOutParameter(1, Types.CLOB);
                call.setLong(2, feature);
                call.setString(3, indicator);
                call.setClob(4, input);
                call.execute();
                Clob output = call.getClob(1);
                try {
                    if (output.length() > 5_000_000)
                        throw new IllegalArgumentException("Mapa excede o limite de tamanho.");
                    return output.getSubString(1, Math.toIntExact(output.length()));
                } finally {
                    output.free();
                }
            } finally {
                input.free();
            }
        });
    }

    public Page<FeatureOption> features(long layer, String search, String field, int page, int size) {
        String name;
        if (field != null && !field.isBlank()) {
            if (!field.matches("[\\p{L}\\p{N}_]{1,100}"))
                throw new IllegalArgumentException("Campo de nome invalido.");
            name = "COALESCE(JSON_VALUE(f.fei_atributos, '$.\"" + field
                    + "\"' RETURNING VARCHAR2(4000) NULL ON ERROR),f.fei_id_origem,TO_CHAR(f.fei_id))";
        } else {
            name = featureNameSql("f.fei_id");
        }
        String from = " FROM feicao f JOIN camada c ON c.cam_id=f.cam_id JOIN versao v ON v.ver_id=c.ver_id "
                + "WHERE f.cam_id=? AND v.ver_status IN ('TRATADA','PUBLICADA') "
                + "AND MOD(f.fei_geometria.sdo_gtype,100) IN (3,7) AND LOWER(" + name + ") LIKE ?";
        String term = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, layer, term);
        var items = jdbc.query("SELECT f.fei_id," + name + ",f.fei_atributos" + from
                + " ORDER BY 2,f.fei_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (r, n) -> new FeatureOption(r.getLong(1), r.getString(2), r.getString(3)), layer, term, page * size,
                size);
        return new Page<>(items, total, page, size);
    }

    public java.util.Map<String, Object> sourceMetadata(long layer) {
        var rows = jdbc.queryForList("SELECT c.cam_nome AS NOME, a.arq_nome AS ARQUIVO, s.fon_nome AS FONTE "
                + "FROM camada c JOIN versao v ON v.ver_id=c.ver_id JOIN arquivo a ON a.arq_id=v.arq_id "
                + "JOIN ingestao i ON i.ing_id=a.ing_id JOIN dicionario d ON d.dic_id=i.dic_id "
                + "JOIN fonte s ON s.fon_id=d.fon_id WHERE c.cam_id=?", layer);
        return rows.isEmpty() ? java.util.Map.of() : rows.getFirst();
    }

    public record ImportedLayer(long camadaId, int quantidade) {
    }

    public ImportedLayer importGeoJson(long datasetId, long versionId) {
        return importGeoJson(datasetId, versionId, false);
    }

    public ImportedLayer importGeoJson(long datasetId, long versionId, boolean repair) {
        return jdbc.execute((ConnectionCallback<ImportedLayer>) connection -> {
            String command = repair ? "{call pkg_carga_ambiental.importar_geojson(?,?,?,?,?)}"
                    : "{call pkg_carga_ambiental.importar_geojson(?,?,?,?)}";
            try (var call = connection.prepareCall(command)) {
                call.setQueryTimeout(300);
                call.setLong(1, datasetId);
                call.setLong(2, versionId);
                call.registerOutParameter(3, Types.NUMERIC);
                call.registerOutParameter(4, Types.NUMERIC);
                if (repair)
                    call.setInt(5, 1);
                call.execute();
                return new ImportedLayer(call.getLong(3), call.getInt(4));
            }
        });
    }

    public ImportedLayer combineLayers(long datasetId, String name, String layersJson) {
        return jdbc.execute((ConnectionCallback<ImportedLayer>) connection -> {
            Clob input = connection.createClob();
            try (var call = connection.prepareCall(
                    "{call pkg_carga_ambiental.combinar_camadas(?,?,?,?,?)}")) {
                input.setString(1, layersJson);
                call.setQueryTimeout(300);
                call.setLong(1, datasetId);
                call.setString(2, name);
                call.setClob(3, input);
                call.registerOutParameter(4, Types.NUMERIC);
                call.registerOutParameter(5, Types.NUMERIC);
                call.execute();
                return new ImportedLayer(call.getLong(4), call.getInt(5));
            } finally {
                input.free();
            }
        });
    }

    public String calculateIcv(long propertyId, String parameters) {
        return calculateIndicator(propertyId, parameters, "icv");
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

    public record HistoryEntry(long id, long featureId, String name, String indicator, String calculatedAt,
            String rule) {
    }

    public record HistoryDetail(HistoryEntry calculation, String parameters, String result) {
    }

    private static final String HISTORY_NAME = featureNameSql("h.fei_imovel_id");

    static String featureNameSql(String fallbackId) {
        // O nome da terra indigena tem prioridade sobre o municipio onde ela fica.
        return "COALESCE(" + java.util.stream.Stream.of(
                "terrai_nome", "TERRAI_NOME", "ti_nome", "TI_NOME", "nome_ti", "NOME_TI",
                "nome_uc", "NOME_UC", "NM_UC", "NM_MUN", "nm_mun", "NM_MUNICIP",
                "nome", "NOME", "name", "NAME", "nom_imovel", "NOM_IMOVEL", "NM_IMOVEL",
                "NM_BIOMA", "nm_bioma", "Bioma", "bioma")
                .map(key -> "TRIM(JSON_VALUE(f.fei_atributos, '$." + key
                        + "' RETURNING VARCHAR2(1000) NULL ON ERROR))")
                .collect(java.util.stream.Collectors.joining(","))
                + ",'Area sem nome ('||" + fallbackId + "||')')";
    }

    private static final String HISTORY_INDICATOR = "CASE WHEN JSON_EXISTS(h.calc_resultado,'$.ICV') AND JSON_EXISTS(h.calc_resultado,'$.IFC') THEN 'todos' "
            + "WHEN JSON_EXISTS(h.calc_resultado,'$.ICV') THEN 'icv' WHEN JSON_EXISTS(h.calc_resultado,'$.IRL') THEN 'irl' "
            + "WHEN JSON_EXISTS(h.calc_resultado,'$.IAPP') THEN 'iapp' WHEN JSON_EXISTS(h.calc_resultado,'$.ISAP') THEN 'isap' "
            + "WHEN JSON_EXISTS(h.calc_resultado,'$.IAE') THEN 'iae' WHEN JSON_EXISTS(h.calc_resultado,'$.IFC') THEN 'ifc' "
            + "WHEN JSON_EXISTS(h.calc_resultado,'$.IDesmat2008') THEN 'idesmat' ELSE 'desconhecido' END";
    private static final String HISTORY_FROM = " FROM calculo_ambiental h LEFT JOIN feicao f ON f.fei_id=h.fei_imovel_id ";
    private static final String HISTORY_SELECT = "SELECT h.calc_id,h.fei_imovel_id," + HISTORY_NAME + ","
            + HISTORY_INDICATOR
            + ",TO_CHAR(h.calc_data,'YYYY-MM-DD HH24:MI:SS'),h.calc_regra";

    private HistoryEntry historyRow(java.sql.ResultSet r) throws java.sql.SQLException {
        return new HistoryEntry(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5),
                r.getString(6));
    }

    private boolean calculationExclusionTableExists() {
        return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name='CALCULO_AMBIENTAL_EXCLUSAO'", Integer.class));
    }
    public boolean calculationDeletionAvailable() {
        return calculationExclusionTableExists() && Integer.valueOf(3).equals(jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name AND o.object_type='TRIGGER' WHERE t.status='ENABLED' AND o.status='VALID' AND t.trigger_name IN ('TRG_CALC_EXCLUSAO_VALIDAR','TRG_CALC_EXCLUSAO_IMUTAVEL','TRG_AUD_CALC_EXCLUSAO')",Integer.class));
    }
    private String visibleCalculation() {
        return calculationExclusionTableExists() ? " AND NOT EXISTS (SELECT 1 FROM calculo_ambiental_exclusao x WHERE x.calc_id=h.calc_id)" : "";
    }
    public void lockCalculation(long id) {
        jdbc.queryForObject("SELECT calc_id FROM calculo_ambiental WHERE calc_id=? FOR UPDATE",Long.class,id);
    }
    public void excludeCalculation(long id,long user) {
        jdbc.update("INSERT INTO calculo_ambiental_exclusao(calc_id,usr_id) VALUES(?,?)",id,user);
    }
    public Page<HistoryEntry> history(String search, String indicator, int page, int size) {
        return history(search, indicator, page, size, EnvironmentalHistoryFilter.empty());
    }

    public Page<HistoryEntry> history(String search, String indicator, int page, int size,
            EnvironmentalHistoryFilter filters) {
        String where = " WHERE LOWER(" + HISTORY_NAME + ") LIKE ?" + visibleCalculation();
        var args = new java.util.ArrayList<Object>();
        args.add("%" + search.toLowerCase(java.util.Locale.ROOT) + "%");
        if (!indicator.isBlank()) {
            var groups = java.util.Map.of("icv", "ICV", "irl", "IRL", "iapp", "IAPP", "isap", "ISAP",
                    "iae", "IAE", "ifc", "IFC", "idesmat", "IDesmat2008");
            String group = groups.get(indicator);
            if (group != null) {
                // Inclui o indicador salvo sozinho ou junto dos demais.
                where += " AND JSON_EXISTS(h.calc_resultado,'$." + group + "')";
            } else {
                where += " AND " + HISTORY_INDICATOR + "=?";
                args.add(indicator);
            }
        }
        where += filters.calculations(args);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + HISTORY_FROM + where, Long.class, args.toArray());
        args.add(page * size);
        args.add(size);
        var rows = jdbc.query(
                HISTORY_SELECT + HISTORY_FROM + where
                        + " ORDER BY h.calc_data DESC,h.calc_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (r, n) -> historyRow(r), args.toArray());
        return new Page<>(rows, total, page, size);
    }

    public HistoryDetail historyDetail(long id) {
        return jdbc.queryForObject(
                HISTORY_SELECT + ",h.calc_parametros,h.calc_resultado" + HISTORY_FROM + " WHERE h.calc_id=?" + visibleCalculation(),
                (r, n) -> new HistoryDetail(historyRow(r), r.getString(7), r.getString(8)), id);
    }

    public String calculateAndSave(long featureId, String indicator, String parameters) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            Clob input = connection.createClob();
            try (var call = connection.prepareCall("{call pr_calcular_indicador(?,?,?,?)}")) {
                input.setString(1, parameters);
                call.setQueryTimeout(300);
                call.setLong(1, featureId);
                call.setString(2, indicator);
                call.setClob(3, input);
                call.registerOutParameter(4, Types.CLOB);
                call.execute();
                Clob output = call.getClob(4);
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
