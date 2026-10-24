package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jakarta.persistence.EntityManager;
import catdevs.georuraldatahub.entity.Occurrence;
import catdevs.georuraldatahub.entity.OccurrenceHistory;
import catdevs.georuraldatahub.validation.RecordValidationResult;

class OccurrenceValidationIntegrationTest {
    @Test void preservesCanonicalValidatorCodesAndFieldNamesWithHistoryAndSkipsValidRows() throws Exception {
        var em = mock(EntityManager.class);
        var service = new OccurrenceService();
        Field field = OccurrenceService.class.getDeclaredField("entityManager"); field.setAccessible(true); field.set(service, em);
        when(em.find(catdevs.georuraldatahub.entity.File.class, 6L)).thenReturn(new catdevs.georuraldatahub.entity.File());
        service.registerValidation(6L, List.of(RecordValidationResult.ok(0, "{}"),
                RecordValidationResult.invalid(1, "{}", List.of("INVALID_TYPE: id", "UNKNOWN_VALUE: tipo=outro"))));
        var stored = ArgumentCaptor.forClass(Object.class);
        verify(em, times(4)).persist(stored.capture());
        var occurrences = stored.getAllValues().stream().filter(Occurrence.class::isInstance).map(Occurrence.class::cast).toList();
        assertEquals(List.of("INVALID_TYPE", "UNKNOWN_VALUE"), occurrences.stream().map(Occurrence::getCode).toList());
        assertEquals(List.of("id", "tipo"), occurrences.stream().map(Occurrence::getFieldName).toList());
        assertEquals(2, stored.getAllValues().stream().filter(OccurrenceHistory.class::isInstance).count());
        verify(em, never()).flush();
    }
}
