package tech.seccertificate.certmgmt.config;

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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tech.seccertificate.certmgmt.security.JwtAuthenticationConverter;
import tech.seccertificate.certmgmt.security.SecurityExceptionHandlers;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


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
                "https://certmanager-six.vercel.app"
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