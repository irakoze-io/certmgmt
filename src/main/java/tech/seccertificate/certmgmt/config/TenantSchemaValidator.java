package tech.seccertificate.certmgmt.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tenant schema validation component.
 * With Hibernate multi-tenancy configured, schema switching is handled automatically.
 * This component provides validation and logging utilities.
 * <p>
 * Note: AspectJ-based aspect removed as it requires additional dependencies.
 * Schema switching is handled by TenantConnectionProvider and TenantIdentifierResolver.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
@Slf4j
@Component
public class TenantSchemaValidator {

    /**
     * Validate that a tenant schema is set for tenant-specific operations.
     * 
     * @param operationName The name of the operation being performed
     * @throws IllegalStateException if no tenant schema is set
     */
    public void validateTenantSchema(String operationName) {
        String tenantSchema = TenantContext.getTenantSchema();
        
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            String message = String.format("No tenant schema set for operation: %s. " +
                    "Set tenant context using TenantService.setTenantContext() or " +
                    "provide X-Tenant-Id or X-Tenant-Schema header in HTTP request.", operationName);
            log.error(message);
            throw new IllegalStateException(message);
        }
        
        log.debug("Validated tenant schema {} for operation: {}", tenantSchema, operationName);
    }

    /**
     * Check if a tenant schema is set (non-strict validation).
     * 
     * @return true if tenant schema is set, false otherwise
     */
    public boolean hasTenantSchema() {
        return TenantContext.hasTenantSchema();
    }
}
