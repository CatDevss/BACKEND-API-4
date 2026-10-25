package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuralprocessing.entity.*;
import catdevs.georuralprocessing.repository.*;
import java.util.List;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;

class ProcessingMappingTest {
    @Test void oracleMappingsAndJpaQueriesBuildWithoutConnectingToOracle() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.OracleDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try {
            var metadata = new MetadataSources(registry);
            for (Class<?> entity : List.of(User.class, Source.class, catdevs.georuraldatahub.entity.Dictionary.class,
                    DataIngestion.class, catdevs.georuraldatahub.entity.File.class, Dataset.class, Version.class, Layer.class,
                    Feature.class, RasterMetadata.class, TreatmentExecution.class)) metadata.addAnnotatedClass(entity);
            try (var factory = metadata.buildMetadata().buildSessionFactory(); var manager = factory.createEntityManager()) {
                var jdbcType = ((org.hibernate.engine.spi.SessionFactoryImplementor) factory).getTypeConfiguration()
                        .getJdbcTypeRegistry().getDescriptor(org.hibernate.type.SqlTypes.GEOMETRY);
                assertEquals("SDOGeometryType", jdbcType.getClass().getSimpleName());
                var repositories = new JpaRepositoryFactory(manager);
                assertNotNull(repositories.getRepository(FeatureRepository.class));
                assertNotNull(repositories.getRepository(VersionRepository.class));
                assertNotNull(repositories.getRepository(FileRepository.class));
                assertNotNull(repositories.getRepository(TreatmentExecutionRepository.class));
                assertNotNull(repositories.getRepository(RasterMetadataRepository.class));
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
    @Test void defaultApplicationScanDoesNotRequireTheProposedTables() {
        var names = new PersistenceManagedTypesScanner(new DefaultResourceLoader())
                .scan("catdevs.georuraldatahub").getManagedClassNames();
        assertTrue(names.contains(Feature.class.getName()));
        assertFalse(names.contains(TreatmentExecution.class.getName()));
        assertFalse(names.contains(RasterMetadata.class.getName()));
    }
}
