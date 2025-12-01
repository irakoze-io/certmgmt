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

import java.net.URI;
import java.util.List;

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
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTenantNotFoundException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Tenant Not Found");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getDetail()).isEqualTo("Tenant not found: test_tenant");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/tenant-not-found");
        assertThat(response.getBody().getInstance()).hasToString("/api/test");
    }

    // ==================== handleTenantException Tests ====================

    @Test
    @DisplayName("Should handle TenantException with BAD_REQUEST status")
    void handleTenantException_ReturnsBadRequest() {
        // Arrange
        var exception = new TenantException("Customer is not active");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTenantException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Tenant Error");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).isEqualTo("Customer is not active");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/tenant-error");
    }

    // ==================== handleCustomerNotFoundException Tests ====================

    @Test
    @DisplayName("Should handle CustomerNotFoundException with NOT_FOUND status")
    void handleCustomerNotFoundException_ReturnsNotFound() {
        // Arrange
        var exception = new CustomerNotFoundException("Customer not found: 123");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCustomerNotFoundException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Customer Not Found");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getDetail()).isEqualTo("Customer not found: 123");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/customer-not-found");
    }

    // ==================== handleTenantSchemaCreationException Tests ====================

    @Test
    @DisplayName("Should handle TenantSchemaCreationException with INTERNAL_SERVER_ERROR status")
    void handleTenantSchemaCreationException_ReturnsInternalServerError() {
        // Arrange
        var exception = new TenantSchemaCreationException("Failed to create schema: test_schema");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTenantSchemaCreationException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Schema Creation Failed");
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).isEqualTo("Failed to create schema: test_schema");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/schema-creation-failed");
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

        var exception = new MethodArgumentNotValidException(null, bindingResult);

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Validation Failed");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).contains("Request validation failed");
        assertThat(response.getBody().getErrors()).isNotNull();
        assertThat(response.getBody().getErrors()).containsEntry("name", "Name is required");
        assertThat(response.getBody().getErrors()).containsEntry("domain", "Domain is invalid");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/validation-failed");
    }

    // ==================== handleIllegalArgumentException Tests ====================

    @Test
    @DisplayName("Should handle IllegalArgumentException with BAD_REQUEST status")
    void handleIllegalArgumentException_ReturnsBadRequest() {
        // Arrange
        var exception = new IllegalArgumentException("Invalid customer ID");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Request");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).isEqualTo("Invalid customer ID");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/invalid-request");
    }

    // ==================== handleIllegalStateException Tests ====================

    @Test
    @DisplayName("Should handle IllegalStateException with BAD_REQUEST status")
    void handleIllegalStateException_ReturnsBadRequest() {
        // Arrange
        var exception = new IllegalStateException("Tenant context not set");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalStateException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid State");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).isEqualTo("Tenant context not set");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/invalid-state");
    }

    // ==================== handleGenericException Tests ====================

    @Test
    @DisplayName("Should handle generic Exception with INTERNAL_SERVER_ERROR status")
    void handleGenericException_ReturnsInternalServerError() {
        // Arrange
        var exception = new RuntimeException("Unexpected error occurred");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred. Please contact support.");
        assertThat(response.getBody().getType()).hasToString("https://api.certmgmt.example.com/problems/internal-server-error");
    }

    // ==================== Request Path Tests ====================

    @Test
    @DisplayName("Should use default path when request context is not available")
    void handleException_NoRequestContext_UsesDefaultPath() {
        // Arrange
        RequestContextHolder.resetRequestAttributes();
        var exception = new TenantNotFoundException("Test exception");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTenantNotFoundException(exception);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInstance()).hasToString("/");
    }

    @Test
    @DisplayName("Should extract request URI from context")
    void handleException_WithRequestContext_ExtractsURI() {
        // Arrange
        mockRequest.setRequestURI("/api/customers/123");
        var exception = new IllegalArgumentException("Test exception");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(exception);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInstance()).hasToString("/api/customers/123");
    }

    @Test
    @DisplayName("Should include query parameters in instance URI")
    void handleException_WithQueryParams_IncludesInURI() {
        // Arrange
        mockRequest.setRequestURI("/api/templates");
        mockRequest.setQueryString("code=test_template");
        var exception = new IllegalArgumentException("Test");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(exception);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInstance()).hasToString("/api/templates");
    }
}
