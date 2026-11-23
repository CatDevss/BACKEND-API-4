package catdevs.georuraldatahub.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consulta dos imóveis do CAR já importados (BE-15).
 *
 * Não existe tabela de imóveis: um imóvel é uma feição poligonal (feicao) de uma
 * camada cuja fonte é o CAR e cujo conjunto é o de imóveis rurais. Código CAR e
 * município ficam em fei_atributos (JSON), cujos nomes de campo variam conforme o
 * arquivo importado; por isso são configuráveis (car.campos.*).
 */
@Repository
public class CarPropertyRepository {
    private static final String FIELD_NAME = "[\\p{L}\\p{N}_]{1,100}";

    // Mesma cadeia de junções usada em EnvironmentalIndicatorRepository.LAYER_FROM.
    private static final String FROM = " FROM feicao f JOIN camada c ON c.cam_id=f.cam_id "
            + "JOIN conjunto co ON co.con_id=c.con_id JOIN versao v ON v.ver_id=c.ver_id "
            + "JOIN arquivo a ON a.arq_id=v.arq_id JOIN ingestao i ON i.ing_id=a.ing_id "
            + "JOIN dicionario d ON d.dic_id=i.dic_id JOIN fonte s ON s.fon_id=d.fon_id ";

    // Geometria em WGS84 (4326), o sistema esperado por mapas web (Leaflet, MapLibre...).
    private static final String GEOJSON = "SDO_UTIL.TO_GEOJSON(CASE WHEN c.cam_srid=4326 "
            + "THEN f.fei_geometria ELSE SDO_CS.TRANSFORM(f.fei_geometria,4326) END)";

    /** Filtros opcionais; null significa "não filtrar". */
    public record Filter(String carCode, String municipality, Long layerId, Long datasetId) {
        public boolean isEmpty() {
            return carCode == null && municipality == null && layerId == null && datasetId == null;
        }
    }

    public record Property(long id, String carCode, String municipality, long layerId, String layer,
            long datasetId, String dataset, long versionId, long fileId, String file, int srid) {
    }

    public record PropertyDetail(Property property, String attributes) {
    }

    public record PropertyGeometry(Property property, String geometry) {
    }

    private record Where(String sql, List<Object> args) {
    }

    private final JdbcTemplate jdbc;
    private final String source;
    private final String dataset;
    private final String codeExpression;
    private final String municipalityExpression;

    public CarPropertyRepository(JdbcTemplate jdbc,
            @Value("${car.fonte:CAR}") String source,
            @Value("${car.conjunto:}") String dataset,
            @Value("${car.campos.codigo:COD_IMOVEL,cod_imovel}") String[] codeFields,
            @Value("${car.campos.municipio:NOM_MUNICI,nom_munici,MUNICIPIO,municipio}") String[] municipalityFields) {
        this.jdbc = jdbc;
        this.source = source;
        this.dataset = dataset;
        this.codeExpression = textExpression(codeFields);
        this.municipalityExpression = textExpression(municipalityFields);
    }

    /**
     * Monta a expressão SQL que lê o primeiro campo preenchido do JSON. O nome do
     * campo não pode ser parâmetro de bind, então é validado antes de entrar no SQL.
     * COALESCE exige 2+ argumentos no Oracle, por isso o caso de um campo só.
     */
    static String textExpression(String[] fields) {
        var parts = Stream.of(fields).map(String::trim).filter(f -> !f.isEmpty()).map(f -> {
            if (!f.matches(FIELD_NAME))
                throw new IllegalArgumentException("Nome de campo inválido em car.campos: " + f);
            return "JSON_VALUE(f.fei_atributos,'$.\"" + f + "\"' RETURNING VARCHAR2(4000) NULL ON ERROR)";
        }).toList();
        if (parts.isEmpty())
            throw new IllegalArgumentException("Informe ao menos um campo em car.campos.");
        return parts.size() == 1 ? parts.getFirst() : "COALESCE(" + String.join(",", parts) + ")";
    }

    private String columns() {
        return "SELECT f.fei_id," + codeExpression + "," + municipalityExpression
                + ",c.cam_id,c.cam_nome,co.con_id,co.con_nome,v.ver_id,a.arq_id,a.arq_nome,c.cam_srid";
    }

    private Where where(Filter filter) {
        var args = new ArrayList<Object>();
        var sql = new StringBuilder(" WHERE v.ver_status IN ('TRATADA','PUBLICADA') "
                + "AND MOD(f.fei_geometria.sdo_gtype,100) IN (3,7) AND UPPER(s.fon_nome)=UPPER(?)");
        args.add(source);
        if (filter.datasetId() != null) {
            sql.append(" AND co.con_id=?");
            args.add(filter.datasetId());
        } else if (dataset != null && !dataset.isBlank()) {
            // Opcional: sem car.conjunto, vale qualquer conjunto da fonte CAR.
            sql.append(" AND UPPER(co.con_nome)=UPPER(?)");
            args.add(dataset);
        }
        if (filter.layerId() != null) {
            sql.append(" AND c.cam_id=?");
            args.add(filter.layerId());
        }
        if (filter.carCode() != null) {
            sql.append(" AND UPPER(TRIM(").append(codeExpression).append("))=UPPER(TRIM(?))");
            args.add(filter.carCode());
        }
        if (filter.municipality() != null) {
            // BINARY_AI ignora maiúsculas e acentos: "sao jose" encontra "São José".
            sql.append(" AND NLSSORT(").append(municipalityExpression)
                    .append(",'NLS_SORT=BINARY_AI')=NLSSORT(?,'NLS_SORT=BINARY_AI')");
            args.add(filter.municipality());
        }
        return new Where(sql.toString(), args);
    }

    private Property property(ResultSet r, int n) throws SQLException {
        return new Property(r.getLong(1), r.getString(2), r.getString(3), r.getLong(4), r.getString(5),
                r.getLong(6), r.getString(7), r.getLong(8), r.getLong(9), r.getString(10), r.getInt(11));
    }

    public EnvironmentalIndicatorRepository.Page<Property> list(Filter filter, int page, int size) {
        var where = where(filter);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + where.sql(), Long.class,
                where.args().toArray());
        var args = new ArrayList<>(where.args());
        args.add(page * size);
        args.add(size);
        var items = jdbc.query(columns() + FROM + where.sql()
                + " ORDER BY f.fei_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", this::property, args.toArray());
        return new EnvironmentalIndicatorRepository.Page<>(items, total == null ? 0 : total, page, size);
    }

    public Optional<PropertyDetail> findDetail(long id) {
        var where = where(new Filter(null, null, null, null));
        var args = new ArrayList<>(where.args());
        args.add(id);
        return jdbc.query(columns() + ",f.fei_atributos" + FROM + where.sql() + " AND f.fei_id=?",
                (r, n) -> new PropertyDetail(property(r, n), r.getString(12)), args.toArray()).stream().findFirst();
    }

    public Optional<PropertyGeometry> findGeometry(long id) {
        var where = where(new Filter(null, null, null, null));
        var args = new ArrayList<>(where.args());
        args.add(id);
        return jdbc.query(columns() + "," + GEOJSON + FROM + where.sql() + " AND f.fei_id=?",
                (r, n) -> new PropertyGeometry(property(r, n), r.getString(12)), args.toArray()).stream()
                .findFirst();
    }

    public List<PropertyGeometry> geometries(Filter filter, int limit) {
        var where = where(filter);
        var args = new ArrayList<>(where.args());
        args.add(limit);
        return jdbc.query(columns() + "," + GEOJSON + FROM + where.sql()
                + " ORDER BY f.fei_id FETCH FIRST ? ROWS ONLY",
                (r, n) -> new PropertyGeometry(property(r, n), r.getString(12)), args.toArray());
    }
}
