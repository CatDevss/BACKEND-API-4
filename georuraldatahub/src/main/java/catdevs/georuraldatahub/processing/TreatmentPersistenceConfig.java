package catdevs.georuraldatahub.processing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// As novas tabelas só são exigidas após a aplicação da migração pelo responsável do banco.
@Configuration
@ConditionalOnProperty(name = "processing.persistence.enabled", havingValue = "true")
@EntityScan(basePackages = {"catdevs.georuraldatahub.entity", "catdevs.georuralprocessing.entity"})
@EnableJpaRepositories(basePackages = {"catdevs.georuraldatahub.repository", "catdevs.georuralprocessing.repository"})
public class TreatmentPersistenceConfig { }
