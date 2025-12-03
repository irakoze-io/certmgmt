package tech.seccertificate.certmgmt.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI configuration for API documentation using Scalar UI.
 *
 * <p>This configuration sets up OpenAPI 3.0 specification with Scalar as the UI.
 * Scalar provides a modern, interactive API documentation and testing interface.
 *
 * <p>Access the API documentation at:
 * <ul>
 *   <li>Scalar UI: http://localhost:8080/scalar</li>
 *   <li>OpenAPI JSON: http://localhost:8080/v3/api-docs</li>
 *   <li>OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml</li>
 * </ul>
 *
 * @author Ivan-Beaudry Irakoze
 * @since December 2025
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.version}")
    private String appVersion;

    @Value("${spring.application.name}")
    private String appName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(appName)
                        .version(appVersion)
                        .description("""
                                REST API for designing and implementing a secure system to define,
                                generate, and manage PDF certificates.

                                ## Features
                                - Certificate generation (synchronous and asynchronous)
                                - Certificate CRUD operations
                                - Certificate verification
                                - Template management
                                - Customer management

                                ## Multi-Tenancy
                                All endpoints (except public verification) require the `X-Tenant-Id` header
                                to identify the tenant context.
                                """)
                        .contact(new Contact()
                                .name("Ivan-Beaudry Irakoze")
                                .email("erakoze.io@gmail.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://eracodes.me")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("<empty>")
                                .description("Production Server")
                ));
    }
}
