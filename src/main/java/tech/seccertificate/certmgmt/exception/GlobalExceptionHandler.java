package tech.seccertificate.certmgmt.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 * Provides centralized exception handling and consistent error responses.
 * 
 * <p>This handler catches exceptions thrown by controllers and converts them
 * into appropriate HTTP responses following RFC 7807 (Problem Details for HTTP APIs).
 * 
 * <p>All error responses conform to the RFC 7807 standard:
 * <ul>
 *   <li>{@code type} - Problem type URI</li>
 *   <li>{@code title} - Short summary</li>
 *   <li>{@code status} - HTTP status code</li>
 *   <li>{@code detail} - Detailed message</li>
 *   <li>{@code instance} - Request URI</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final String PROBLEM_BASE_URI = "https://api.certmgmt.example.com/problems";

    /**
     * Handle tenant not found exceptions.
     */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFoundException(TenantNotFoundException ex) {
        log.warn("Tenant not found: {}", ex.getMessage());
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/tenant-not-found"))
                .title("Tenant Not Found")
                .status(HttpStatus.NOT_FOUND.value())
                .detail(ex.getMessage())
                .instance(URI.create(getRequestPath()))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle tenant-related exceptions.
     */
    @ExceptionHandler(TenantException.class)
    public ResponseEntity<ErrorResponse> handleTenantException(TenantException ex) {
        log.error("Tenant exception: {}", ex.getMessage(), ex);
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/tenant-error"))
                .title("Tenant Error")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail(ex.getMessage())
                .instance(URI.create(getRequestPath()))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle customer not found exceptions.
     */
    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFoundException(CustomerNotFoundException ex) {
        log.warn("Customer not found: {}", ex.getMessage());
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/customer-not-found"))
                .title("Customer Not Found")
                .status(HttpStatus.NOT_FOUND.value())
                .detail(ex.getMessage())
                .instance(URI.create(getRequestPath()))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle tenant schema creation exceptions.
     */
    @ExceptionHandler(TenantSchemaCreationException.class)
    public ResponseEntity<ErrorResponse> handleTenantSchemaCreationException(TenantSchemaCreationException ex) {
        log.error("Tenant schema creation failed: {}", ex.getMessage(), ex);
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/schema-creation-failed"))
                .title("Schema Creation Failed")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail(ex.getMessage())
                .instance(URI.create(getRequestPath()))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handle validation exceptions (from @Valid annotations).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/validation-failed"))
                .title("Validation Failed")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Request validation failed. See 'errors' field for field-level details.")
                .instance(URI.create(getRequestPath()))
                .errors(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle illegal argument exceptions (from service layer validation).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/invalid-request"))
                .title("Invalid Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail(ex.getMessage())
                .instance(URI.create(getRequestPath()))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle illegal state exceptions (e.g., tenant context not set).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage(), ex);
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/invalid-state"))
                .title("Invalid State")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail(ex.getMessage())
                .instance(URI.create(getRequestPath()))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle all other unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        var error = ErrorResponse.builder()
                .type(URI.create(PROBLEM_BASE_URI + "/internal-server-error"))
                .title("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail("An unexpected error occurred. Please contact support.")
                .instance(URI.create(getRequestPath()))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Get the current request path from the servlet request.
     * Returns a relative path suitable for use as a URI instance.
     * 
     * @return The request path, or "/" if not available
     */
    private String getRequestPath() {
        try {
            var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                var request = attributes.getRequest();
                return request.getRequestURI();
            }
        } catch (Exception e) {
            log.debug("Could not retrieve request path", e);
        }
        return "/";
    }
}
