package tech.seccertificate.certmgmt.dto.customer;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for creating a new customer.
 * Used for customer onboarding API endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateCustomerRequest {

    /**
     * Customer name (required).
     * Must be between 1 and 75 characters.
     */
    @NotBlank(message = "Customer name is required")
    @Size(min = 1, max = 75, message = "Customer name must be between 1 and 75 characters")
    private String name;

    /**
     * Customer domain (required, must be unique).
     * Used for tenant identification.
     */
    @NotBlank(message = "Domain is required")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]*(\\.[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]*)*$",
            message = "Domain must be a valid domain name")
    private String domain;

    /**
     * Optional tenant schema name.
     * If not provided, will be auto-generated from domain/name.
     * Must contain only alphanumeric characters and underscores, max 75 characters.
     */
    @Size(max = 75, message = "Tenant schema must not exceed 75 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "Tenant schema must contain only alphanumeric characters and underscores")
    private String tenantSchema;

    /**
     * Customer settings as key-value pairs (optional).
     */
    private Map<String, Object> settings;

    /**
     * Maximum number of users allowed (optional, defaults to 10).
     * Must be a positive integer.
     */
    @Min(value = 1, message = "Max users must be at least 1")
    @Max(value = 10000, message = "Max users must not exceed 10000")
    private Integer maxUsers;

    /**
     * Maximum certificates per month (optional, defaults to 1000).
     * Must be a positive integer.
     */
    @Min(value = 1, message = "Max certificates per month must be at least 1")
    @Max(value = 1000000, message = "Max certificates per month must not exceed 1000000")
    private Integer maxCertificatesPerMonth;
}
