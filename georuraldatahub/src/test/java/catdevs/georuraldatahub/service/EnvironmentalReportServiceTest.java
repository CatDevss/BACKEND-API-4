package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository;
import catdevs.georuraldatahub.repository.EnvironmentalReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EnvironmentalReportServiceTest {
    private final EnvironmentalReportRepository reports = mock(EnvironmentalReportRepository.class);
    private final EnvironmentalIndicatorRepository calculations =
        mock(EnvironmentalIndicatorRepository.class);
    private final EnvironmentalIndicatorService indicators = new EnvironmentalIndicatorService(calculations);
    private final EnvironmentalReportService service =
        new EnvironmentalReportService(reports, calculations, indicators);
    private final ObjectMapper mapper = new ObjectMapper();
    private final String id = "00000000-0000-4000-8000-000000000001";

    private EnvironmentalReportRepository.Header header() {
        return new EnvironmentalReportRepository.Header(id, "Relatório de teste", "icv",
            "{\"vegetacao\":[5]}", "[{\"id\":2,\"name\":\"Área A\"}]", 1, "2026-01-01T00:00:00Z", 0, 0);
    }

    @Test
    void reusesSavedCalculationWithoutExecutingOracleAgain() {
        when(reports.available()).thenReturn(true);
        when(reports.get(id, true)).thenReturn(header());
        when(reports.item(id, 2))
            .thenReturn(new EnvironmentalReportRepository.Item(2, "Área A", "CONCLUIDO", null, 8L,
                "{\"ICV\":{\"areaHa\":0,\"situacao\":\"DADOS_PRESENTES\"}}"));
        var result = service.calculate(id, 2);
        assertEquals(8, result.path("calculoId").asInt());
        assertEquals(0, result.path("ICV").path("areaHa").asInt());
        verifyNoInteractions(calculations);
        verify(reports, never()).complete(anyString(), anyLong(), anyLong());
    }

    @Test
    void persistsOracleResultAndGroupLinkTogether() {
        when(reports.available()).thenReturn(true);
        when(reports.get(id, true)).thenReturn(header());
        when(calculations.calculateAndSave(eq(2L), eq("icv"), anyString()))
            .thenReturn("{\"calculoId\":9,\"ICV\":{\"areaHa\":12.3456789}}");
        var result = service.calculate(id, 2);
        assertEquals(12.3456789, result.path("ICV").path("areaHa").asDouble());
        verify(calculations).calculateAndSave(eq(2L), eq("icv"), contains("relatorioId"));
        verify(reports).complete(id, 2, 9);
    }

    @Test
    void refusesAreasOutsideReportBeforeCallingOracle() {
        when(reports.available()).thenReturn(true);
        when(reports.get(id, true)).thenReturn(header());
        assertThrows(IllegalArgumentException.class, () -> service.calculate(id, 3));
        verifyNoInteractions(calculations);
    }

    @Test
    void neverOverwritesCommittedCalculationWithFailure() {
        when(reports.available()).thenReturn(true);
        when(reports.get(id, true)).thenReturn(header());
        when(reports.item(id, 2))
            .thenReturn(new EnvironmentalReportRepository.Item(2, "A", "CONCLUIDO", null, 8L, "{}"));
        service.fail(id, 2, "Resposta perdida");
        verify(reports, never()).fail(anyString(), anyLong(), anyString());
    }

    @Test
    void refusesInvalidOrDuplicatePlanAndMissingMigration() {
        assertThrows(ResponseStatusException.class,
            () -> service.create(id, 1, null, "icv", mapper.createObjectNode(), List.of(2L)));
        when(reports.available()).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
            () -> service.create(id, 1, null, "icv", mapper.createObjectNode(), List.of(2L, 2L)));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(id, 1, null, null, mapper.createObjectNode(), List.of(2L)));
        verify(reports, never()).resolveAreas(anyLong(), anyString());
        verifyNoInteractions(calculations);
    }

    @Test
    void failedAreaIsImmutableAndCannotBeRecalculatedInSameReport() {
        when(reports.available()).thenReturn(true);
        when(reports.get(id, true)).thenReturn(header());
        when(reports.item(id, 2))
            .thenReturn(
                new EnvironmentalReportRepository.Item(2, "A", "ERRO", "Geometria inválida", null, null));
        assertThrows(ResponseStatusException.class, () -> service.calculate(id, 2));
        verifyNoInteractions(calculations);
    }
    @Test
    void excludesReportWithoutDeletingCalculations() {
        when(reports.available()).thenReturn(true);
        when(reports.deletionAvailable()).thenReturn(true);
        when(reports.canDelete(4)).thenReturn(true);
        when(reports.get(id, true)).thenReturn(header());
        service.delete(id, 4);
        verify(reports).get(id, true);
        verify(reports).delete(id, 4);
        verifyNoInteractions(calculations);
    }
    @Test
    void refusesExclusionWithoutMigrationOrAllowedUser() {
        when(reports.available()).thenReturn(true);
        assertEquals(503,
            assertThrows(ResponseStatusException.class, () -> service.delete(id, 4)).getStatusCode().value());
        when(reports.deletionAvailable()).thenReturn(true);
        assertEquals(403,
            assertThrows(ResponseStatusException.class, () -> service.delete(id, 4)).getStatusCode().value());
        verify(reports, never()).delete(anyString(), anyLong());
    }
    @Test
    void excludedReportsCannotBeRecreatedOrReopened() {
        when(reports.available()).thenReturn(true);
        when(reports.isDeleted(id)).thenReturn(true);
        assertEquals(410,
            assertThrows(ResponseStatusException.class,
                () -> service.create(id, 1, null, "icv", mapper.createObjectNode(), List.of(2L)))
                .getStatusCode()
                .value());
        when(reports.get(id, true)).thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        assertEquals(404,
            assertThrows(ResponseStatusException.class, () -> service.calculate(id, 2))
                .getStatusCode()
                .value());
        verifyNoInteractions(calculations);
    }
    @Test
    void excludesIndividualCalculationWithoutChangingSavedResult() {
        when(calculations.calculationDeletionAvailable()).thenReturn(true);
        when(reports.canDelete(4)).thenReturn(true);
        service.deleteCalculation(21, 4);
        verify(calculations).lockCalculation(21);
        verify(calculations).historyDetail(21);
        verify(calculations).excludeCalculation(21, 4);
        verify(calculations, never()).calculateAndSave(anyLong(), anyString(), anyString());
    }
    @Test
    void refusesExclusionOfUnknownOrAlreadyExcludedCalculation() {
        when(calculations.calculationDeletionAvailable()).thenReturn(true);
        when(reports.canDelete(4)).thenReturn(true);
        when(calculations.historyDetail(21))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        assertEquals(404,
            assertThrows(ResponseStatusException.class, () -> service.deleteCalculation(21, 4))
                .getStatusCode()
                .value());
        verify(calculations, never()).excludeCalculation(anyLong(), anyLong());
    }
}
