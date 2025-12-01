package tech.seccertificate.certmgmt.service;

import tech.seccertificate.certmgmt.entity.Customer;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for customer management operations.
 * Handles customer onboarding, tenant schema creation, and validation.
 */
public interface CustomerService {

    /**
     * Create a new customer and set up their tenant schema.
     * This is the main onboarding process that:
     * 1. Validates customer data
     * 2. Creates the customer record
     * 3. Creates the tenant schema using create_tenant_schema() function
     * 4. Sets up initial configuration
     *
     * @param customer The customer entity to create
     * @return The created customer with generated ID
     * @throws IllegalArgumentException if customer data is invalid
     * @throws IllegalStateException if tenant schema creation fails
     */
    Customer onboardCustomer(Customer customer);

    /**
     * Find a customer by ID.
     *
     * @param id The customer ID
     * @return Optional containing the customer if found
     */
    Optional<Customer> findById(Long id);

    /**
     * Find a customer by domain.
     *
     * @param domain The customer domain
     * @return Optional containing the customer if found
     */
    Optional<Customer> findByDomain(String domain);

    /**
     * Find a customer by tenant schema name.
     *
     * @param tenantSchema The tenant schema name
     * @return Optional containing the customer if found
     */
    Optional<Customer> findByTenantSchema(String tenantSchema);

    /**
     * Get all customers.
     *
     * @return List of all customers
     */
    List<Customer> findAll();

    /**
     * Get all active customers.
     *
     * @return List of active customers
     */
    List<Customer> findActiveCustomers();

    /**
     * Update customer information.
     *
     * @param customer The customer with updated information
     * @return The updated customer
     * @throws IllegalArgumentException if customer is null or ID is missing
     */
    Customer updateCustomer(Customer customer);

    /**
     * Update customer status.
     *
     * @param customerId The customer ID
     * @param status The new status
     * @return The updated customer
     * @throws IllegalArgumentException if customer not found
     */
    Customer updateStatus(Long customerId, Customer.CustomerStatus status);

    /**
     * Suspend a customer account.
     *
     * @param customerId The customer ID
     * @return The updated customer with SUSPENDED status
     * @throws IllegalArgumentException if customer not found
     */
    Customer suspendCustomer(Long customerId);

    /**
     * Activate a customer account.
     *
     * @param customerId The customer ID
     * @return The updated customer with ACTIVE status
     * @throws IllegalArgumentException if customer not found
     */
    Customer activateCustomer(Long customerId);

    /**
     * Check if a domain is already in use.
     *
     * @param domain The domain to check
     * @return true if domain is already in use, false otherwise
     */
    boolean isDomainTaken(String domain);

    /**
     * Check if a tenant schema name is already in use.
     *
     * @param tenantSchema The tenant schema name to check
     * @return true if schema name is already in use, false otherwise
     */
    boolean isTenantSchemaTaken(String tenantSchema);

    /**
     * Validate customer data before creation or update.
     *
     * @param customer The customer to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateCustomer(Customer customer);

    /**
     * Generate a unique tenant schema name from customer domain or name.
     *
     * @param domain The customer domain
     * @param name The customer name
     * @return A sanitized tenant schema name
     */
    String generateTenantSchemaName(String domain, String name);
}
