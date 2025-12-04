package tech.seccertificate.certmgmt.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.seccertificate.certmgmt.dto.Response;
import tech.seccertificate.certmgmt.dto.customer.CreateCustomerRequest;
import tech.seccertificate.certmgmt.dto.customer.CustomerResponse;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.exception.CustomerNotFoundException;
import tech.seccertificate.certmgmt.service.CustomerService;

import java.net.URI;

/**
 * REST controller for customer management operations.
 * Handles customer onboarding and customer information retrieval.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/customers - Create a new customer (onboarding)</li>
 *   <li>GET /api/customers/{id} - Get customer by ID</li>
 *   <li>GET /api/customers - Get all customers</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Create a new customer (onboarding).
     * This endpoint creates a customer and sets up their tenant schema.
     * 
     * @param request The customer creation request
     * @return Created customer response with 201 status
     */
    @PostMapping
    public ResponseEntity<Response<CustomerResponse>> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        log.info("Creating customer: {}", request.getName());

        var customer = mapToEntity(request);
        var createdCustomer = customerService.onboardCustomer(customer);

        var response = mapToDTO(createdCustomer);
        var unifiedResponse = Response.success(
                "Customer created successfully",
                response
        );

        var location = URI.create("/api/customers/" + createdCustomer.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(unifiedResponse);
    }

    /**
     * Get customer by ID.
     * 
     * @param id The customer ID
     * @return Customer response with 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Response<CustomerResponse>> getCustomer(@PathVariable Long id) {
        log.debug("Getting customer with ID: {}", id);
        
        var customer = customerService.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer with ID " + id + " not found"));
        
        var response = mapToDTO(customer);
        var unifiedResponse = Response.success(
                "Customer retrieved successfully",
                response
        );
        
        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Get all customers.
     * 
     * @return List of customer responses with 200 status
     */
    @GetMapping
    public ResponseEntity<Response<java.util.List<CustomerResponse>>> getAllCustomers() {
        log.debug("Getting all customers");
        
        var customers = customerService.findAll();
        var customerDTOs = customers.stream()
                .map(this::mapToDTO)
                .toList();
        
        var unifiedResponse = Response.success(
                "Customers retrieved successfully",
                customerDTOs
        );
        
        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Map CreateCustomerRequest to Customer entity.
     */
    private Customer mapToEntity(CreateCustomerRequest request) {
        return Customer.builder()
                .name(request.getName())
                .domain(request.getDomain())
                .tenantSchema(request.getTenantSchema()) // Optional, will be generated if null
                .maxUsers(request.getMaxUsers())
                .maxCertificatesPerMonth(request.getMaxCertificatesPerMonth())
                .build();
    }

    /**
     * Map Customer entity to CustomerResponse.
     */
    private CustomerResponse mapToDTO(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .tenantSchema(customer.getTenantSchema())
                .name(customer.getName())
                .domain(customer.getDomain())
                .status(customer.getStatus())
                .maxUsers(customer.getMaxUsers())
                .maxCertificatesPerMonth(customer.getMaxCertificatesPerMonth())
                .createdDate(customer.getCreatedDate())
                .updatedDate(customer.getUpdatedDate())
                .build();
    }
}
