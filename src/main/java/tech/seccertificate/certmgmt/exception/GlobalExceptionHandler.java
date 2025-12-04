package tech.seccertificate.certmgmt.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.seccertificate.certmgmt.dto.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 * Provides centralized exception handling and consistent error responses.
 *
 * <p>This handler catches exceptions thrown by controllers and converts them
 * into appropriate HTTP responses using the unified {@link Response} envelope structure.
 *
 * <p>All error responses follow the unified API response format:
 * <ul>
 *   <li>{@code success} - Always false for errors</li>
 *   <li>{@code message} - Human-readable error message</li>
 *   <li>{@code error} - Structured error information with errorCode, type, details, and data</li>
 * </ul>
 *
 * <p>Exception categories handled:
 * <ul>
 *   <li>Validation Errors - MethodArgumentNotValidException, IllegalArgumentException</li>
 *   <li>Business Exceptions - TenantException, CustomerNotFoundException, ApplicationObjectNotFoundException</li>
 *   <li>General Exceptions - All other unhandled exceptions</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== Business Exceptions ====================

    /**
     * Handle tenant not found exceptions.
     */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Response<Void>> handleTenantNotFoundException(TenantNotFoundException ex) {
        log.warn("Tenant not found: {}", ex.getMessage());
        var response = Response.<Void>error(
                ex.getMessage(),
                "TENANT_NOT_FOUND",
                "Resource Not Found",
                List.of(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle tenant-related exceptions (business logic errors).
     */
    @ExceptionHandler(TenantException.class)
    public ResponseEntity<Response<Void>> handleTenantException(TenantException ex) {
        log.error("Tenant exception: {}", ex.getMessage(), ex);
        var response = Response.<Void>error(
                ex.getMessage(),
                "TENANT_ERROR",
                "Business Logic Error",
                List.of(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle customer not found exceptions.
     */
    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Response<Void>> handleCustomerNotFoundException(CustomerNotFoundException ex) {
        log.warn("Customer not found: {}", ex.getMessage());
        var response = Response.<Void>error(
                ex.getMessage(),
                "CUSTOMER_NOT_FOUND",
                "Resource Not Found",
                List.of(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle application object not found exceptions (generic resource not found).
     */
    @ExceptionHandler(ApplicationObjectNotFoundException.class)
    public ResponseEntity<Response<Void>> handleApplicationObjectNotFoundException(ApplicationObjectNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        var response = Response.<Void>error(
                "The application could not find the requested resource",
                "RESOURCE_NOT_FOUND",
                "Resource Not Found",
                List.of(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle tenant schema creation exceptions (system errors).
     */
    @ExceptionHandler(TenantSchemaCreationException.class)
    public ResponseEntity<Response<Void>> handleTenantSchemaCreationException(TenantSchemaCreationException ex) {
        log.error("Tenant schema creation failed: {}", ex.getMessage(), ex);
        var response = Response.<Void>error(
                ex.getMessage(),
                "SCHEMA_CREATION_FAILED",
                "System Error",
                List.of(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ==================== Validation Errors ====================

    /**
     * Handle validation exceptions (from @Valid annotations).
     * Extracts field-level validation errors and formats them according to unified response structure.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getBindingResult().getAllErrors().size() + " error(s)");

        Map<String, String> fieldErrors = new HashMap<>();
        List<String> errorDetails = new ArrayList<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                String fieldName = fieldError.getField();
                String errorMessage = fieldError.getDefaultMessage();
                fieldErrors.put(fieldName, errorMessage);
                errorDetails.add(fieldName + ": " + errorMessage);
            } else {
                String errorMessage = error.getDefaultMessage();
                errorDetails.add(errorMessage);
            }
        });

        var response = Response.<Void>error(
                "Request validation failed. Please check the error details.",
                "VALIDATION_FAILED",
                "Validation Error",
                errorDetails,
                Map.of("fieldErrors", fieldErrors),
                null  // No top-level details needed
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle illegal argument exceptions (from service layer validation).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        var response = Response.<Void>error(
                ex.getMessage(),
                "INVALID_REQUEST",
                "Validation Error",
                List.of(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle illegal state exceptions (e.g., tenant context not set).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Response<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage(), ex);
        var response = Response.<Void>error(
                ex.getMessage(),
                "INVALID_STATE",
                "Business Logic Error",
                List.of(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== General Exceptions ====================

    /**
     * Handle all other unhandled exceptions.
     * This is a catch-all handler for any exceptions not specifically handled above.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        var response = Response.<Void>error(
                "An unexpected error occurred. Please contact support.",
                "INTERNAL_SERVER_ERROR",
                "System Error",
                List.of("An internal server error occurred. Please try again later or contact support if the problem persists.")
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
