package catdevs.georuraldatahub.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.config.MethodSecurityConfig;
import catdevs.georuraldatahub.config.SecurityConfig;
import catdevs.georuraldatahub.controller.UserController;
import catdevs.georuraldatahub.dto.CurrentUserDTO;
import catdevs.georuraldatahub.entity.enums.UserType;
import catdevs.georuraldatahub.service.UserService;

/**
 * Passa requisições pela cadeia real de segurança: JWT no formato do Keycloak,
 * conversão de perfis, @PreAuthorize e tratamento de 401/403.
 */
@WebMvcTest(UserController.class)
@Import({ SecurityConfig.class, MethodSecurityConfig.class, KeycloakSecurityTest.Endpoints.class })
@EnableConfigurationProperties(KeycloakProperties.class)
@TestPropertySource(properties = {
        "security.keycloak.enabled=true",
        "security.keycloak.client-id=georural-frontend",
        "security.keycloak.allowed-origins=http://localhost:5173" })
class KeycloakSecurityTest {

    /** Um endpoint por regra de {@link Permissions}: GET /teste/{nome da constante}. */
    @RestController
    static class Endpoints {
        @PreAuthorize(Permissions.FILES_READ) @GetMapping("/teste/FILES_READ") String a() { return "ok"; }
        @PreAuthorize(Permissions.SOURCES_READ) @GetMapping("/teste/SOURCES_READ") String b() { return "ok"; }
        @PreAuthorize(Permissions.SOURCES_CREATE) @GetMapping("/teste/SOURCES_CREATE") String c() { return "ok"; }
        @PreAuthorize(Permissions.SOURCES_UPDATE) @GetMapping("/teste/SOURCES_UPDATE") String d() { return "ok"; }
        @PreAuthorize(Permissions.DICTIONARIES_READ) @GetMapping("/teste/DICTIONARIES_READ") String e() { return "ok"; }
        @PreAuthorize(Permissions.DICTIONARIES_MANAGE) @GetMapping("/teste/DICTIONARIES_MANAGE") String f() { return "ok"; }
        @PreAuthorize(Permissions.DICTIONARIES_GENERATE) @GetMapping("/teste/DICTIONARIES_GENERATE") String g() { return "ok"; }
        @PreAuthorize(Permissions.DATASETS_READ) @GetMapping("/teste/DATASETS_READ") String h() { return "ok"; }
        @PreAuthorize(Permissions.DATASETS_MANAGE) @GetMapping("/teste/DATASETS_MANAGE") String i() { return "ok"; }
        @PreAuthorize(Permissions.REPORTS_DELETE) @GetMapping("/teste/REPORTS_DELETE") String j() { return "ok"; }
        @PreAuthorize(Permissions.MAP_READ) @GetMapping("/teste/MAP_READ") String k() { return "ok"; }
        @PreAuthorize(Permissions.INGESTION_RUN) @GetMapping("/teste/INGESTION_RUN") String l() { return "ok"; }
        @PreAuthorize(Permissions.QUARANTINE_READ) @GetMapping("/teste/QUARANTINE_READ") String m() { return "ok"; }
        @PreAuthorize(Permissions.QUARANTINE_MANAGE) @GetMapping("/teste/QUARANTINE_MANAGE") String n() { return "ok"; }
        @PreAuthorize(Permissions.USERS_MANAGE) @GetMapping("/teste/USERS_MANAGE") String o() { return "ok"; }
    }

    /** Mesma matriz do front-end (src/access/session.ts). */
    static final Map<String, Set<String>> MATRIZ = Map.ofEntries(
            Map.entry("FILES_READ", Set.of("ADMIN", "GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")),
            Map.entry("SOURCES_READ", Set.of("ADMIN", "GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")),
            Map.entry("SOURCES_CREATE", Set.of("GESTOR")),
            Map.entry("SOURCES_UPDATE", Set.of("GESTOR", "OPERADOR", "ANALISTA")),
            Map.entry("DICTIONARIES_READ", Set.of("GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")),
            Map.entry("DICTIONARIES_MANAGE", Set.of("GESTOR")),
            Map.entry("DICTIONARIES_GENERATE", Set.of("GESTOR", "OPERADOR")),
            Map.entry("DATASETS_READ", Set.of("GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")),
            Map.entry("DATASETS_MANAGE", Set.of("OPERADOR")),
            Map.entry("REPORTS_DELETE", Set.of("ADMIN", "GESTOR", "OPERADOR", "ANALISTA")),
            Map.entry("MAP_READ", Set.of("GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")),
            Map.entry("INGESTION_RUN", Set.of("OPERADOR")),
            Map.entry("QUARANTINE_READ", Set.of("ADMIN", "GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")),
            Map.entry("QUARANTINE_MANAGE", Set.of("OPERADOR")),
            Map.entry("USERS_MANAGE", Set.of("ADMIN")));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    UserService userService;

    @MockitoBean
    CurrentUserService currentUserService;

    @BeforeEach
    void tokens() {
        // O "token" do header é o nome do perfil; o decoder simulado devolve um JWT no formato do Keycloak.
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> keycloakJwt(inv.getArgument(0)));
        when(userService.getAllUsers()).thenReturn(List.of());
        when(currentUserService.current()).thenReturn(new CurrentUserDTO(1L, "analista", "Alice",
                "analista@georural.local", UserType.ANALISTA, Set.of("ANALISTA")));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/usuarios/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/teste/FILES_READ")).andExpect(status().isUnauthorized());
    }

    @Test
    void cadaRegraLiberaSomenteOsPerfisDaMatriz() throws Exception {
        for (var regra : MATRIZ.entrySet()) {
            expect(() -> get("/teste/" + regra.getKey()), regra.getValue());
        }
        mvc.perform(get("/teste/FILES_READ").header("Authorization", "Bearer VISITANTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuariosPorPerfil() throws Exception {
        expect(() -> get("/usuarios"), Set.of("ADMIN"));
        expect(() -> get("/usuarios/me"), Set.copyOf(Roles.ALL));
    }

    @Test
    void corsLiberaSomenteOFront() throws Exception {
        mvc.perform(options("/teste/FILES_READ")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/teste/FILES_READ")
                .header("Origin", "http://malicioso.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void converterLeRealmEClientRoles() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("preferred_username", "ana")
                .claim("realm_access", Map.of("roles", List.of("admin", "default-roles-georural")))
                .claim("resource_access", Map.of("georural-frontend", Map.of("roles", List.of("Auditor")),
                        "account", Map.of("roles", List.of("manage-account"))))
                .build();

        AbstractAuthenticationToken auth = new KeycloakJwtAuthenticationConverter("georural-frontend").convert(jwt);

        assertThat(auth.getName()).isEqualTo("ana");
        assertThat(auth.getAuthorities()).extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_AUDITOR")
                .doesNotContain("ROLE_MANAGE-ACCOUNT");
        assertThat(Roles.highest(Set.of("AUDITOR", "OPERADOR"))).isEqualTo(UserType.OPERADOR);
        assertThat(Roles.highest(Set.of("DEFAULT-ROLES-GEORURAL"))).isNull();
    }

    /** Uma requisição nova por perfil: o builder do MockMvc acumula headers. */
    private void expect(Supplier<MockHttpServletRequestBuilder> request, Set<String> allowed) throws Exception {
        for (String role : Roles.ALL) {
            var result = mvc.perform(request.get().header("Authorization", "Bearer " + role));
            result.andExpect(allowed.contains(role) ? status().isOk() : status().isForbidden());
        }
    }

    private static Jwt keycloakJwt(String role) {
        return Jwt.withTokenValue(role).header("alg", "RS256")
                .subject("sub-" + role)
                .issuer("http://localhost:8081/realms/georural")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", role.toLowerCase())
                .claim("email", role.toLowerCase() + "@georural.local")
                .claim("realm_access", Map.of("roles", List.of(role.toLowerCase(), "offline_access")))
                .build();
    }
}
