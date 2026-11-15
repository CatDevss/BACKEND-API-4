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
}
