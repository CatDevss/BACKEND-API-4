package catdevs.georuraldatahub.repository;

import java.io.StringReader;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acesso às feições (tabela feicao) por SQL nativo. A geometria entra e sai
 * como GeoJSON, convertida pelo Oracle Spatial (SDO_UTIL.FROM_GEOJSON /
 * TO_GEOJSON), sem mapear SDO_GEOMETRY no JPA.
 *
 * Os métodos participam da transação do chamador (@Transactional no serviço).
 */
@Repository
public class FeatureRepository {

    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
            INSERT INTO feicao (cam_id, fei_id_origem, fei_geometria, fei_atributos)
            VALUES (?, ?, SDO_UTIL.FROM_GEOJSON(?, NULL, ?), ?)
            """;

    private final JdbcTemplate jdbc;

    public FeatureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Feição a ser gravada.
     *
     * @param originId       identificador da feição no arquivo de origem
     * @param geometryGeoJson geometria em GeoJSON (objeto "geometry")
     * @param attributesJson propriedades da feição em JSON (pode ser null)
     */
    public record NewFeature(String originId, String geometryGeoJson, String attributesJson) {
    }

    /** Insere as feições em lote e devolve quantas foram gravadas. */
    public int insertBatch(Long layerId, int srid, List<NewFeature> features) {
        if (features.isEmpty()) {
            return 0;
        }
        jdbc.batchUpdate(INSERT_SQL, features, BATCH_SIZE, (ps, feature) -> {
            ps.setLong(1, layerId);
            ps.setString(2, feature.originId());
            // CLOB: geometrias de municípios passam de 4000 caracteres
            ps.setCharacterStream(3, new StringReader(feature.geometryGeoJson()),
                    feature.geometryGeoJson().length());
            ps.setInt(4, srid);
            if (feature.attributesJson() == null) {
                ps.setNull(5, Types.CLOB);
            } else {
                ps.setCharacterStream(5, new StringReader(feature.attributesJson()),
                        feature.attributesJson().length());
            }
        });
        return features.size();
    }

    /** Geometria da feição em GeoJSON, convertida pelo próprio banco. */
    public Optional<String> findGeometryAsGeoJson(Long featureId) {
        return jdbc.query(
                "SELECT SDO_UTIL.TO_GEOJSON(fei_geometria) FROM feicao WHERE fei_id = ?",
                (rs, rowNum) -> rs.getString(1),
                featureId).stream().findFirst();
    }

    public long countByLayerId(Long layerId) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM feicao WHERE cam_id = ?", Long.class, layerId);
        return total == null ? 0 : total;
    }

    public List<Long> findIdsByLayerId(Long layerId) {
        return jdbc.queryForList(
                "SELECT fei_id FROM feicao WHERE cam_id = ? ORDER BY fei_id",
                Long.class, layerId);
    }
}