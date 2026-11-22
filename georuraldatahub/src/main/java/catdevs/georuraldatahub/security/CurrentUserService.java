package catdevs.georuraldatahub.security;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.CurrentUserDTO;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.enums.UserType;
import catdevs.georuraldatahub.repository.UserRepository;

/**
 * Liga o usuário autenticado no Keycloak ao registro da tabela {@code usuario}.
 *
 * <p>A chave de ligação é o e-mail (único na tabela). No primeiro acesso o
 * usuário é criado; nos seguintes, nome e perfil são atualizados a partir do
 * token, mantendo o Keycloak como fonte da verdade dos perfis. O {@code usr_id}
 * continua sendo usado nas FKs (fonte, conjunto, ingestão, quarentena, auditoria).</p>
 */
@Service
public class CurrentUserService {

    private final UserRepository users;
    private final KeycloakProperties keycloak;

    public CurrentUserService(UserRepository users, KeycloakProperties keycloak) {
        this.users = users;
        this.keycloak = keycloak;
    }

    /** Sincroniza e devolve o usuário do token atual. */
    @Transactional
    public CurrentUserDTO current() {
        Jwt jwt = currentJwt();
        Set<String> roles = KeycloakJwtAuthenticationConverter.extractRoles(jwt, keycloak.clientId());
        User user = sync(jwt, roles);
        return new CurrentUserDTO(user.getId(), jwt.getClaimAsString("preferred_username"), user.getName(),
                user.getEmail(), user.getUserType(), roles);
    }

    /** {@code usr_id} do usuário autenticado (cria o registro local se ainda não existir). */
    @Transactional
    public Long currentUserId() {
        return current().id();
    }

    private User sync(Jwt jwt, Set<String> roles) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O token do Keycloak não possui e-mail; cadastre um e-mail para o usuário no realm");
        }
        UserType type = Roles.highest(roles);
        String name = displayName(jwt, email);

        User user = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email.toLowerCase());
            created.setUserStatus(1);
            return created;
        });
        if (user.getId() != null && user.getUserStatus() == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário desativado no GeoRural DataHub");
        }
        boolean changed = user.getId() == null;
        if (!name.equals(user.getName())) {
            user.setName(name);
            changed = true;
        }
        if (type != null && type != user.getUserType()) {
            user.setUserType(type);
            changed = true;
        }
        return changed ? users.save(user) : user;
    }

    private static String displayName(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = jwt.getClaimAsString("preferred_username");
        }
        if (name == null || name.isBlank()) {
            name = email;
        }
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
    }
}
