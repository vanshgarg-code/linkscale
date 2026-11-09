package com.linkscale.backend.security;

import com.linkscale.backend.config.LinkScaleProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Instant;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    private final LinkScaleProperties properties;
    private final Environment environment;

    public SecurityConfig(LinkScaleProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    private boolean isPhase1bDevLinkCreationExempt(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !"/api/v1/links".equals(request.getRequestURI())) {
            return false;
        }
        boolean isDevOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
        boolean isAllowed = properties.devMode() != null && properties.devMode().allowUnauthenticated();
        return isDevOrTest && isAllowed;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   CsrfTokenRepository csrfTokenRepository) throws Exception {

        RequestMatcher csrfProtectionMatcher = request -> {
            if (SAFE_METHODS.contains(request.getMethod())) {
                return false;
            }
            if (isPhase1bDevLinkCreationExempt(request)) {
                return false;
            }
            if (request.getRequestURI().startsWith("/actuator")) {
                return false;
            }
            // Require valid CSRF tokens for POST /api/v1/auth/register, /login, and /logout,
            // including when the caller is signed out, as well as all other mutating endpoints.
            return true;
        };

        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .requireCsrfProtectionMatcher(csrfProtectionMatcher)
                )
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            String body = """
                                    {"status":401,"error":"Unauthorized","message":"Full authentication is required to access this resource","timestamp":"%s"}
                                    """.formatted(Instant.now());
                            response.getWriter().write(body);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            String body = """
                                    {"status":403,"error":"Forbidden","message":"%s","timestamp":"%s"}
                                    """.formatted(escapeJson(accessDeniedException.getMessage()), Instant.now());
                            response.getWriter().write(body);
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/r/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/links").permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/users/me", "/api/v1/account").authenticated()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
