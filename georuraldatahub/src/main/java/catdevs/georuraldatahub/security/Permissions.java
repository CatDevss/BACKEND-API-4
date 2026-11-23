package catdevs.georuraldatahub.security;

/**
 * Regras de acesso usadas no {@code @PreAuthorize} dos controllers.
 *
 * <p>Espelham a matriz de permissões do portal ({@code src/access/session.ts}
 * do front-end): cada constante aqui corresponde a uma permissão de lá, com os
 * mesmos perfis. Ao mudar um perfil, altere os dois lados.</p>
 *
 * <pre>
 * Constante              Permissão no front       Perfis
 * FILES_READ             files.read               ADMIN, GESTOR, OPERADOR, ANALISTA, AUDITOR
 * SOURCES_READ           sources.read             ADMIN, GESTOR, OPERADOR, ANALISTA, AUDITOR
 * SOURCES_CREATE         sources.create           GESTOR
 * SOURCES_UPDATE         sources.update           GESTOR, OPERADOR, ANALISTA
 * DICTIONARIES_READ      dictionaries.read        GESTOR, OPERADOR, ANALISTA, AUDITOR
 * DICTIONARIES_MANAGE    dictionaries.manage      GESTOR
 * DICTIONARIES_GENERATE  dictionaries.generate    GESTOR, OPERADOR
 * DATASETS_READ          datasets.read            GESTOR, OPERADOR, ANALISTA, AUDITOR
 * DATASETS_MANAGE        datasets.manage          OPERADOR
 * REPORTS_DELETE         reports.delete           ADMIN, GESTOR, OPERADOR, ANALISTA
 * MAP_READ               map.read                 GESTOR, OPERADOR, ANALISTA, AUDITOR
 * INGESTION_RUN          ingestion.run            OPERADOR
 * QUARANTINE_READ        quarantine.read          ADMIN, GESTOR, OPERADOR, ANALISTA, AUDITOR
 * QUARANTINE_MANAGE      quarantine.manage        OPERADOR
 * USERS_MANAGE           (somente API)            ADMIN
 * AUTHENTICATED          (somente API)            qualquer usuário logado
 * </pre>
 */
public final class Permissions {

    private static final String ALL = "hasAnyRole('ADMIN','GESTOR','OPERADOR','ANALISTA','AUDITOR')";

    public static final String FILES_READ = ALL;
    public static final String SOURCES_READ = ALL;
    public static final String SOURCES_CREATE = "hasRole('GESTOR')";
    public static final String SOURCES_UPDATE = "hasAnyRole('GESTOR','OPERADOR','ANALISTA')";
    public static final String DICTIONARIES_READ = "hasAnyRole('GESTOR','OPERADOR','ANALISTA','AUDITOR')";
    public static final String DICTIONARIES_MANAGE = "hasRole('GESTOR')";
    public static final String DICTIONARIES_GENERATE = "hasAnyRole('GESTOR','OPERADOR')";
    public static final String DATASETS_READ = "hasAnyRole('GESTOR','OPERADOR','ANALISTA','AUDITOR')";
    public static final String DATASETS_MANAGE = "hasRole('OPERADOR')";
    public static final String REPORTS_DELETE = "hasAnyRole('ADMIN','GESTOR','OPERADOR','ANALISTA')";
    public static final String MAP_READ = "hasAnyRole('GESTOR','OPERADOR','ANALISTA','AUDITOR')";
    public static final String INGESTION_RUN = "hasRole('OPERADOR')";
    public static final String QUARANTINE_READ = ALL;
    public static final String QUARANTINE_MANAGE = "hasRole('OPERADOR')";
    public static final String USERS_MANAGE = "hasRole('ADMIN')";
    public static final String AUTHENTICATED = "isAuthenticated()";

    private Permissions() {
    }
}
