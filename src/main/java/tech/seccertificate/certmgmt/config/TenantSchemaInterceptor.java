package tech.seccertificate.certmgmt.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper class to execute operations within a specific tenant schema context.
 * With Hibernate multi-tenancy configured, this mainly manages TenantContext
 * while Hibernate handles the actual schema switching.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
@Component
@Slf4j
public class TenantSchemaInterceptor {
    
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Execute an operation within a specific tenant schema context.
     * The schema is set in TenantContext, and Hibernate's multi-tenancy
     * will automatically use the correct schema for database operations.
     * 
     * @param tenantSchema The tenant schema name
     * @param operation The operation to execute
     * @return The result of the operation
     */
    @Transactional
    public <T> T executeInSchema(String tenantSchema, java.util.function.Function<EntityManager, T> operation) {
        String previousSchema = TenantContext.getTenantSchema();
        try {
            TenantContext.setTenantSchema(tenantSchema);
            log.debug("Executing operation in tenant schema: {}", tenantSchema);
            return operation.apply(entityManager);
        } finally {
            if (previousSchema != null) {
                TenantContext.setTenantSchema(previousSchema);
            } else {
                TenantContext.clear();
            }
        }
    }
}
