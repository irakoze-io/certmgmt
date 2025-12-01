package tech.seccertificate.certmgmt.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

/**
 * Interceptor to extract tenant information from HTTP requests and set tenant context.
 * This ensures that all repository operations use the correct tenant schema.
 * 
 * Tenant can be identified via:
 * 1. X-Tenant-Id header (customer ID)
 * 2. X-Tenant-Schema header (schema name directly)
 * 3. Subdomain (extracted from Host header)
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantRequestInterceptor implements HandlerInterceptor {

    private final CustomerRepository customerRepository;

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String TENANT_SCHEMA_HEADER = "X-Tenant-Schema";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Try to get tenant from header first
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        String tenantSchema = request.getHeader(TENANT_SCHEMA_HEADER);

        if (tenantId != null && !tenantId.isEmpty()) {
            // Resolve schema from customer ID
            try {
                Long customerId = Long.parseLong(tenantId);
                Customer customer = customerRepository.findById(customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
                
                String schema = customer.getTenantSchema();
                if (schema == null || schema.isEmpty()) {
                    log.warn("Customer {} does not have a tenant schema configured", customerId);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return false;
                }
                
                TenantContext.setTenantSchema(schema);
                log.debug("Set tenant schema {} from customer ID {}", schema, customerId);
                return true;
            } catch (NumberFormatException e) {
                log.warn("Invalid tenant ID format: {}", tenantId);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            } catch (IllegalArgumentException e) {
                log.warn("Failed to resolve tenant: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        } else if (tenantSchema != null && !tenantSchema.isEmpty()) {
            // Use schema directly
            TenantContext.setTenantSchema(tenantSchema);
            log.debug("Set tenant schema directly: {}", tenantSchema);
            return true;
        } else {
            // Try to extract from subdomain
            String host = request.getHeader("Host");
            if (host != null) {
                String schema = extractSchemaFromHost(host);
                if (schema != null && !schema.isEmpty()) {
                    TenantContext.setTenantSchema(schema);
                    log.debug("Set tenant schema from subdomain: {}", schema);
                    return true;
                }
            }
        }

        // No tenant specified - operations will use public schema
        // This is acceptable for public schema operations (Customer, GlobalAuditLog)
        log.debug("No tenant specified, using default (public) schema");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Always clear tenant context after request to prevent memory leaks
        TenantContext.clear();
        log.debug("Cleared tenant context after request");
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
