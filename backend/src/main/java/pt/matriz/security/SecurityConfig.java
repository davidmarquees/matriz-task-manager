package pt.matriz.security;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import pt.matriz.accounts.AccountService;
import pt.matriz.web.Problems;

/**
 * Authentication with a server-side session (docs/architecture.md): HttpOnly session cookie,
 * 30-minute inactivity timeout (application.yml), CSRF token in the XSRF-TOKEN cookie sent back in the
 * X-XSRF-TOKEN header, and 401/403 answers in the Problem Details format (NFR5).
 * Login, logout and the password change are handled by {@link AuthController}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, CsrfTokenRepository csrfTokens, SecurityContextRepository contexts,
                            SessionRegistry sessions, AccountService accounts, ObjectMapper json) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(contexts))
                .sessionManagement(session -> session
                        // unlimited sessions per user, but tracked, so a password change can end the others (D48)
                        .maximumSessions(-1)
                        .sessionRegistry(sessions)
                        .expiredSessionStrategy(event -> unauthorized(event.getResponse(), json)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/logout", "/auth/password-reset",
                                "/auth/password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> unauthorized(response, json))
                        .accessDeniedHandler((request, response, e) ->
                                Problems.write(response, json, Problems.of(HttpStatus.FORBIDDEN))))
                .addFilterBefore(new AccountStateFilter(accounts, json), AuthorizationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable());
        return http.build();
    }

    private static void unauthorized(HttpServletResponse response, ObjectMapper json) throws IOException {
        Problems.write(response, json, Problems.of(HttpStatus.UNAUTHORIZED));
    }

    /**
     * CORS only for the origins in MATRIZ_CORS_ALLOWED_ORIGINS (comma-separated, docs/deployment.md).
     * Empty by default: no cross-origin access, as in local development, where the browser reaches the
     * API through the same origin (Vite proxy or nginx).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${matriz.cors-allowed-origins}") String origins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (!allowed.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(allowed);
            config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
            config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
            config.setAllowCredentials(true);
            source.registerCorsConfiguration("/**", config);
        }
        return source;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${matriz.cookie-secure}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // Path "/" so the SPA can read it on every page; SameSite=Lax like the session cookie.
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Keeps the session registry up to date when sessions end or change id. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** Applied at login: new session id (session fixation), new CSRF token, session registered. */
    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(CsrfTokenRepository csrfTokens, SessionRegistry sessions) {
        CsrfAuthenticationStrategy newCsrfToken = new CsrfAuthenticationStrategy(csrfTokens);
        // load the new token at once, so the login response already carries the new XSRF-TOKEN cookie
        newCsrfToken.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                newCsrfToken,
                new RegisterSessionAuthenticationStrategy(sessions)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
