package tech.seccertificate.certmgmt.controller;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.config.TenantResolutionFilter;
import tech.seccertificate.certmgmt.dto.Response;
import tech.seccertificate.certmgmt.dto.user.LoginRequest;
import tech.seccertificate.certmgmt.dto.user.LoginResponse;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.User;
import tech.seccertificate.certmgmt.repository.CustomerRepository;
import tech.seccertificate.certmgmt.repository.UserRepository;
import tech.seccertificate.certmgmt.security.TenantUserDetails;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and user management operations")
public class AuthController {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Principal principal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> userInfo = Map.of(
            "username", principal != null ? principal.getName() : "anonymous",
            "authenticated", authentication != null && authentication.isAuthenticated()
        );

        if (authentication != null && authentication.getPrincipal() instanceof TenantUserDetails userDetails) {
            userInfo = Map.of(
                "username", userDetails.getUsername(),
                "email", userDetails.getEmail(),
                "userId", userDetails.getUserId().toString(),
                "customerId", userDetails.getCustomerId(),
                "role", userDetails.getRole().name(),
                "tenantSchema", userDetails.getTenantSchema(),
                "firstName", userDetails.getFirstName() != null ? userDetails.getFirstName() : "",
                "lastName", userDetails.getLastName() != null ? userDetails.getLastName() : "",
                "authenticated", true
            );
        }

        return ResponseEntity.ok(userInfo);
    }

    @Operation(
        summary = "Create a new user",
        description = "Creates a new user in the tenant specified by X-Tenant-Id header. " +
                      "The user will be created in the tenant's schema. Email must be unique within the tenant."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Response.class),
                examples = @ExampleObject(
                    name = "Success",
                    summary = "User Created",
                    value = """
                        {
                          "success": true,
                          "message": "User created successfully",
                          "data": {
                            "id": "550e8400-e29b-41d4-a716-446655440000",
                            "customerId": 1,
                            "email": "john.doe@example.com",
                            "keycloakId": "550e8400-e29b-41d4-a716-446655440000",
                            "firstName": "John",
                            "lastName": "Doe",
                            "role": "VIEWER",
                            "active": true
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error or bad request (e.g., email already exists, tenant context not set)"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    @PostMapping("/users")
    public ResponseEntity<Response<LoginResponse>> createUser(
            @RequestHeader(value = TenantResolutionFilter.TENANT_ID_HEADER, required = false) String tenantIdHeader,
            @Valid @RequestBody LoginRequest request) {

        log.debug("Creating user with email: {} for tenant: {}", request.getEmail(), tenantIdHeader);

        // Get tenant schema from context (set by TenantResolutionFilter)
        String tenantSchema = TenantContext.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalStateException(
                "Tenant context not set. Please provide X-Tenant-Id header."
            );
        }

        // Get customer to retrieve customerId
        Customer customer = customerRepository.findByTenantSchema(tenantSchema)
            .orElseThrow(() -> new IllegalStateException(
                "Customer not found for tenant schema: " + tenantSchema
            ));

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                "User with email " + request.getEmail() + " already exists in this tenant"
            );
        }

        // Generate keycloak_id if not provided
        String keycloakId = request.getKeycloakId();
        if (keycloakId == null || keycloakId.isEmpty()) {
            keycloakId = UUID.randomUUID().toString();
        } else {
            // Check if keycloak_id already exists
            if (userRepository.existsByKeycloakId(keycloakId)) {
                throw new IllegalArgumentException(
                    "User with keycloak_id " + keycloakId + " already exists"
                );
            }
        }

        // Encode password
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Build user entity
        User user = User.builder()
                .customerId(customer.getId())
                .email(request.getEmail())
                .keycloakId(keycloakId)
                .password(encodedPassword)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole() != null ? request.getRole() : User.UserRole.VIEWER)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        // Save user
        User savedUser = userRepository.save(user);
        log.info("Created user {} with ID {} for customer {}", savedUser.getEmail(), savedUser.getId(), customer.getId());

        LoginResponse loginResponse = LoginResponse.from(savedUser);
        var response = Response.success(
            "User created successfully",
                loginResponse
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
