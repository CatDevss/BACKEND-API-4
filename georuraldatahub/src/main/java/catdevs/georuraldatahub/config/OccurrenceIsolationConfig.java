package catdevs.georuraldatahub.config;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;

/** Temporary compatibility mode for databases that have not received the BE-05 schema. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "features.occurrences.enabled", havingValue = "false")
public class OccurrenceIsolationConfig {
    @Bean
    public ManagedClassNameFilter occurrenceEntityFilter() {
        Set<String> excluded = Set.of("catdevs.georuraldatahub.entity.Occurrence",
                "catdevs.georuraldatahub.entity.OccurrenceHistory");
        return className -> !excluded.contains(className);
    }
}
