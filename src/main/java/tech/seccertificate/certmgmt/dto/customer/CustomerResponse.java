package tech.seccertificate.certmgmt.dto.customer;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.entity.Customer;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for Customer entity.
 * Used for API responses to expose customer information.
 */
@Schema(description = "Customer response containing customer information")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponse {

    @Schema(description = "Customer unique identifier", example = "1")
    private Long id;

    @Schema(description = "Tenant schema name (read-only, set during creation)", example = "acme_corp")
    private String tenantSchema;

    @Schema(description = "Customer name", example = "Acme Corporation")
    private String name;

    @Schema(description = "Customer domain (unique identifier)", example = "acme.example.com")
    private String domain;

    @Schema(description = "Customer settings as key-value pairs", example = "{\"theme\": \"dark\", \"language\": \"en\"}")
    private Map<String, Object> settings;

    @Schema(description = "Customer status", example = "ACTIVE", allowableValues = {"ACTIVE", "SUSPENDED", "TRIAL", "CANCELLED"})
    private Customer.CustomerStatus status;

    @Schema(description = "Maximum number of users allowed", example = "100")
    private Integer maxUsers;

    @Schema(description = "Maximum certificates per month", example = "10000")
    private Integer maxCertificatesPerMonth;

    @Schema(description = "Date when customer was created", example = "2024-01-15T10:30:00")
    private LocalDateTime createdDate;

    @Schema(description = "Date when customer was last updated", example = "2024-01-20T14:45:00")
    private LocalDateTime updatedDate;
}
