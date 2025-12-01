package tech.seccertificate.certmgmt.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response structure for API errors.
 * Provides consistent error format across all endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    /**
     * Timestamp when the error occurred.
     */
    private LocalDateTime timestamp;
    
    /**
     * HTTP status code.
     */
    private Integer status;
    
    /**
     * Error type/category.
     */
    private String error;
    
    /**
     * Human-readable error message.
     */
    private String message;
    
    /**
     * Request path where the error occurred.
     */
    private String path;
    
    /**
     * Validation errors (field-level errors).
     * Only populated for validation failures.
     */
    private Map<String, String> errors;
}
