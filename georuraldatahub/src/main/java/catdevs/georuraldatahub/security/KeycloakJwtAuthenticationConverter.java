package catdevs.georuraldatahub.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Converte o JWT emitido pelo Keycloak em uma autenticação do Spring Security.
 *
 * <p>Lê os perfis de {@code realm_access.roles} e de
 * {@code resource_access.<clientId>.roles}, normaliza para maiúsculas e gera as
 * authorities {@code ROLE_ADMIN}, {@code ROLE_OPERADOR}, etc. O nome do
 * principal é o {@code preferred_username}.</p>
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String clientId;

    public KeycloakJwtAuthenticationConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String role : extractRoles(jwt, clientId)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        String name = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, authorities, name != null ? name : jwt.getSubject());
    }

    /** Perfis do token (realm + client), em maiúsculas e sem repetição. */
    public static Set<String> extractRoles(Jwt jwt, String clientId) {
        Set<String> roles = new LinkedHashSet<>();
        addRoles(roles, jwt.getClaimAsMap("realm_access"));
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.get(clientId) instanceof Map<?, ?> client) {
            addRoles(roles, client);
        }
        return roles;
    }

    private static void addRoles(Set<String> target, Map<?, ?> access) {
        if (access != null && access.get("roles") instanceof Collection<?> values) {
            for (Object value : values) {
                if (value != null && !value.toString().isBlank()) {
                    target.add(value.toString().trim().toUpperCase(Locale.ROOT));
                }
            }
        }
    }
}
