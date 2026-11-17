
package catdevs.georuraldatahub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<IndicatorApiKeyFilter> disableContainerRegistration(
            IndicatorApiKeyFilter filter) {
        FilterRegistrationBean<IndicatorApiKeyFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            IndicatorApiKeyFilter indicatorApiKeyFilter) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(
                                (request, response, exception) ->
                                        response.sendError(401, "Autenticação necessária.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/indicadores/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(
                        indicatorApiKeyFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}