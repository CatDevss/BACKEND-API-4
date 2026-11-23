package catdevs.georuraldatahub.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentalHistoryFilterTest {
    @Test void filtraPeriodoDosDadosComLimitesInclusivosECamada() {
        var filters = new EnvironmentalHistoryFilter(8L, LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-20"));
        var args = new ArrayList<Object>();
        String sql = filters.calculations(args);
        assertTrue(sql.contains("f.cam_id=?"));
        assertTrue(sql.contains("h.calc_parametros,'$.fim'"));
        assertTrue(sql.contains("h.calc_parametros,'$.inicio'"));
        assertTrue(sql.contains(">=?"));
        assertTrue(sql.contains("<=?"));
        assertTrue(sql.contains("IS NOT NULL"));
        assertTrue(sql.contains("DEFAULT NULL ON CONVERSION ERROR"));
        assertFalse(sql.contains("calc_data"));
        assertEquals(List.of(8L, Date.valueOf("2026-10-20"), Date.valueOf("2026-10-20")), args);
        var reportArgs = new ArrayList<Object>();
        String reportSql = filters.reports(reportArgs);
        assertTrue(reportSql.contains("r.cam_id=?"));
        assertTrue(reportSql.contains("r.rel_parametros"));
        assertEquals(args, reportArgs);
    }

    @Test void aceitaLimitesAbertosSemTransformarAusenciaEmZero() {
        assertEquals("", EnvironmentalHistoryFilter.empty().calculations(new ArrayList<>()));
        var args = new ArrayList<Object>();
        var filters = new EnvironmentalHistoryFilter(null, null, LocalDate.parse("2026-10-20"));
        String sql = filters.calculations(args);
        assertTrue(sql.contains("DATE '2008-07-22'"));
        assertTrue(sql.contains("IS NOT NULL"));
        assertFalse(sql.contains(">=?"));
        assertEquals(List.of(Date.valueOf("2026-10-20")), args);
    }

    @Test void rejeitaCamadaInvalidaEPeriodoInvertido() {
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalHistoryFilter(0L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalHistoryFilter(null,
                LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-20")));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalHistoryFilter(null,
                LocalDate.of(0, 1, 1), null));
    }
}
