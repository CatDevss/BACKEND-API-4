package catdevs.georuraldatahub.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import catdevs.georuraldatahub.dto.IndicatorCalculationResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class EnvironmentalCalculationRepository {

    private static final String SELECT_COLUMNS = """
            SELECT calc_id, fei_imovel_id, calc_data, calc_regra,
                   calc_parametros, calc_resultado
              FROM calculo_ambiental
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EnvironmentalCalculationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<IndicatorCalculationResponseDTO> findById(Long calculationId) {
        List<IndicatorCalculationResponseDTO> rows = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE calc_id = ?",
                rowMapper(), calculationId);
        return rows.stream().findFirst();
    }

    public List<IndicatorCalculationResponseDTO> findByFeatureId(Long featureId, int page, int size) {
        int offset = Math.multiplyExact(page, size);
        return jdbcTemplate.query(
                SELECT_COLUMNS
                        + " WHERE fei_imovel_id = ? ORDER BY calc_data DESC, calc_id DESC"
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                rowMapper(), featureId, offset, size);
    }

    private RowMapper<IndicatorCalculationResponseDTO> rowMapper() {
        return (rs, rowNum) -> new IndicatorCalculationResponseDTO(
                rs.getLong("calc_id"),
                rs.getLong("fei_imovel_id"),
                toLocalDateTime(rs),
                rs.getString("calc_regra"),
                readJson(rs.getString("calc_parametros"), "calc_parametros"),
                readJson(rs.getString("calc_resultado"), "calc_resultado"));
    }

    private LocalDateTime toLocalDateTime(ResultSet rs) throws SQLException {
        var timestamp = rs.getTimestamp("calc_data");
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private JsonNode readJson(String value, String column) {
        if (value == null || value.isBlank()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "A coluna " + column + " contém conteúdo que não é JSON válido.", exception);
        }
    }
}