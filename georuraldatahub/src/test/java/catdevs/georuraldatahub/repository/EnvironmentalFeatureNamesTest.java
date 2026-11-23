package catdevs.georuraldatahub.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class EnvironmentalFeatureNamesTest {
    @Test void usesIndigenousLandNameBeforeMunicipalityInSearchAndHistory() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(5L), eq("%apucarana%"))).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("%apucarana%"))).thenReturn(0L);
        var repository = new EnvironmentalIndicatorRepository(jdbc);
        repository.features(5, "Apucarana", "", 0, 20);
        repository.history("Apucarana", "", 0, 20);
        var search = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(search.capture(), eq(Long.class), eq(5L), eq("%apucarana%"));
        var history = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(history.capture(), eq(Long.class), eq("%apucarana%"));
        for (String sql : java.util.List.of(search.getValue(), history.getValue())) {
            assertTrue(sql.contains("$.terrai_nome"));
            assertTrue(sql.indexOf("$.terrai_nome") < sql.indexOf("$.NM_MUN"));
            assertFalse(sql.contains("fei_id_origem"));
        }
    }
    @Test void searchesIndicatorInsideCombinedCalculations() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("%%"))).thenReturn(0L);
        new EnvironmentalIndicatorRepository(jdbc).history("", "ifc", 0, 20);
        var query = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(query.capture(), eq(Long.class), eq("%%"));
        assertTrue(query.getValue().contains("AND JSON_EXISTS(h.calc_resultado,'$.IFC')"));
        assertFalse(query.getValue().contains("END=?"));
    }

}
