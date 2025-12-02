package tech.seccertificate.certmgmt.config;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves the current tenant identifier from TenantContext.
 * This is used by Hibernate to determine which schema to use for database operations.
 *
 * @author Ivan-Beaudry Irakoze
 * @Project AuthHub
 * @since Oct 5, 2024
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    /**
     * Default schema to use when no tenant is set (public schema).
     */
    private static final String DEFAULT_TENANT_IDENTIFIER = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantSchema = TenantContext.getTenantSchema();

        // If no tenant is set, use default (public) schema
        // This allows operations on public schema tables (Customer, GlobalAuditLog)
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            return DEFAULT_TENANT_IDENTIFIER;
        }

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
