
package catdevs.georuraldatahub.service;

import java.io.StringReader;
import java.sql.Clob;
import java.sql.Types;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.dto.IndicatorCalculationRequestDTO;
import catdevs.georuraldatahub.dto.IndicatorCalculationResponseDTO;
import catdevs.georuraldatahub.repository.EnvironmentalCalculationRepository;

@Service
public class EnvironmentalIndicatorCalculationService {

    private static final String CALCULATE_CALL =
            "{ call PKG_INDICADORES_AMBIENTAIS.CALCULAR(?, ?, ?) }";

    private final JdbcTemplate jdbcTemplate;
    private final EnvironmentalCalculationRepository calculationRepository;
    private final ObjectMapper objectMapper;
    private final IndicatorDatasetAccessService accessService;

    public EnvironmentalIndicatorCalculationService(
            JdbcTemplate jdbcTemplate,
            EnvironmentalCalculationRepository calculationRepository,
            ObjectMapper objectMapper,
            IndicatorDatasetAccessService accessService) {

        this.jdbcTemplate = jdbcTemplate;
        this.calculationRepository = calculationRepository;
        this.objectMapper = objectMapper;
        this.accessService = accessService;
    }

    /**
     * Solicita ao Oracle o cálculo dos indicadores
     * A própria procedure grava a execução em CALCULO_AMBIENTAL, por isso, este serviço não insere uma segunda cópia do resultado
     */
    @Transactional
    public IndicatorCalculationResponseDTO calculate(
            IndicatorCalculationRequestDTO request) {

        Long featureId = request.featureId();

        accessService.requireFeatureAccess(featureId);

        String configJson = serializeConfig(request.config());

        String resultJson = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> {

                    try (var statement =
                            connection.prepareCall(CALCULATE_CALL)) {

                        statement.setLong(1, featureId);
                        statement.setClob(2, new StringReader(configJson));
                        statement.registerOutParameter(3, Types.CLOB);

                        statement.execute();

                        Clob result = statement.getClob(3);

                        if (result == null) {
                            return null;
                        }

                        return result.getSubString(
                                1,
                                Math.toIntExact(result.length()));
                    }
                });

        if (resultJson == null || resultJson.isBlank()) {
            throw new IllegalStateException(
                    "A procedure do Oracle não retornou conteúdo em P_RESULTADO.");
        }

        // CALCULAR grava a execução no banco e inclui calculoId no JSON.
        JsonNode resultNode =
                parseJson(resultJson, "resultado retornado pelo Oracle");

        JsonNode idNode = resultNode.path("calculoId");

        if (idNode.isMissingNode()
                || idNode.isNull()
                || !idNode.canConvertToLong()) {

            throw new IllegalStateException(
                    "O JSON retornado pelo Oracle não contém um calculoId válido.");
        }

        IndicatorCalculationResponseDTO saved = calculationRepository.findById(idNode.asLong())
                .orElseThrow(() -> new IllegalStateException(
                        "O Oracle retornou o cálculo " + idNode.asLong()
                                + ", mas a execução não foi encontrada em CALCULO_AMBIENTAL."));
        accessService.requireFeatureAccess(saved.featureId());
        return saved;
    }

    @Transactional(readOnly = true)
    public IndicatorCalculationResponseDTO findById(Long calculationId) {

        IndicatorCalculationResponseDTO result = calculationRepository.findById(calculationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Execução de cálculo não encontrada."));
        accessService.requireFeatureAccess(result.featureId());
        return result;
    }

    @Transactional(readOnly = true)
    public java.util.List<IndicatorCalculationResponseDTO> findByFeatureId(
            Long featureId, int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Use page >= 0 e size entre 1 e 100.");
        }
        accessService.requireFeatureAccess(featureId);
        return calculationRepository.findByFeatureId(featureId, page, size);
    }

    private String serializeConfig(JsonNode config) {

        try {
            return objectMapper.writeValueAsString(
                    config == null || config.isNull()
                            ? objectMapper.createObjectNode()
                            : config);

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não foi possível converter a configuração para JSON.",
                    exception);
        }
    }

    private JsonNode parseJson(String value, String description) {

        try {
            return objectMapper.readTree(value);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "O " + description + " não está em formato JSON válido.",
                    exception);
        }
    }
}