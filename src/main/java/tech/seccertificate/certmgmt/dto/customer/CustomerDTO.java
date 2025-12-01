package tech.seccertificate.certmgmt.dto.customer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.entity.Customer;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Data Transfer Object for Customer entity.
 * Used for API responses to expose customer information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerDTO {

    /**
     * Customer unique identifier.
     */
    private Long id;

    /**
     * Tenant schema name (read-only, set during creation).
     */
    private String tenantSchema;

    /**
     * Customer name.
     */
    private String name;

    /**
     * Customer domain (unique identifier).
     */
    private String domain;

    /**
     * Customer settings as JSON.
     */
    private Map<String, Object> settings;

    /**
     * Customer status.
     */
    private Customer.CustomerStatus status;

    /**
     * Maximum number of users allowed.
     */
    private Integer maxUsers;

    /**
     * Maximum certificates per month.
     */
    private Integer maxCertificatesPerMonth;

    /**
     * Date when customer was created.
     */
    private LocalDateTime createdDate;

    /**
     * Date when customer was last updated.
     */
    private LocalDateTime updatedDate;
}
