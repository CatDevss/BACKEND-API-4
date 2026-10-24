package catdevs.georuraldatahub.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import catdevs.georuraldatahub.controller.OccurrenceController;
import catdevs.georuraldatahub.service.OccurrenceService;

class OccurrenceIsolationConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(jakarta.persistence.EntityManagerFactory.class,
                    () -> org.mockito.Mockito.mock(jakarta.persistence.EntityManagerFactory.class))
            .withUserConfiguration(OccurrenceIsolationConfig.class, OccurrenceService.class, OccurrenceController.class);

    @Test void disabledModeExcludesOnlyTheTwoNewEntitiesAndDoesNotRegisterOccurrenceEndpoints() {
        runner.withPropertyValues("features.occurrences.enabled=false").run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(OccurrenceService.class).isEmpty());
            assertTrue(context.getBeansOfType(OccurrenceController.class).isEmpty());
            var loader = new DefaultResourceLoader();
            var full = new HashSet<>(new PersistenceManagedTypesScanner(loader).scan("catdevs.georuraldatahub.entity").getManagedClassNames());
            var isolated = new HashSet<>(new PersistenceManagedTypesScanner(loader, context.getBean(ManagedClassNameFilter.class))
                    .scan("catdevs.georuraldatahub.entity").getManagedClassNames());
            assertTrue(isolated.contains("catdevs.georuraldatahub.entity.File"));
            assertTrue(isolated.contains("catdevs.georuraldatahub.entity.Quarantine"));
            assertTrue(isolated.contains("catdevs.georuraldatahub.entity.Dataset"));
            full.removeAll(isolated);
            assertEquals(Set.of("catdevs.georuraldatahub.entity.Occurrence", "catdevs.georuraldatahub.entity.OccurrenceHistory"), full);
        });
    }
    @Test void defaultModePreservesOccurrenceIntegrationAndEntityScanning() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(OccurrenceService.class).size());
            assertEquals(1, context.getBeansOfType(OccurrenceController.class).size());
            assertTrue(context.getBeansOfType(ManagedClassNameFilter.class).isEmpty());
        });
    }
}
