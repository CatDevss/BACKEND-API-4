package catdevs.georuraldatahub.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticação temporária e explícita para os endpoints de indicadores
 * A chave identifica o usuário proprietário configurado em INDICATORS_API_USER_ID
 * Não substitui a futura autenticação individual da aplicação
 */
@Component
public class IndicatorApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final String configuredApiKey;
    private final String configuredUserId;

    public IndicatorApiKeyFilter(
            @Value("${indicators.security.api-key:}") String configuredApiKey,
            @Value("${indicators.security.user-id:}") String configuredUserId) {
        this.configuredApiKey = configuredApiKey == null ? "" : configuredApiKey;
        this.configuredUserId = configuredUserId == null ? "" : configuredUserId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/indicadores");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String suppliedKey = request.getHeader(HEADER);
        boolean configured = !configuredApiKey.isBlank() && !configuredUserId.isBlank();
        boolean validKey = suppliedKey != null
                && MessageDigest.isEqual(
                        configuredApiKey.getBytes(StandardCharsets.UTF_8),
                        suppliedKey.getBytes(StandardCharsets.UTF_8));

        if (configured && validKey) {
            try {
                Long userId = Long.valueOf(configuredUserId);
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_INDICATOR_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (NumberFormatException exception) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}