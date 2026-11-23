package catdevs.georuraldatahub.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da integração com o Keycloak.
 *
 * @param enabled         quando {@code false}, todas as rotas ficam liberadas (apenas para desenvolvimento local)
 * @param clientId        client do front-end; os client roles dele também são lidos do token
 * @param allowedOrigins  origens liberadas no CORS (ex.: o endereço do front em Vue)
 */
@ConfigurationProperties(prefix = "security.keycloak")
public record KeycloakProperties(Boolean enabled, String clientId, List<String> allowedOrigins) {

    public KeycloakProperties {
        enabled = enabled == null || enabled;
        clientId = clientId == null || clientId.isBlank() ? "georural-frontend" : clientId;
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of("http://localhost:5173")
                : allowedOrigins;
    }
}
