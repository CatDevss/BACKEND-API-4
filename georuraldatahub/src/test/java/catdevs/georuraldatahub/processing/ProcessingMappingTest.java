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
            var mapping = metadata.buildMetadata();
            try (var factory = mapping.buildSessionFactory(); var manager = factory.createEntityManager()) {
                var jdbcType = ((org.hibernate.engine.spi.SessionFactoryImplementor) factory).getTypeConfiguration()
                        .getJdbcTypeRegistry().getDescriptor(org.hibernate.type.SqlTypes.GEOMETRY);
                assertEquals("SDOGeometryType", jdbcType.getClass().getSimpleName());
                // Reproduz a informação retornada pelo Oracle JDBC sem conectar ao banco.
                var validator = new org.hibernate.tool.schema.internal.GroupedSchemaValidatorImpl(
                        new org.hibernate.tool.schema.internal.HibernateSchemaManagementTool(),
                        org.hibernate.tool.schema.spi.SchemaFilter.ALL) {
                    void check(org.hibernate.mapping.Table table, org.hibernate.mapping.Column column,
                            org.hibernate.tool.schema.extract.spi.ColumnInformation actual) {
                        validateColumnType(table, column, actual, mapping,
                                ((org.hibernate.engine.spi.SessionFactoryImplementor) factory).getJdbcServices().getDialect());
                    }
                };
                for (var entity : List.of(Feature.class, RasterMetadata.class)) {
                    var binding = mapping.getEntityBinding(entity.getName());
                    var column = binding.getProperty(entity == Feature.class ? "geometry" : "footprint").getColumns().getFirst();
                    var identifier = org.hibernate.boot.model.naming.Identifier.toIdentifier(column.getName());
                    var actual = new org.hibernate.tool.schema.extract.internal.ColumnInformationImpl(
                            null, identifier, java.sql.Types.STRUCT, "SDO_GEOMETRY", 0, 0, false);
                    assertDoesNotThrow(() -> validator.check(binding.getTable(), column, actual));
                    var wrong = new org.hibernate.tool.schema.extract.internal.ColumnInformationImpl(
                            null, identifier, java.sql.Types.VARCHAR, "VARCHAR2", 255, 0, true);
                    assertThrows(org.hibernate.tool.schema.spi.SchemaManagementException.class,
                            () -> validator.check(binding.getTable(), column, wrong));
                }
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
