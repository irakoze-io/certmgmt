package tech.seccertificate.certmgmt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.exception.TenantException;
import tech.seccertificate.certmgmt.exception.TenantNotFoundException;
import tech.seccertificate.certmgmt.config.TenantSchemaInterceptor;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Service for managing tenant context and schema operations.
 * This service provides a convenient way to execute operations within a tenant schema context.
 * With Hibernate multi-tenancy configured, setting the tenant context automatically
 * switches the schema for all subsequent database operations.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Dynamic schema switching based on customer ID or schema name</li>
 *   <li>Schema existence validation</li>
 *   <li>Schema name format validation</li>
 *   <li>Customer status validation (active check)</li>
 *   <li>Thread-safe context management</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {
    
    private final CustomerRepository customerRepository;
    private final TenantSchemaInterceptor schemaInterceptor;
    private final DataSource dataSource;
    
    /**
     * Execute an operation within a tenant context determined by customer ID.
     * 
     * @param customerId The customer ID
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws TenantNotFoundException if customer is not found or has no tenant schema
     */
    public <T> T executeInTenantContext(Long customerId, Function<Object, T> operation) {
        var customer = validateAndGetCustomer(customerId);
        var tenantSchema = customer.getTenantSchema();
        
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
        var customer = validateAndGetCustomer(customerId);
        var tenantSchema = customer.getTenantSchema();
        
        TenantContext.setTenantSchema(tenantSchema);
        log.debug("Set tenant context to schema {} for customer {}", tenantSchema, customerId);
    }
    
    /**
     * Set the tenant context directly using schema name.
     * Note: This method does not validate customer status. Use {@link #setTenantContext(Long)} 
     * if you need customer validation.
     * 
     * @param tenantSchema The tenant schema name
     * @throws IllegalArgumentException if schema name is invalid
     */
    public void setTenantContext(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalArgumentException("Tenant schema cannot be null or empty");
        }
        
        // Validate schema name format
        validateSchemaNameFormat(tenantSchema);
        
        TenantContext.setTenantSchema(tenantSchema);
        log.debug("Set tenant context to schema: {}", tenantSchema);
    }
    
    /**
     * Set the tenant context and validate that the customer is active.
     * 
     * @param customerId The customer ID
     * @param requireActive If true, throws exception if customer is not active
     * @throws TenantNotFoundException if customer is not found or has no tenant schema
     * @throws TenantException if requireActive is true and customer is not active
     */
    public void setTenantContext(Long customerId, boolean requireActive) {
        var customer = validateAndGetCustomer(customerId, requireActive);
        var tenantSchema = customer.getTenantSchema();
        
        TenantContext.setTenantSchema(tenantSchema);
        log.debug("Set tenant context to schema {} for customer {} (active: {})", 
                tenantSchema, customerId, customer.getStatus());
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
        var customer = validateAndGetCustomer(customerId);
        return customer.getTenantSchema();
    }
    
    /**
     * Validate that a tenant schema exists in the database.
     * 
     * @param tenantSchema The schema name to validate
     * @return true if schema exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean validateSchemaExists(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            return false;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            var metaData = connection.getMetaData();
            var schemas = metaData.getSchemas(null, tenantSchema);
            
            var exists = schemas.next();
            log.debug("Schema '{}' exists: {}", tenantSchema, exists);
            return exists;
        } catch (SQLException e) {
            log.error("Error validating schema existence: {}", tenantSchema, e);
            return false;
        }
    }
    
    /**
     * Validate schema name format.
     * Schema names must:
     * - Contain only alphanumeric characters and underscores
     * - Not exceed 75 characters
     * - Not be null or empty
     * 
     * @param tenantSchema The schema name to validate
     * @throws IllegalArgumentException if schema name format is invalid
     */
    public void validateSchemaNameFormat(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalArgumentException("Tenant schema cannot be null or empty");
        }
        
        if (!tenantSchema.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException(
                    "Tenant schema must contain only alphanumeric characters and underscores: " + tenantSchema
            );
        }
        
        if (tenantSchema.length() > 75) {
            throw new IllegalArgumentException("Tenant schema must not exceed 75 characters");
        }
    }
    
    /**
     * Validate that a customer exists, has a tenant schema, and optionally check if active.
     * 
     * @param customerId The customer ID
     * @param requireActive If true, validates that customer is active
     * @return The validated customer
     * @throws TenantNotFoundException if customer is not found or has no tenant schema
     * @throws TenantException if requireActive is true and customer is not active
     */
    public Customer validateAndGetCustomer(Long customerId, boolean requireActive) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new TenantNotFoundException("Customer not found: " + customerId));
        
        var tenantSchema = customer.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new TenantNotFoundException("Customer " + customerId + " does not have a tenant schema configured");
        }
        
        if (requireActive && customer.getStatus() != Customer.CustomerStatus.ACTIVE) {
            throw new TenantException(
                    String.format("Customer %d is not active (status: %s)", customerId, customer.getStatus())
            );
        }
        
        return customer;
    }
    
    /**
     * Validate that a customer exists and has a tenant schema.
     * Does not check customer status.
     * 
     * @param customerId The customer ID
     * @return The validated customer
     * @throws TenantNotFoundException if customer is not found or has no tenant schema
     */
    public Customer validateAndGetCustomer(Long customerId) {
        return validateAndGetCustomer(customerId, false);
    }
    
    /**
     * Check if a customer is active.
     * 
     * @param customerId The customer ID
     * @return true if customer exists and is active, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isCustomerActive(Long customerId) {
        return customerRepository.findById(customerId)
                .map(customer -> customer.getStatus() == Customer.CustomerStatus.ACTIVE)
                .orElse(false);
    }
    
    /**
     * Get customer by ID, throwing TenantNotFoundException if not found.
     * 
     * @param customerId The customer ID
     * @return The customer
     * @throws TenantNotFoundException if customer is not found
     */
    @Transactional(readOnly = true)
    public Customer getCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new TenantNotFoundException("Customer not found: " + customerId));
    }
    
    /**
     * Get customer by tenant schema name.
     * 
     * @param tenantSchema The tenant schema name
     * @return The customer, or empty if not found
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Customer> getCustomerBySchema(String tenantSchema) {
        return customerRepository.findByTenantSchema(tenantSchema);
    }
}
