package tech.seccertificate.certmgmt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Value("${app.base-url}")
    private String appUrl;

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

                                ## Authentication
                                Most endpoints require JWT Bearer authentication. Use the `/auth/login` endpoint
                                to obtain a JWT token, then include it in the Authorization header:
                                ```
                                Authorization: Bearer <your-jwt-token>
                                ```

                                ## Multi-Tenancy
                                All endpoints (except public verification) require the `X-Tenant-Id` header
                                to identify the tenant context.

                                ## Unified Response Format
                                All API endpoints return responses wrapped in a unified `Response<T>` structure:

                                **Success Response (200/201):**
                                ```json
                                {
                                  "success": true,
                                  "message": "Operation completed successfully",
                                  "data": { ... },
                                  "details": null
                                }
                                ```

                                **Error Response (400/401/403/404/500):**
                                ```json
                                {
                                  "success": false,
                                  "message": "Specific error description",
                                  "error": {
                                    "errorCode": "APP_SPECIFIC_CODE",
                                    "errorType": "Validation Error",
                                    "details": ["email is required", "phone number is invalid"],
                                    "data": { "fieldErrors": { ... } }
                                  },
                                  "details": null
                                }
                                ```

                                See the API Response Standards documentation for more details.
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
                                .url(appUrl)
                                .description("Production Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .description("JWT Bearer token authentication. Obtain token via /auth/login endpoint."))
                        .addExamples("successResponse", new Example()
                                .summary("Success Response Example")
                                .description("Example of a successful API response")
                                .value(Map.of(
                                        "success", true,
                                        "message", "Operation completed successfully",
                                        "data", Map.of("id", 1, "name", "Example"),
                                        "details", new ArrayList<>()
                                )))
                        .addExamples("errorResponse", new Example()
                                .summary("Error Response Example")
                                .description("Example of an error API response")
                                .value(Map.of(
                                        "success", false,
                                        "message", "Validation failed. Please check the error details.",
                                        "error", Map.of(
                                                "errorCode", "VALIDATION_FAILED",
                                                "errorType", "Validation Error",
                                                "details", List.of("email is required", "phone number is invalid"),
                                                "data", Map.of("fieldErrors", Map.of(
                                                        "email", "email is required",
                                                        "phone", "phone number is invalid"
                                                ))
                                        ),
                                        "details", new ArrayList<>()
                                )))
                        .addExamples("notFoundResponse", new Example()
                                .summary("Not Found Response Example")
                                .description("Example of a 404 Not Found error response")
                                .value(Map.of(
                                        "success", false,
                                        "message", "The application could not find the requested resource",
                                        "error", Map.of(
                                                "errorCode", "RESOURCE_NOT_FOUND",
                                                "errorType", "Resource Not Found",
                                                "details", List.of("Resource with ID 123 not found"),
                                                "data",  new ArrayList<>()
                                        ),
                                        "details",  new ArrayList<>()
                                ))));
    }
}
