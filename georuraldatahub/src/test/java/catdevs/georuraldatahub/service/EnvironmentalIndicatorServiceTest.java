package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnvironmentalIndicatorServiceTest {
    private final EnvironmentalIndicatorRepository repository = mock(EnvironmentalIndicatorRepository.class);
    private final EnvironmentalIndicatorService service = new EnvironmentalIndicatorService(repository);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void returnsOracleResultWithoutRecalculatingOrRounding() throws Exception {
        var parameters = mapper.readTree("{\"vegetacao\":[2]}");
        when(repository.calculate(1, parameters.toString())).thenReturn("{\"ICV\":{\"percentual\":33.333333333},\"calculoId\":8}");
        var result = service.calculate(1, parameters);
        assertEquals(33.333333333, result.path("ICV").path("percentual").asDouble());
        assertEquals(8, result.path("calculoId").asInt());
        verify(repository).calculate(1, parameters.toString());
    }

    @Test void rejectsInvalidIdsAndBodyBeforeCallingOracle() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(0, mapper.createObjectNode()));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(1, mapper.createArrayNode()));
        assertThrows(IllegalArgumentException.class, () -> service.importGeoJson(1, -2));
        verifyNoInteractions(repository);
    }

    @Test void missingCoverageIsReportedAs422WithoutOracleStack() {
        when(repository.calculate(eq(1L), anyString())).thenThrow(new DataIntegrityViolationException("Oracle",
            new SQLException("ORA-20126: Informe camadas para app\nORA-06512: package", "99999", 20126)));
        var error = assertThrows(ResponseStatusException.class, () -> service.calculate(1, mapper.createObjectNode()));
        assertEquals(422, error.getStatusCode().value());
        assertEquals("Informe camadas para app", error.getReason());
    }

    @Test void rejectsMalformedOracleResult() {
        when(repository.calculate(eq(1L), anyString())).thenReturn("invalid");
        assertThrows(IllegalStateException.class, () -> service.calculate(1, mapper.createObjectNode()));
    }

    @Test void individualIndicatorsReturnOracleResultUnchanged() throws Exception {
        var parameters = mapper.readTree("{\"camadas\":[2,3]}");
        for (String name : new String[]{"icv", "irl", "iapp", "isap", "iae", "idesmat", "ifc"}) {
            when(repository.calculateIndicator(1, parameters.toString(), name))
                .thenReturn("{\"valorOracle\":12.3456789}");
            var result = service.calculateIndicator(1, parameters, name);
            assertEquals(12.3456789, result.path("valorOracle").asDouble());
            verify(repository).calculateIndicator(1, parameters.toString(), name);
        }
    }

    @Test void combinesLayersWithoutChangingSourceIds() {
        var expected = new EnvironmentalIndicatorRepository.ImportedLayer(50, 300);
        when(repository.combineLayers(21, "Focos", "[43,44]")).thenReturn(expected);
        assertEquals(expected, service.combineLayers(21, "Focos", java.util.List.of(43L, 44L)));
        verify(repository).combineLayers(21, "Focos", "[43,44]");
    }

    @Test void rejectsDuplicateOrMissingCompositionSources() {
        assertThrows(IllegalArgumentException.class,
            () -> service.combineLayers(21, "Focos", java.util.List.of(43L, 43L)));
        assertThrows(IllegalArgumentException.class,
            () -> service.combineLayers(21, "Focos", java.util.List.of(43L)));
        verifyNoInteractions(repository);
    }
}
