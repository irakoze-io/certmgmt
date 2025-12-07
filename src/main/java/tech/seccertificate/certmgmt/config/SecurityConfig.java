package tech.seccertificate.certmgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tech.seccertificate.certmgmt.security.TenantUserDetailsService;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TenantUserDetailsService userDetailsService;

    public SecurityConfig(TenantUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * Security filter chain for OAuth2 Authorization Server endpoints.
     * This chain has higher priority (lower order) and handles OAuth2/OIDC endpoints.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
        return http
                .securityMatcher("/oauth2/**", "/.well-known/**")
                .oauth2AuthorizationServer(authorizationServer -> authorizationServer
                        .oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .build();
    }

    /**
     * Security filter chain for API endpoints.
     * This chain handles all other requests including public endpoints.
     */
    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/scalar/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/users").permitAll()
                        .anyRequest().authenticated())
                .userDetailsService(userDetailsService)
                .httpBasic(Customizer.withDefaults())
                // .formLogin(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for API endpoints
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}