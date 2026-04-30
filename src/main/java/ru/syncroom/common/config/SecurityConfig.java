package ru.syncroom.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.syncroom.common.security.JwtAuthenticationFilter;
import java.util.List;

/**
 * Spring Security configuration.
 * Configures JWT-based authentication and public endpoints for auth operations.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppCorsProperties appCorsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .requireCsrfProtectionMatcher(csrfProtectionMatcher())
                )
                .sessionManagement(session -> 
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI and OpenAPI docs
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                        // Public endpoints
                        .requestMatchers("/api/auth/oauth").permitAll()
                        .requestMatchers("/api/auth/email").permitAll()
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/csrf").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        // WebSocket handshake — auth happens inside ChannelInterceptor
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/ws-stomp/**").permitAll()
                        // SRS media server callback — called from Docker internal network, no JWT
                        .requestMatchers("/api/projector/srs-callback").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RequestMatcher csrfProtectionMatcher() {
        return request -> {
            String method = request.getMethod();
            boolean unsafeMethod = HttpMethod.POST.matches(method)
                    || HttpMethod.PUT.matches(method)
                    || HttpMethod.PATCH.matches(method)
                    || HttpMethod.DELETE.matches(method);
            if (!unsafeMethod) {
                return false;
            }

            String path = request.getRequestURI();
            if (path.startsWith("/api/auth/")
                    || path.startsWith("/ws/")
                    || path.startsWith("/ws-stomp/")
                    || path.equals("/api/projector/srs-callback")) {
                return false;
            }

            String authHeader = request.getHeader("Authorization");
            boolean bearerRequest = authHeader != null && authHeader.startsWith("Bearer ");
            return !bearerRequest;
        };
    }

    /**
     * CORS configuration for browser/web clients.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appCorsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

