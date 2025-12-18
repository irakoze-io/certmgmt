package tech.seccertificate.certmgmt.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import tech.seccertificate.certmgmt.security.JwtAuthenticationConverter;
import tech.seccertificate.certmgmt.security.SecurityExceptionHandlers;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${app.base-url}")
    private String appBaseUrl;

    private final SecurityExceptionHandlers securityExceptionHandlers;
    private final String jwtSecret;

    public SecurityConfig(SecurityExceptionHandlers securityExceptionHandlers,
                          @Value("${jwt.secret:eQbbr3+SBvrUDiYRCwjX5e1WC+Zowfmt2CHZCdTgpi0=}") String jwtSecret) {
        this.securityExceptionHandlers = securityExceptionHandlers;
        this.jwtSecret = jwtSecret;
    }

    /**
     * Security filter chain for API endpoints with JWT-based authentication.
     *
     * <p>This chain:
     * <ul>
     *   <li>Configures JWT decoder using symmetric key (HMAC-SHA256)</li>
     *   <li>Uses stateless sessions (no session storage)</li>
     *   <li>Protects all endpoints except public ones (actuator, docs, public auth endpoints)</li>
     *   <li>Provides custom authentication entry point and access denied handler</li>
     *   <li>Validates all authenticated requests</li>
     * </ul>
     *
     * <p>Public endpoints:
     * <ul>
     *   <li>Actuator endpoints</li>
     *   <li>API documentation (Swagger/Scalar)</li>
     *   <li>Customer registration (POST /api/customers)</li>
     *   <li>User creation (POST /auth/users)</li>
     *   <li>Login endpoint (POST /auth/login)</li>
     * </ul>
     *
     * <p>All other endpoints require a valid JWT token in the Authorization header:
     * <pre>Authorization: Bearer &lt;jwt-token&gt;</pre>
     */
    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/scalar/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow CORS preflight
                        .requestMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/certificates/verify/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/customers").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(publicEndpointBearerTokenResolver())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(securityExceptionHandlers)
                        .accessDeniedHandler(securityExceptionHandlers))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Disable CSRF for stateless API (JWT tokens are CSRF-resistant)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * CORS configuration for development and production.
     * Allows cross-origin requests from Angular frontend.
     *
     * @return CORS configuration source
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow Angular dev server (localhost:5050) and production frontend
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5050",
                "http://127.0.0.1:5050",
                "https://certmanager-six.vercel.app",
                "https://certmanager-51szoafvd-eracodes-projects.vercel.app",
                appBaseUrl
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight response for 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Creates a JWT decoder using symmetric key (HMAC-SHA256).
     *
     * <p>The decoder validates JWT tokens signed with the same secret key
     * used to generate tokens in JwtTokenService.
     *
     * @return JWT decoder
     */
    @Bean
    JwtDecoder jwtDecoder() {
        if (jwtSecret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        var secretKey = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * Creates a BearerTokenResolver that skips token extraction for public endpoints.
     *
     * <p>This prevents the OAuth2 Resource Server filter from attempting to validate
     * malformed Bearer tokens on public endpoints (like /auth/login), which would
     * otherwise cause 401 errors before authorization rules are checked.
     *
     * <p>For public endpoints, this resolver returns null, which tells the
     * OAuth2 Resource Server filter to skip token processing entirely.
     *
     * @return BearerTokenResolver that skips public endpoints
     */
    @Bean
    BearerTokenResolver publicEndpointBearerTokenResolver() {
        final BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        
        return new BearerTokenResolver() {
            @Override
            public String resolve(HttpServletRequest request) {
                // If this is a public endpoint, return null to skip token processing
                if (isPublicEndpoint(request)) {
                    return null;
                }
                // For protected endpoints, use the default resolver
                return defaultResolver.resolve(request);
            }
            
            /**
             * Check if the request is to a public endpoint that should not require authentication.
             * This matches the public endpoints defined in authorizeHttpRequests above.
             */
            private boolean isPublicEndpoint(HttpServletRequest request) {
                String path = request.getRequestURI();
                String method = request.getMethod();

                // Actuator, docs, and error endpoints
                if (path.startsWith("/actuator/") ||
                    path.startsWith("/v3/api-docs/") ||
                    path.startsWith("/swagger-ui/") ||
                    path.startsWith("/scalar/") ||
                    path.equals("/scalar") ||
                    path.equals("/error") ||
                    path.equals("/favicon.ico")) {
                    return true;
                }

                // Public API endpoints
                if (HttpMethod.POST.matches(method) && path.equals("/auth/login")) {
                    return true;
                }
                if (HttpMethod.POST.matches(method) && path.equals("/auth/users")) {
                    return true;
                }
                if (HttpMethod.POST.matches(method) && path.equals("/api/customers")) {
                    return true;
                }
                if (HttpMethod.GET.matches(method) && path.startsWith("/api/certificates/verify/")) {
                    return true;
                }
                return HttpMethod.OPTIONS.matches(method); // CORS preflight
            }
        };
    }

    /**
     * Creates a custom JWT authentication converter that extracts authorities from JWT claims.
     *
     * @return JWT authentication converter
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new JwtAuthenticationConverter();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}