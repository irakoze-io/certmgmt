package tech.seccertificate.certmgmt.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.Map;

/**
 * RFC 7807 compliant Problem Details for HTTP APIs.
 * 
 * <p>This class implements the standard error response format defined in
 * <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a>.
 * 
 * <p>Required fields:
 * <ul>
 *   <li>{@code type} - A URI reference that identifies the problem type</li>
 *   <li>{@code title} - A short, human-readable summary of the problem type</li>
 *   <li>{@code status} - The HTTP status code</li>
 * </ul>
 * 
 * <p>Optional fields:
 * <ul>
 *   <li>{@code detail} - A human-readable explanation specific to this occurrence</li>
 *   <li>{@code instance} - A URI reference that identifies the specific occurrence</li>
 * </ul>
 * 
 * <p>Extension members:
 * <ul>
 *   <li>{@code errors} - Field-level validation errors (only for validation failures)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * A URI reference that identifies the problem type.
     * This should be a stable identifier that doesn't change between occurrences.
     * 
     * Example: "https://api.example.com/problems/tenant-not-found"
     */
    private URI type;
    
    /**
     * A short, human-readable summary of the problem type.
     * This should be the same for every occurrence of the same problem type.
     * 
     * Example: "Tenant Not Found"
     */
    private String title;
    
    /**
     * The HTTP status code for this occurrence of the problem.
     * Must match the actual HTTP status code returned.
     */
    private Integer status;
    
    /**
     * A human-readable explanation specific to this occurrence of the problem.
     * This provides more detail than the title.
     * 
     * Example: "Customer with ID 123 does not have a tenant schema configured"
     */
    private String detail;
    
    /**
     * A URI reference that identifies the specific occurrence of the problem.
     * This should point to the specific request that caused the error.
     * 
     * Example: "https://api.example.com/api/customers/123"
     */
    private URI instance;
    
    /**
     * Extension member: Field-level validation errors.
     * Only populated for validation failures (MethodArgumentNotValidException).
     * Maps field names to their error messages.
     */
    private Map<String, String> errors;
}
