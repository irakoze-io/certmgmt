package tech.seccertificate.certmgmt.config;

/**
 * Thread-local context holder for current tenant schema.
 * This allows the application to know which tenant schema to use for database operations.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
public class TenantContext {
    
    private static final ThreadLocal<String> TENANT_SCHEMA = new ThreadLocal<>();

    public static void setTenantSchema(String schemaName) {
        TENANT_SCHEMA.set(schemaName);
    }

    public static String getTenantSchema() {
        return TENANT_SCHEMA.get();
    }

    public static void clear() {
        TENANT_SCHEMA.remove();
    }

    public static boolean hasTenantSchema() {
        return TENANT_SCHEMA.get() != null;
    }
}
