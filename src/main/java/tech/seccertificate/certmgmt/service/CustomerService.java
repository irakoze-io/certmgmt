package tech.seccertificate.certmgmt.service;

import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.model.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing customer operations (tenant onboarding).
 */
@Service
public class CustomerService {
    
    private final CustomerRepository customerRepository;
    
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
    
    /**
     * Create a new customer with API credentials.
     */
    public Customer createCustomer(String name, String email) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name cannot be empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer email cannot be empty");
        }
        
        // Check if email already exists
        Optional<Customer> existing = customerRepository.findByEmail(email);
        if (existing.isPresent()) {
            throw new IllegalStateException("Customer with email " + email + " already exists");
        }
        
        Customer customer = new Customer(name, email);
        customer.setApiKey(generateApiKey());
        customer.setApiSecret(generateApiSecret());
        
        return customerRepository.save(customer);
    }
    
    /**
     * Get customer by ID.
     */
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }
    
    /**
     * Get customer by email.
     */
    public Optional<Customer> getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email);
    }
    
    /**
     * Get customer by API key.
     */
    public Optional<Customer> getCustomerByApiKey(String apiKey) {
        return customerRepository.findByApiKey(apiKey);
    }
    
    /**
     * Update customer information.
     */
    public Customer updateCustomer(Long id, String name, String email) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        
        if (name != null && !name.trim().isEmpty()) {
            customer.setName(name);
        }
        if (email != null && !email.trim().isEmpty()) {
            customer.setEmail(email);
        }
        
        return customerRepository.save(customer);
    }
    
    /**
     * Deactivate a customer.
     */
    public void deactivateCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        customer.setActive(false);
        customerRepository.save(customer);
    }
    
    /**
     * Regenerate API credentials for a customer.
     */
    public Customer regenerateApiCredentials(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        
        customer.setApiKey(generateApiKey());
        customer.setApiSecret(generateApiSecret());
        
        return customerRepository.save(customer);
    }
    
    private String generateApiKey() {
        return "ak_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private String generateApiSecret() {
        return "as_" + UUID.randomUUID().toString().replace("-", "");
    }
}
