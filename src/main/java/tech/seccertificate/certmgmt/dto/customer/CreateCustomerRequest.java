package tech.seccertificate.certmgmt.dto.customer;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request to create a new customer (onboarding)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateCustomerRequest {

    @NotBlank(message = "Customer name is required")
    @Size(min = 1, max = 75, message = "Customer name must be between 1 and 75 characters")
    @Schema(description = "Customer name", example = "Acme Corporation", required = true, minLength = 1, maxLength = 75)
    private String name;

    @NotBlank(message = "Domain is required")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]*(\\.[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]*)*$",
            message = "Domain must be a valid domain name")
    @Schema(description = "Customer domain (must be unique), used for tenant identification", 
            example = "acme.example.com", required = true)
    private String domain;

    @Size(max = 75, message = "Tenant schema must not exceed 75 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "Tenant schema must contain only alphanumeric characters and underscores")
    @Schema(description = "Optional tenant schema name. If not provided, will be auto-generated from domain/name. " +
            "Must contain only alphanumeric characters and underscores, max 75 characters", 
            example = "acme_corp", maxLength = 75)
    private String tenantSchema;

    @Schema(description = "Customer settings as key-value pairs", 
            example = "{\"theme\": \"dark\", \"language\": \"en\"}")
    private Map<String, Object> settings;

    @Min(value = 1, message = "Max users must be at least 1")
    @Max(value = 10000, message = "Max users must not exceed 10000")
    @Schema(description = "Maximum number of users allowed (defaults to 10)", example = "100", minimum = "1", maximum = "10000")
    private Integer maxUsers;

    @Min(value = 1, message = "Max certificates per month must be at least 1")
    @Max(value = 1000000, message = "Max certificates per month must not exceed 1000000")
    @Schema(description = "Maximum certificates per month (defaults to 1000)", example = "10000", minimum = "1", maximum = "1000000")
    private Integer maxCertificatesPerMonth;
}
