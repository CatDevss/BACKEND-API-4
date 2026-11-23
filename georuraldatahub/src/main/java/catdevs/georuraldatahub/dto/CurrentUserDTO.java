package catdevs.georuraldatahub.dto;

import java.util.Set;

import catdevs.georuraldatahub.entity.enums.UserType;

/**
 * Usuário autenticado: registro local (tabela usuario) + perfis vindos do Keycloak.
 * O front usa o {@code id} como {@code userId} nas requisições e os {@code roles}
 * para montar o menu.
 */
public record CurrentUserDTO(
        Long id,
        String username,
        String name,
        String email,
        UserType userType,
        Set<String> roles) {
}
