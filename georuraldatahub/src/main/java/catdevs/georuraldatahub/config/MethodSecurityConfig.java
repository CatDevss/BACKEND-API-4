package catdevs.georuraldatahub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Liga o {@code @PreAuthorize} dos controllers.
 *
 * <p>Fica desligado quando {@code security.keycloak.enabled=false}, para que o
 * modo de desenvolvimento sem Keycloak continue liberando todas as rotas.</p>
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "security.keycloak", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MethodSecurityConfig {
}
