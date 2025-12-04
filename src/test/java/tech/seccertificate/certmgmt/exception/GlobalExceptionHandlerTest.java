package tech.seccertificate.certmgmt.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tech.seccertificate.certmgmt.dto.Response;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    // ==================== handleTenantNotFoundException Tests ====================

    @Test
    @DisplayName("Should handle TenantNotFoundException with NOT_FOUND status")
    void handleTenantNotFoundException_ReturnsNotFound() {
        // Arrange
        var exception = new TenantNotFoundException("Tenant not found: test_tenant");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleTenantNotFoundException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Tenant not found: test_tenant");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("TENANT_NOT_FOUND");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getError().getErrorDetails()).contains("Tenant not found: test_tenant");
    }

    // ==================== handleTenantException Tests ====================

    @Test
    @DisplayName("Should handle TenantException with BAD_REQUEST status")
    void handleTenantException_ReturnsBadRequest() {
        // Arrange
        var exception = new TenantException("Customer is not active");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleTenantException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Customer is not active");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("TENANT_ERROR");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("Business Logic Error");
        assertThat(response.getBody().getError().getErrorDetails()).contains("Customer is not active");
    }

    // ==================== handleCustomerNotFoundException Tests ====================

    @Test
    @DisplayName("Should handle CustomerNotFoundException with NOT_FOUND status")
    void handleCustomerNotFoundException_ReturnsNotFound() {
        // Arrange
        var exception = new CustomerNotFoundException("Customer not found: 123");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleCustomerNotFoundException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Customer not found: 123");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("CUSTOMER_NOT_FOUND");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getError().getErrorDetails()).contains("Customer not found: 123");
    }

    // ==================== handleTenantSchemaCreationException Tests ====================

    @Test
    @DisplayName("Should handle TenantSchemaCreationException with INTERNAL_SERVER_ERROR status")
    void handleTenantSchemaCreationException_ReturnsInternalServerError() {
        // Arrange
        var exception = new TenantSchemaCreationException("Failed to create schema: test_schema");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleTenantSchemaCreationException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Failed to create schema: test_schema");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("SCHEMA_CREATION_FAILED");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("System Error");
        assertThat(response.getBody().getError().getErrorDetails()).contains("Failed to create schema: test_schema");
    }

    // ==================== handleValidationExceptions Tests ====================

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with field errors")
    void handleValidationExceptions_ReturnsBadRequestWithFieldErrors() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("customer", "name", "Name is required");
        FieldError fieldError2 = new FieldError("customer", "domain", "Domain is invalid");
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));
        
        // Create MethodArgumentNotValidException using mock
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Request validation failed");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("Validation Error");
        assertThat(response.getBody().getError().getErrorDetails()).isNotNull();
        assertThat(response.getBody().getError().getErrorDetails()).contains("name: Name is required");
        assertThat(response.getBody().getError().getErrorDetails()).contains("domain: Domain is invalid");
        
        // Check errorData contains fieldErrors
        assertThat(response.getBody().getError().getErrorData()).isNotNull();
        assertThat(response.getBody().getError().getErrorData()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> errorData = (Map<String, Object>) response.getBody().getError().getErrorData();
        assertThat(errorData).containsKey("fieldErrors");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) errorData.get("fieldErrors");
        assertThat(fieldErrors).containsEntry("name", "Name is required");
        assertThat(fieldErrors).containsEntry("domain", "Domain is invalid");
    }

    // ==================== handleIllegalArgumentException Tests ====================

    @Test
    @DisplayName("Should handle IllegalArgumentException with BAD_REQUEST status")
    void handleIllegalArgumentException_ReturnsBadRequest() {
        // Arrange
        var exception = new IllegalArgumentException("Invalid customer ID");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleIllegalArgumentException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid customer ID");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("Validation Error");
        assertThat(response.getBody().getError().getErrorDetails()).contains("Invalid customer ID");
    }

    // ==================== handleIllegalStateException Tests ====================

    @Test
    @DisplayName("Should handle IllegalStateException with BAD_REQUEST status")
    void handleIllegalStateException_ReturnsBadRequest() {
        // Arrange
        var exception = new IllegalStateException("Tenant context not set");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleIllegalStateException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Tenant context not set");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("INVALID_STATE");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("Business Logic Error");
        assertThat(response.getBody().getError().getErrorDetails()).contains("Tenant context not set");
    }

    // ==================== handleGenericException Tests ====================

    @Test
    @DisplayName("Should handle generic Exception with INTERNAL_SERVER_ERROR status")
    void handleGenericException_ReturnsInternalServerError() {
        // Arrange
        var exception = new RuntimeException("Unexpected error occurred");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleGenericException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred. Please contact support.");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("System Error");
        assertThat(response.getBody().getError().getErrorDetails()).isNotNull();
        assertThat(response.getBody().getError().getErrorDetails()).contains("An internal server error occurred. Please try again later or contact support if the problem persists.");
    }

    // ==================== ApplicationObjectNotFoundException Tests ====================

    @Test
    @DisplayName("Should handle ApplicationObjectNotFoundException with NOT_FOUND status")
    void handleApplicationObjectNotFoundException_ReturnsNotFound() {
        // Arrange
        var exception = new ApplicationObjectNotFoundException("Resource not found: template_123");

        // Act
        ResponseEntity<Response<Void>> response = exceptionHandler.handleApplicationObjectNotFoundException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found: template_123");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getError().getErrorType()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getError().getErrorDetails()).contains("Resource not found: template_123");
    }
}
