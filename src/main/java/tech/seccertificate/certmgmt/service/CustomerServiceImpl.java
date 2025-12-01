package tech.seccertificate.certmgmt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.exception.CustomerNotFoundException;
import tech.seccertificate.certmgmt.exception.TenantSchemaCreationException;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Implementation of CustomerService.
 * Handles customer onboarding, tenant schema creation, and validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final DataSource dataSource;

    @Override
    @Transactional
    public Customer onboardCustomer(Customer customer) {
        log.info("Starting customer onboarding for: {}", customer.getName());

        validateCustomer(customer);

        if (customer.getTenantSchema() == null || customer.getTenantSchema().isEmpty()) {
            String tenantSchema = generateTenantSchemaName(customer.getDomain(), customer.getName());
            customer.setTenantSchema(tenantSchema);
            log.debug("Generated tenant schema name: {}", tenantSchema);
        }

        if (isTenantSchemaTaken(customer.getTenantSchema())) {
            throw new IllegalArgumentException(
                    String.format("Tenant schema '%s' is already in use", customer.getTenantSchema())
            );
        }

        if (isDomainTaken(customer.getDomain())) {
            throw new IllegalArgumentException(
                    String.format("Domain '%s' is already in use", customer.getDomain())
            );
        }

        if (customer.getStatus() == null) {
            customer.setStatus(Customer.CustomerStatus.TRIAL);
        }
        if (customer.getMaxUsers() == null) {
            customer.setMaxUsers(10);
        }
        if (customer.getMaxCertificatesPerMonth() == null) {
            customer.setMaxCertificatesPerMonth(1_000);
        }
        if (customer.getSettings() == null || customer.getSettings().isEmpty()) {
            customer.setSettings("{}");
        }

        Customer savedCustomer;
        try {
            savedCustomer = customerRepository.save(customer);
            log.info("Customer saved with ID: {}", savedCustomer.getId());
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to save customer due to data integrity violation", e);
            throw new IllegalArgumentException("Customer data violates constraints: " + e.getMessage(), e);
        }

        try {
            createTenantSchema(savedCustomer.getTenantSchema());
            log.info("Tenant schema '{}' created successfully for customer {}",
                    savedCustomer.getTenantSchema(), savedCustomer.getId());
        } catch (Exception e) {
            log.error("Failed to create tenant schema '{}' for customer {}",
                    savedCustomer.getTenantSchema(), savedCustomer.getId(), e);
            // Rollback customer creation by deleting it
            try {
                customerRepository.delete(savedCustomer);
                log.info("Rolled back customer {} creation due to schema creation failure",
                        savedCustomer.getId());
            } catch (Exception rollbackException) {
                log.error("Failed to rollback customer {} creation", savedCustomer.getId(), rollbackException);
            }

            var exceptionMessage = String.format("Failed to create tenant schema '%s': %s",
                    savedCustomer.getTenantSchema(), e.getMessage());

            throw new TenantSchemaCreationException(exceptionMessage, e);
        }

        log.info("Customer onboarding completed successfully for customer ID: {}", savedCustomer.getId());
        return savedCustomer;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByDomain(String domain) {
        return customerRepository.findByDomain(domain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByTenantSchema(String tenantSchema) {
        return customerRepository.findByTenantSchema(tenantSchema);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findActiveCustomers() {
        return customerRepository.findByStatus(Customer.CustomerStatus.ACTIVE);
    }

    @Override
    @Transactional
    public Customer updateCustomer(Customer customer) {
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("Customer and customer ID must not be null");
        }

        var existingCustomer = customerRepository.findById(customer.getId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customer.getId()));

        validateCustomer(customer);

        // Since the tenant schema is immutable, prevent changing it if it's already created
        if (customer.getTenantSchema() != null &&
                !customer.getTenantSchema().equals(existingCustomer.getTenantSchema())) {
            throw new IllegalArgumentException("Tenant schema cannot be changed after creation");
        }

        if (customer.getDomain() != null &&
                !customer.getDomain().equalsIgnoreCase(existingCustomer.getDomain())) {
            if (isDomainTaken(customer.getDomain())) {
                throw new IllegalArgumentException("Domain is already in use: " + customer.getDomain());
            }
        }

        // Update allowed fields
        if (customer.getName() != null) {
            existingCustomer.setName(customer.getName());
        }
        if (customer.getDomain() != null) {
            existingCustomer.setDomain(customer.getDomain());
        }
        if (customer.getSettings() != null) {
            existingCustomer.setSettings(customer.getSettings());
        }
        if (customer.getStatus() != null) {
            existingCustomer.setStatus(customer.getStatus());
        }
        if (customer.getMaxUsers() != null) {
            existingCustomer.setMaxUsers(customer.getMaxUsers());
        }
        if (customer.getMaxCertificatesPerMonth() != null) {
            existingCustomer.setMaxCertificatesPerMonth(customer.getMaxCertificatesPerMonth());
        }

        return customerRepository.save(existingCustomer);
    }

    @Override
    @Transactional
    public Customer updateStatus(Long customerId, Customer.CustomerStatus status) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));

        customer.setStatus(status);
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer suspendCustomer(Long customerId) {
        return updateStatus(customerId, Customer.CustomerStatus.SUSPENDED);
    }

    @Override
    @Transactional
    public Customer activateCustomer(Long customerId) {
        return updateStatus(customerId, Customer.CustomerStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDomainTaken(String domain) {
        if (domain == null || domain.isEmpty()) {
            return false;
        }
        return customerRepository.existsByDomain(domain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTenantSchemaTaken(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            return false;
        }
        return customerRepository.existsByTenantSchema(tenantSchema);
    }

    @Override
    public void validateCustomer(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer cannot be null");
        }

        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }

        if (customer.getName().length() > 75) {
            throw new IllegalArgumentException("Customer name must not exceed 75 characters");
        }

        if (customer.getDomain() == null || customer.getDomain().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer domain is required");
        }

        // Basic domain name validation
        var domain = customer.getDomain().trim().toLowerCase();
        if (!domain.matches("^[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?)*$")) {
            throw new IllegalArgumentException("Invalid domain format: " + customer.getDomain());
        }

        if (customer.getTenantSchema() != null && !customer.getTenantSchema().isEmpty()) {
            var schema = customer.getTenantSchema();
            if (!schema.matches("^[a-zA-Z0-9_]+$")) {
                throw new IllegalArgumentException(
                        "Tenant schema must contain only alphanumeric characters and underscores: " + schema
                );
            }
            if (schema.length() > 75) {
                throw new IllegalArgumentException("Tenant schema must not exceed 75 characters");
            }
        }

        // Validate max users
        if (customer.getMaxUsers() != null && customer.getMaxUsers() < 1) {
            throw new IllegalArgumentException("Max users must be at least 1");
        }

        // Validate max certificates per month
        if (customer.getMaxCertificatesPerMonth() != null && customer.getMaxCertificatesPerMonth() < 1) {
            throw new IllegalArgumentException("Max certificates per month must be at least 1");
        }
    }

    @Override
    public String generateTenantSchemaName(String domain, String name) {
        var base = domain != null ? domain.replaceAll("[^a-zA-Z0-9]", "_") : "";

        if (base.isEmpty() || base.length() < 3) {
            base = name != null ? name.replaceAll("[^a-zA-Z0-9]", "_") : "tenant";
        }

        if (!base.matches("^[a-zA-Z].*")) {
            base = "tenant_" + base;
        }

        if (base.length() > 75) {
            base = base.substring(0, 75);
        }

        var candidate = base;
        var suffix = 1;
        while (isTenantSchemaTaken(candidate)) {
            var suffixStr = "_" + suffix;
            var maxLength = 75 - suffixStr.length();
            candidate = base.substring(0, Math.min(base.length(), maxLength)) + suffixStr;
            suffix++;
        }

        return candidate.toLowerCase();
    }

    private void createTenantSchema(String schemaName) {
        log.debug("Creating tenant schema: {}", schemaName);

        try (var connection = dataSource.getConnection()) {
            var sanitized = sanitizeSchemaName(schemaName);

            try (var statement = connection.prepareStatement("SELECT create_tenant_schema(?)")) {
                statement.setString(1, sanitized);
                statement.execute();
                log.info("Successfully created tenant schema: {}", sanitized);
            }
        } catch (SQLException e) {
            log.error("SQL error while creating tenant schema: {}", schemaName, e);
            throw new TenantSchemaCreationException(
                    String.format("Failed to create tenant schema '%s': %s", schemaName, e.getMessage()), e
            );
        } catch (Exception e) {
            log.error("Unexpected error while creating tenant schema: {}", schemaName, e);
            throw new TenantSchemaCreationException(
                    String.format("Unexpected error creating tenant schema '%s': %s", schemaName, e.getMessage()), e
            );
        }
    }

    private String sanitizeSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }

        var sanitized = schemaName.replaceAll("[^a-zA-Z0-9_]", "");

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Schema name must contain at least one valid character");
        }

        return sanitized;
    }
}
