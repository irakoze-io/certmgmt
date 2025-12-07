package tech.seccertificate.certmgmt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.io.IOException;

/**
 * Servlet Filter for tenant resolution and validation.
 * This filter runs before Spring MVC and validates tenant existence and activity.
 * <p>
 * Based on: Schema Separation Implementation pattern from:
 * <a href="https://medium.com/@shahharsh172/building-secure-multi-tenant-applications-with-spring-boot">Building Secure Mutli-Tenant Applications</a>
 * <p>
 * Tenant can be identified via:
 * 1. X-Tenant-Id header (customer ID)
 * 2. X-Tenant-Schema header (schema name directly)
 * 3. Subdomain (extracted from Host header)
 *
 * @author Ivan-Beaudry Irakoze
 * @since Dec 4, 2024
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final CustomerRepository customerRepository;

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String TENANT_SCHEMA_HEADER = "X-Tenant-Schema";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip tenant resolution for excluded paths
        if (shouldSkipTenantResolution(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String tenantId = request.getHeader(TENANT_ID_HEADER);
            String tenantSchema = request.getHeader(TENANT_SCHEMA_HEADER);

            if (tenantId != null && !tenantId.isEmpty()) {
                // Resolve schema from customer ID with validation
                String schema = resolveTenantFromCustomerId(tenantId, response);
                if (schema == null) {
                    // Error response already set
                    return;
                }
                TenantContext.setTenantSchema(schema);
                log.debug("Set tenant schema {} from customer ID {}", schema, tenantId);
            } else if (tenantSchema != null && !tenantSchema.isEmpty()) {
                // Validate schema exists and customer is active
                if (!validateTenantSchema(tenantSchema, response)) {
                    // Error response already set
                    return;
                }
                TenantContext.setTenantSchema(tenantSchema);
                log.debug("Set tenant schema directly: {}", tenantSchema);
            } else {
                // Try to extract from subdomain
                String host = request.getHeader("Host");
                if (host != null) {
                    String schema = extractSchemaFromHost(host);
                    if (schema != null && !schema.isEmpty()) {
                        if (!validateTenantSchema(schema, response)) {
                            // Error response already set
                            return;
                        }
                        TenantContext.setTenantSchema(schema);
                        log.debug("Set tenant schema from subdomain: {}", schema);
                    } else {
                        // No tenant specified - operations will use public schema
                        log.debug("No tenant specified, using default (public) schema");
                    }
                } else {
                    // No tenant specified - operations will use public schema
                    log.debug("No tenant specified, using default (public) schema");
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request to prevent memory leaks
            TenantContext.clear();
            log.debug("Cleared tenant context after request");
        }
    }

    /**
     * Resolve tenant schema from customer ID with validation.
     *
     * @param tenantId The tenant ID (customer ID)
     * @param response HTTP response to set error status if validation fails
     * @return The tenant schema name, or null if validation fails
     */
    private String resolveTenantFromCustomerId(String tenantId, HttpServletResponse response) {
        try {
            Long customerId = Long.parseLong(tenantId);
            Customer customer = customerRepository.findById(customerId)
                    .orElse(null);

            if (customer == null) {
                log.warn("Customer not found: {}", customerId);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\":\"Customer not found: " + customerId + "\"}");
                } catch (IOException e) {
                    log.error("Failed to write error response", e);
                }
                return null;
            }

            // Validate customer is active
            if (customer.getStatus() != Customer.CustomerStatus.ACTIVE) {
                log.warn("Customer {} is not active (status: {})", customerId, customer.getStatus());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\":\"Customer is not active\"}");
                } catch (IOException e) {
                    log.error("Failed to write error response", e);
                }
                return null;
            }

            String schema = customer.getTenantSchema();
            if (schema == null || schema.isEmpty()) {
                log.warn("Customer {} does not have a tenant schema configured", customerId);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\":\"Customer does not have a tenant schema configured\"}");
                } catch (IOException e) {
                    log.error("Failed to write error response", e);
                }
                return null;
            }

            return schema;
        } catch (NumberFormatException e) {
            log.warn("Invalid tenant ID format: {}", tenantId);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\":\"Invalid tenant ID format\"}");
            } catch (IOException ioException) {
                log.error("Failed to write error response", ioException);
            }
            return null;
        }
    }

    /**
     * Validate tenant schema exists and customer is active.
     *
     * @param tenantSchema The tenant schema name
     * @param response HTTP response to set error status if validation fails
     * @return true if validation passes, false otherwise
     */
    private boolean validateTenantSchema(String tenantSchema, HttpServletResponse response) {
        // Validate schema name format
        if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
            log.warn("Invalid tenant schema format: {}", tenantSchema);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\":\"Invalid tenant schema format\"}");
            } catch (IOException e) {
                log.error("Failed to write error response", e);
            }
            return false;
        }

        // Check if customer exists and is active for this schema
        var customerOpt = customerRepository.findByTenantSchema(tenantSchema);
        if (customerOpt.isEmpty()) {
            log.warn("No customer found for tenant schema: {}", tenantSchema);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\":\"Tenant schema not found\"}");
            } catch (IOException e) {
                log.error("Failed to write error response", e);
            }
            return false;
        }

        Customer customer = customerOpt.get();
        if (customer.getStatus() != Customer.CustomerStatus.ACTIVE) {
            log.warn("Customer for schema {} is not active (status: {})", tenantSchema, customer.getStatus());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\":\"Customer is not active\"}");
            } catch (IOException e) {
                log.error("Failed to write error response", e);
            }
            return false;
        }

        return true;
    }

    /**
     * Check if tenant resolution should be skipped for this request.
     *
     * @param request HTTP request
     * @return true if tenant resolution should be skipped
     */
    private boolean shouldSkipTenantResolution(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/actuator/") ||
               path.equals("/error") ||
               path.equals("/favicon.ico") ||
               path.startsWith("/v3/api-docs/") ||
               path.startsWith("/swagger-ui/") ||
               path.equals("/swagger-ui.html") ||
               path.equals("/scalar") ||
               path.startsWith("/scalar/");
    }

    /**
     * Extract tenant schema from host/subdomain.
     * Example: tenant1.example.com -> tenant1
     *
     * @param host The Host header value
     * @return The tenant schema name, or null if not found
     */
    private String extractSchemaFromHost(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }

        // Remove port if present
        String hostWithoutPort = host.split(":")[0];

        // Split by dot and get first part (subdomain)
        String[] parts = hostWithoutPort.split("\\.");
        if (parts.length > 1) {
            String subdomain = parts[0];
            // Validate subdomain format (alphanumeric and underscores only)
            if (subdomain.matches("^[a-zA-Z0-9_]+$")) {
                return subdomain;
            }
        }

        return null;
    }
}