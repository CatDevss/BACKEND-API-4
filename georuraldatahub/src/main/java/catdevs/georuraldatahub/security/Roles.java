package catdevs.georuraldatahub.security;

import java.util.List;

import catdevs.georuraldatahub.entity.enums.UserType;

/**
 * Perfis de acesso do GeoRural DataHub.
 *
 * <p>Os nomes são os mesmos dos realm roles cadastrados no Keycloak e do
 * enum {@link UserType} gravado na tabela {@code usuario}. No Spring Security
 * cada perfil vira a authority {@code ROLE_<NOME>}.</p>
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String OPERADOR = "OPERADOR";
    public static final String ANALISTA = "ANALISTA";
    public static final String GESTOR = "GESTOR";
    public static final String AUDITOR = "AUDITOR";

    /** Todos os perfis, do maior para o menor privilégio. */
    public static final List<String> ALL = List.of(ADMIN, OPERADOR, ANALISTA, GESTOR, AUDITOR);

    private Roles() {
    }

    /** Perfil de maior privilégio entre os informados, ou {@code null} se nenhum for conhecido. */
    public static UserType highest(Iterable<String> roles) {
        UserType best = null;
        for (String role : roles) {
            int index = ALL.indexOf(role);
            if (index >= 0 && (best == null || index < ALL.indexOf(best.name()))) {
                best = UserType.valueOf(role);
            }
        }
        return best;
    }
}
