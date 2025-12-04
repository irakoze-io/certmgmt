package tech.seccertificate.certmgmt.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import jakarta.annotation.PostConstruct;

/**
 * Resolves the current tenant identifier from TenantContext.
 * This is used by Hibernate to determine which schema to use for database operations.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Dec 4, 2024
 */
@Slf4j
// Note: Bean is created in MultiTenantConfig, not via @Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    /**
     * Default schema to use when no tenant is set (public schema).
     */
    private static final String DEFAULT_TENANT_IDENTIFIER = "public";

    @PostConstruct
    public void init() {
        log.debug("TenantIdentifierResolver initialized");
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantSchema = TenantContext.getTenantSchema();
        log.debug("TenantIdentifierResolver.resolveCurrentTenantIdentifier() called. Current tenantSchema: {}", tenantSchema);

        // If no tenant is set, use default (public) schema
        // This allows operations on public schema tables (Customer, GlobalAuditLog)
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            log.debug("No tenant schema set in TenantContext, returning default: {}", DEFAULT_TENANT_IDENTIFIER);
            return DEFAULT_TENANT_IDENTIFIER;
        }

        log.debug("Returning tenant schema: {}", tenantSchema);
        return tenantSchema;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Validate that existing sessions match the current tenant identifier
        // Return false to indicate that we don't want to validate existing sessions
        // This allows for dynamic tenant switching within the same session
        return false;
    }

    @Override
    public boolean isRoot(Object o) {
        // var tenantIdentifier = resolveCurrentTenantIdentifier();
        if (!(o instanceof String tenantIdentifier)) {
            return false;
        }
        return DEFAULT_TENANT_IDENTIFIER.equals(tenantIdentifier);
    }
}
