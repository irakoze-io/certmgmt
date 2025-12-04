package tech.seccertificate.certmgmt.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Unified error response structure supporting both RFC 7807 Problem Details format
 * and the unified API response format.
 * 
 * <p>This class implements the standard error response format defined in
 * <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a> while also supporting
 * the unified API response format with errorCode, errorType, errorDetails, and errorData.
 * 
 * <p>RFC 7807 Required fields:
 * <ul>
 *   <li>{@code type} - A URI reference that identifies the problem type</li>
 *   <li>{@code title} - A short, human-readable summary of the problem type</li>
 *   <li>{@code status} - The HTTP status code</li>
 * </ul>
 * 
 * <p>RFC 7807 Optional fields:
 * <ul>
 *   <li>{@code detail} - A human-readable explanation specific to this occurrence</li>
 *   <li>{@code instance} - A URI reference that identifies the specific occurrence</li>
 * </ul>
 * 
 * <p>RFC 7807 Extension members:
 * <ul>
 *   <li>{@code errors} - Field-level validation errors (only for validation failures)</li>
 * </ul>
 * 
 * <p>Unified API Response Format fields (for use with {@link tech.seccertificate.certmgmt.dto.Response}):
 * <ul>
 *   <li>{@code errorCode} - Application-specific error code (e.g., "CUSTOMER_NOT_FOUND")</li>
 *   <li>{@code errorType} - Error type category (e.g., "Validation Error", "Business Logic Error")</li>
 *   <li>{@code errorDetails} - List of detailed error messages</li>
 *   <li>{@code errorData} - Additional error data (e.g., field-level errors as Map)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    // RFC 7807 fields
    /**
     * A URI reference that identifies the problem type.
     * This should be a stable identifier that doesn't change between occurrences.
     * <p>
     * Example: "https://api.example.com/problems/tenant-not-found"
     */
    private URI type;
    
    /**
     * A short, human-readable summary of the problem type.
     * This should be the same for every occurrence of the same problem type.
     * <p>
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
     * <p>
     * Example: "Customer with ID 123 does not have a tenant schema configured"
     */
    private String detail;
    
    /**
     * A URI reference that identifies the specific occurrence of the problem.
     * This should point to the specific request that caused the error.
     * <p>
     * Example: "https://api.example.com/api/customers/123"
     */
    private URI instance;
    
    /**
     * Extension member: Field-level validation errors.
     * Only populated for validation failures (MethodArgumentNotValidException).
     * Maps field names to their error messages.
     */
    private Map<String, String> errors;
    
    // Unified API Response Format fields
    /**
     * Application-specific error code for unified API response format.
     * <p>
     * This should be a stable, machine-readable identifier that can be used
     * by clients to handle specific error scenarios programmatically.
     * <p>
     * Examples:
     * <ul>
     *   <li>"CUSTOMER_NOT_FOUND"</li>
     *   <li>"TENANT_NOT_FOUND"</li>
     *   <li>"VALIDATION_FAILED"</li>
     *   <li>"UNAUTHORIZED"</li>
     * </ul>
     * <p>
     * Error codes should follow SCREAMING_SNAKE_CASE convention.
     */
    @JsonProperty("errorCode")
    private String errorCode;
    
    /**
     * Error type category for unified API response format.
     * <p>
     * Examples:
     * <ul>
     *   <li>"Validation Error" - Input validation failures</li>
     *   <li>"Business Logic Error" - Business rule violations</li>
     *   <li>"Authentication Error" - Authentication/authorization failures</li>
     *   <li>"Resource Not Found" - Requested resource doesn't exist</li>
     *   <li>"System Error" - Internal server errors</li>
     * </ul>
     */
    @JsonProperty("errorType")
    private String errorType;
    
    /**
     * List of detailed error messages for unified API response format.
     * <p>
     * Used for providing multiple error details, especially for validation errors
     * where multiple fields may have issues.
     * <p>
     * Examples:
     * <ul>
     *   <li>["email is required", "phone number is invalid"]</li>
     *   <li>["Tenant schema already exists", "Domain is already in use"]</li>
     * </ul>
     */
    @JsonProperty("details")
    private List<String> errorDetails;
    
    /**
     * Additional error data for unified API response format.
     * <p>
     * Can contain structured error information such as:
     * <ul>
     *   <li>Field-level validation errors (Map&lt;String, String&gt;)</li>
     *   <li>Additional context about the error</li>
     *   <li>Retry information</li>
     *   <li>Any other relevant error metadata</li>
     * </ul>
     */
    @JsonProperty("data")
    private Object errorData;
}
