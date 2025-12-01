package tech.seccertificate.certmgmt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.config.TenantSchemaInterceptor;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.util.function.Function;

/**
 * Service for managing tenant context and schema operations.
 * This service provides a convenient way to execute operations within a tenant schema context.
 */
@Service
@RequiredArgsConstructor
public class TenantService {
    
    private final CustomerRepository customerRepository;
    private final TenantSchemaInterceptor schemaInterceptor;
    
    public <T> T executeInTenantContext(Long customerId, Function<Object, T> operation) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        
        String tenantSchema = customer.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalStateException("Customer " + customerId + " does not have a tenant schema configured");
        }
        
        return schemaInterceptor.executeInSchema(tenantSchema, em -> operation.apply(null));
    }
    
    public void setTenantContext(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        
        String tenantSchema = customer.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalStateException("Customer " + customerId + " does not have a tenant schema configured");
        }
        
        TenantContext.setTenantSchema(tenantSchema);
    }
    
    public void setTenantContext(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalArgumentException("Tenant schema cannot be null or empty");
        }
        TenantContext.setTenantSchema(tenantSchema);
    }

    public void clearTenantContext() {
        TenantContext.clear();
    }

    public String getCurrentTenantSchema() {
        return TenantContext.getTenantSchema();
    }
}
