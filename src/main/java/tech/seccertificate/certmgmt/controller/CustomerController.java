package tech.seccertificate.certmgmt.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.seccertificate.certmgmt.dto.Response;
import tech.seccertificate.certmgmt.dto.customer.CreateCustomerRequest;
import tech.seccertificate.certmgmt.dto.customer.CustomerResponse;
import tech.seccertificate.certmgmt.dto.template.TemplateVersionResponse;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.exception.CustomerNotFoundException;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.TemplateService;

import java.net.URI;
import java.util.List;
import java.util.Map;

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
@Tag(name = "Customers", description = "Customer management operations including onboarding and retrieval")
public class CustomerController {

    private final CustomerService customerService;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    /**
     * Create a new customer (onboarding).
     * This endpoint creates a customer and sets up their tenant schema.
     *
     * @param request The customer creation request
     * @return Created customer response with 201 status
     */
    @Operation(
            summary = "Create a new customer",
            description = "Creates a new customer and sets up their tenant schema. " +
                    "This is the onboarding endpoint that initializes a new tenant."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Customer created successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class),
                            examples = @ExampleObject(
                                    name = "Success",
                                    summary = "Customer Created",
                                    value = """
                                            {
                                              "success": true,
                                              "message": "Customer created successfully",
                                              "data": {
                                                "id": 1,
                                                "name": "Acme Corp",
                                                "domain": "acme.com",
                                                "tenantSchema": "acme_corp",
                                                "status": "TRIAL",
                                                "maxUsers": 10,
                                                "maxCertificatesPerMonth": 100
                                              },
                                              "details": null
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error or bad request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)
                    )
            )
    })
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
    @Operation(
            summary = "Get customer by ID",
            description = "Retrieves a customer by their unique identifier"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Customer retrieved successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Customer not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)
                    )
            )
    })
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
     * Retrieves all template versions associated with the specified customer ID.
     *
     * @param customerId the ID of the customer whose template versions are to be retrieved
     * @return a ResponseEntity containing a Response object with a list of TemplateVersion objects
     */
    @Operation(
            summary = "Get all template versions for a customer",
            description = "Retrieves all template versions associated with the specified customer ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "Templates versions loaded successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)))
    })
    @GetMapping("/{customerId}/template-versions")
    public ResponseEntity<Response<List<TemplateVersionResponse>>>
    getAllTemplateVersionsByCustomerId(@PathVariable Long customerId) {
        var responses = templateService
                .findVersionsByCustomerId(customerId)
                .stream()
                .map(this::mapToDTO)
                .toList();

        log.debug("Retrieved {} template versions for customer {}", responses.size(), customerId);
        return ResponseEntity
                .ok(Response.success("Template versions retrieved successfully", responses));
    }

    /**
     * Get all customers.
     *
     * <p>This endpoint is restricted to ADMIN users only.
     * Regular users cannot access this endpoint as it returns
     * all customers across all tenants.
     *
     * @return List of customer responses with 200 status
     */
    @Operation(
            summary = "Get all customers",
            description = "Retrieves a list of all customers in the system. " +
                    "Restricted to SUPER_ADMIN users only."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Customers retrieved successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied - ADMIN role required",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)
                    )
            )
    })
    @PreAuthorize("hasRole('ADMIN')")
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

    /**
     * Map TemplateVersion entity to TemplateVersionResponse.
     */
    private TemplateVersionResponse mapToDTO(TemplateVersion version) {
        Map<String, Object> fieldSchema = null;
        if (version.getFieldSchema() != null && !version.getFieldSchema().isEmpty()) {
            try {
                fieldSchema = objectMapper.readValue(version.getFieldSchema(), new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("Failed to parse fieldSchema JSON for version {}: {}", version.getId(), e.getMessage());
                fieldSchema = Map.of();
            }
        }

        Map<String, Object> settings = null;
        if (version.getSettings() != null && !version.getSettings().isEmpty()) {
            try {
                settings = objectMapper.readValue(version.getSettings(), new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("Failed to parse settings JSON for version {}: {}", version.getId(), e.getMessage());
                settings = Map.of();
            }
        }

        String createdByName = null;
        if (version.getCreatedByUser() != null) {
            var firstName = version.getCreatedByUser().getFirstName() != null ?
                    version.getCreatedByUser().getFirstName() : "";
            var lastName = version.getCreatedByUser().getLastName() != null ?
                    version.getCreatedByUser().getLastName() : "";
            createdByName = (firstName + " " + lastName).trim();
        }

        return TemplateVersionResponse.builder()
                .id(version.getId())
                .templateId(version.getTemplate().getId())
                .version(version.getVersion())
                .htmlContent(version.getHtmlContent())
                .fieldSchema(fieldSchema)
                .cssStyles(version.getCssStyles())
                .settings(settings)
                .status(version.getStatus())
                .createdBy(version.getCreatedBy())
                .createdByName(createdByName)
                .createdAt(version.getCreatedAt())
                .build();
    }
}
