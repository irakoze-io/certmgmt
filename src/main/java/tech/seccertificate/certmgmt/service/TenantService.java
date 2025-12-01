package tech.seccertificate.certmgmt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.exception.TenantNotFoundException;
import tech.seccertificate.certmgmt.config.TenantSchemaInterceptor;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.util.function.Function;

/**
 * Service for managing tenant context and schema operations.
 * This service provides a convenient way to execute operations within a tenant schema context.
 * With Hibernate multi-tenancy configured, setting the tenant context automatically
 * switches the schema for all subsequent database operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {
    
    private final CustomerRepository customerRepository;
    private final TenantSchemaInterceptor schemaInterceptor;
    
    /**
     * Execute an operation within a tenant context determined by customer ID.
     * 
     * @param customerId The customer ID
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws TenantNotFoundException if customer is not found or has no tenant schema
     */
    public <T> T executeInTenantContext(Long customerId, Function<Object, T> operation) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new TenantNotFoundException("Customer not found: " + customerId));
        
        String tenantSchema = customer.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new TenantNotFoundException("Customer " + customerId + " does not have a tenant schema configured");
        }
        
        log.debug("Executing operation in tenant context for customer {} (schema: {})", customerId, tenantSchema);
        return schemaInterceptor.executeInSchema(tenantSchema, em -> operation.apply(null));
    }
    
    /**
     * Set the tenant context for the current thread based on customer ID.
     * All subsequent database operations will use the tenant's schema.
     * 
     * @param customerId The customer ID
     * @throws TenantNotFoundException if customer is not found or has no tenant schema
     */
    public void setTenantContext(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new TenantNotFoundException("Customer not found: " + customerId));
        
        String tenantSchema = customer.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new TenantNotFoundException("Customer " + customerId + " does not have a tenant schema configured");
        }
        
        TenantContext.setTenantSchema(tenantSchema);
        log.debug("Set tenant context to schema {} for customer {}", tenantSchema, customerId);
    }
    
    /**
     * Set the tenant context directly using schema name.
     * 
     * @param tenantSchema The tenant schema name
     * @throws IllegalArgumentException if schema name is invalid
     */
    public void setTenantContext(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalArgumentException("Tenant schema cannot be null or empty");
        }
        TenantContext.setTenantSchema(tenantSchema);
        log.debug("Set tenant context to schema: {}", tenantSchema);
    }

    /**
     * Clear the tenant context for the current thread.
     * After clearing, operations will use the default (public) schema.
     */
    public void clearTenantContext() {
        TenantContext.clear();
        log.debug("Cleared tenant context");
    }

    /**
     * Get the current tenant schema for the current thread.
     * 
     * @return The current tenant schema name, or null if not set
     */
    public String getCurrentTenantSchema() {
        return TenantContext.getTenantSchema();
    }

    /**
     * Check if a tenant context is currently set.
     * 
     * @return true if tenant context is set, false otherwise
     */
    public boolean hasTenantContext() {
        return TenantContext.hasTenantSchema();
    }

    /**
     * Resolve tenant schema from customer ID.
     * 
     * @param customerId The customer ID
     * @return The tenant schema name
     * @throws TenantNotFoundException if customer is not found or has no tenant schema
     */
    public String resolveTenantSchema(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new TenantNotFoundException("Customer not found: " + customerId));
        
        String tenantSchema = customer.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new TenantNotFoundException("Customer " + customerId + " does not have a tenant schema configured");
        }
        
        return tenantSchema;
    }
}
