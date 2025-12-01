package tech.seccertificate.certmgmt.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interceptor/Helper class to manage tenant schema switching for JPA operations.
 * This ensures that all database operations use the correct tenant schema.
 */
@Component
public class TenantSchemaInterceptor {
    
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public <T> T executeInSchema(String tenantSchema, java.util.function.Function<EntityManager, T> operation) {
        String previousSchema = TenantContext.getTenantSchema();
        try {
            setSchema(tenantSchema);
            TenantContext.setTenantSchema(tenantSchema);
            return operation.apply(entityManager);
        } finally {
            if (previousSchema != null) {
                setSchema(previousSchema);
                TenantContext.setTenantSchema(previousSchema);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void setSchema(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return;
        }
        
        // Sanitize schema name
        String sanitized = sanitizeSchemaName(schemaName);
        
        // Set search_path for PostgreSQL
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + sanitized + ", public");
            }
        });
    }
    
    /**
     * Sanitize schema name to prevent SQL injection.
     * @param schemaName The schema name to sanitize
     * @return Sanitized schema name
     */
    private String sanitizeSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }
        // Only allow alphanumeric characters and underscores
        String sanitized = schemaName.replaceAll("[^a-zA-Z0-9_]", "");
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Schema name must contain at least one valid character");
        }
        return sanitized;
    }
}
