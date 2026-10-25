package catdevs.georuraldatahub.repository;

import static org.junit.jupiter.api.Assertions.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.File;
import jakarta.persistence.LockModeType;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

class QuarantineRepositoryQueryTest {
    @Test void jpqlIsValidWithOracleDialectAndClaimUsesTimedWriteLock() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.OracleDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var metadata = new MetadataSources(registry);
            for (Class<?> entity : new Class<?>[]{Quarantine.class, User.class, Source.class, Dictionary.class,
                    DataIngestion.class, File.class, Dataset.class}) metadata.addAnnotatedClass(entity);
            try (var factory = metadata.buildMetadata().buildSessionFactory()) {
                try (var entityManager = factory.createEntityManager()) {
                    assertNotNull(new JpaRepositoryFactory(entityManager).getRepository(QuarantineRepository.class));
                }
                var translator = ((SessionFactoryImplementor) factory).getQueryEngine().getHqlTranslator();
                var search = QuarantineRepository.class.getMethod("search", String.class, Long.class, Long.class, boolean.class, Pageable.class);
                translator.translate(search.getAnnotation(Query.class).value(), Quarantine.class);
                var locked = QuarantineRepository.class.getMethod("findLockedById", Long.class);
                translator.translate(locked.getAnnotation(Query.class).value(), Quarantine.class);
                assertEquals(LockModeType.PESSIMISTIC_WRITE, locked.getAnnotation(Lock.class).value());
                var hint = locked.getAnnotation(QueryHints.class).value()[0];
                assertEquals("jakarta.persistence.lock.timeout", hint.name()); assertEquals("10000", hint.value());
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
