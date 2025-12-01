package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.config.TenantSchemaInterceptor;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.exception.TenantException;
import tech.seccertificate.certmgmt.exception.TenantNotFoundException;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService Unit Tests")
class TenantServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TenantSchemaInterceptor schemaInterceptor;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private ResultSet resultSet;

    @InjectMocks
    private TenantService tenantService;

    private Customer validCustomer;

    @BeforeEach
    void setUp() {
        validCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .domain("example.com")
                .tenantSchema("example_com")
                .status(Customer.CustomerStatus.ACTIVE)
                .build();
    }

    @AfterEach
    void tearDown() {
        // Clear tenant context after each test to avoid leaking state
        TenantContext.clear();
    }

    // ==================== executeInTenantContext Tests ====================

    @Test
    @DisplayName("Should execute operation in tenant context")
    void executeInTenantContext_ValidCustomer_ExecutesOperation() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(schemaInterceptor.executeInSchema(eq("example_com"), any()))
                .thenAnswer(invocation -> {
                    Function<Object, String> operation = invocation.getArgument(1);
                    return operation.apply(null);
                });

        Function<Object, String> testOperation = obj -> "test result";

        // Act
        String result = tenantService.executeInTenantContext(1L, testOperation);

        // Assert
        assertThat(result).isEqualTo("test result");
        verify(customerRepository).findById(1L);
        verify(schemaInterceptor).executeInSchema(eq("example_com"), any());
    }

    @Test
    @DisplayName("Should throw exception when customer not found in executeInTenantContext")
    void executeInTenantContext_CustomerNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> tenantService.executeInTenantContext(999L, obj -> "test"))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    // ==================== setTenantContext(Long customerId) Tests ====================

    @Test
    @DisplayName("Should set tenant context by customer ID")
    void setTenantContext_ValidCustomerId_SetsContext() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        tenantService.setTenantContext(1L);

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEqualTo("example_com");
        verify(customerRepository).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when customer not found")
    void setTenantContext_CustomerNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> tenantService.setTenantContext(999L))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    @DisplayName("Should throw exception when customer has no tenant schema")
    void setTenantContext_NoTenantSchema_ThrowsException() {
        // Arrange
        validCustomer.setTenantSchema(null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act & Assert
        assertThatThrownBy(() -> tenantService.setTenantContext(1L))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("does not have a tenant schema configured");
    }

    // ==================== setTenantContext(String tenantSchema) Tests ====================

    @Test
    @DisplayName("Should set tenant context by schema name")
    void setTenantContext_ValidSchemaName_SetsContext() {
        // Act
        tenantService.setTenantContext("valid_schema");

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEqualTo("valid_schema");
    }

    @Test
    @DisplayName("Should throw exception when schema name is null")
    void setTenantContext_NullSchemaName_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> tenantService.setTenantContext((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant schema cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when schema name is empty")
    void setTenantContext_EmptySchemaName_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> tenantService.setTenantContext(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant schema cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when schema name has invalid format")
    void setTenantContext_InvalidSchemaFormat_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> tenantService.setTenantContext("invalid-schema!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only alphanumeric characters and underscores");
    }

    // ==================== setTenantContext(Long customerId, boolean requireActive) Tests ====================

    @Test
    @DisplayName("Should set tenant context without requiring active status")
    void setTenantContext_NotRequireActive_SetsContext() {
        // Arrange
        validCustomer.setStatus(Customer.CustomerStatus.SUSPENDED);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        tenantService.setTenantContext(1L, false);

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEqualTo("example_com");
    }

    @Test
    @DisplayName("Should set tenant context when customer is active")
    void setTenantContext_RequireActiveAndIsActive_SetsContext() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        tenantService.setTenantContext(1L, true);

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEqualTo("example_com");
    }

    @Test
    @DisplayName("Should throw exception when requiring active but customer is not active")
    void setTenantContext_RequireActiveButNotActive_ThrowsException() {
        // Arrange
        validCustomer.setStatus(Customer.CustomerStatus.SUSPENDED);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act & Assert
        assertThatThrownBy(() -> tenantService.setTenantContext(1L, true))
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("is not active")
                .hasMessageContaining("SUSPENDED");
    }

    // ==================== clearTenantContext Tests ====================

    @Test
    @DisplayName("Should clear tenant context")
    void clearTenantContext_ClearsContext() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");
        assertThat(TenantContext.getTenantSchema()).isNotNull();

        // Act
        tenantService.clearTenantContext();

        // Assert
        assertThat(TenantContext.getTenantSchema()).isNull();
    }

    // ==================== getCurrentTenantSchema Tests ====================

    @Test
    @DisplayName("Should get current tenant schema")
    void getCurrentTenantSchema_ReturnsSchema() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act
        String result = tenantService.getCurrentTenantSchema();

        // Assert
        assertThat(result).isEqualTo("test_schema");
    }

    @Test
    @DisplayName("Should return null when no tenant schema is set")
    void getCurrentTenantSchema_NoSchemaSet_ReturnsNull() {
        // Act
        String result = tenantService.getCurrentTenantSchema();

        // Assert
        assertThat(result).isNull();
    }

    // ==================== hasTenantContext Tests ====================

    @Test
    @DisplayName("Should return true when tenant context is set")
    void hasTenantContext_ContextSet_ReturnsTrue() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act
        boolean result = tenantService.hasTenantContext();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when tenant context is not set")
    void hasTenantContext_NoContextSet_ReturnsFalse() {
        // Act
        boolean result = tenantService.hasTenantContext();

        // Assert
        assertThat(result).isFalse();
    }

    // ==================== resolveTenantSchema Tests ====================

    @Test
    @DisplayName("Should resolve tenant schema from customer ID")
    void resolveTenantSchema_ValidCustomerId_ReturnsSchema() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        String result = tenantService.resolveTenantSchema(1L);

        // Assert
        assertThat(result).isEqualTo("example_com");
        verify(customerRepository).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when customer not found during schema resolution")
    void resolveTenantSchema_CustomerNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> tenantService.resolveTenantSchema(999L))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    // ==================== validateSchemaExists Tests ====================

    @Test
    @DisplayName("Should return true when schema exists")
    void validateSchemaExists_ExistingSchema_ReturnsTrue() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getSchemas(isNull(), eq("example_com"))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        // Act
        boolean result = tenantService.validateSchemaExists("example_com");

        // Assert
        assertThat(result).isTrue();
        verify(dataSource).getConnection();
        verify(connection).close();
    }

    @Test
    @DisplayName("Should return false when schema does not exist")
    void validateSchemaExists_NonExistingSchema_ReturnsFalse() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getSchemas(isNull(), eq("non_existing"))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // Act
        boolean result = tenantService.validateSchemaExists("non_existing");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when schema name is null")
    void validateSchemaExists_NullSchema_ReturnsFalse() {
        // Act
        boolean result = tenantService.validateSchemaExists(null);

        // Assert
        assertThat(result).isFalse();
        verify(dataSource, never()).getConnection();
    }

    @Test
    @DisplayName("Should return false when schema name is empty")
    void validateSchemaExists_EmptySchema_ReturnsFalse() {
        // Act
        boolean result = tenantService.validateSchemaExists("");

        // Assert
        assertThat(result).isFalse();
        verify(dataSource, never()).getConnection();
    }

    @Test
    @DisplayName("Should return false when SQLException occurs")
    void validateSchemaExists_SQLException_ReturnsFalse() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenThrow(new SQLException("Database error"));

        // Act
        boolean result = tenantService.validateSchemaExists("test_schema");

        // Assert
        assertThat(result).isFalse();
    }

    // ==================== validateSchemaNameFormat Tests ====================

    @Test
    @DisplayName("Should validate valid schema name")
    void validateSchemaNameFormat_ValidSchema_NoException() {
        // Act & Assert
        assertThatCode(() -> tenantService.validateSchemaNameFormat("valid_schema_123"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when schema name is null")
    void validateSchemaNameFormat_NullSchema_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateSchemaNameFormat(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant schema cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when schema name is empty")
    void validateSchemaNameFormat_EmptySchema_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateSchemaNameFormat(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant schema cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when schema name contains invalid characters")
    void validateSchemaNameFormat_InvalidCharacters_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateSchemaNameFormat("invalid-schema!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only alphanumeric characters and underscores");
    }

    @Test
    @DisplayName("Should throw exception when schema name contains hyphens")
    void validateSchemaNameFormat_ContainsHyphens_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateSchemaNameFormat("invalid-schema"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only alphanumeric characters and underscores");
    }

    @Test
    @DisplayName("Should throw exception when schema name exceeds 75 characters")
    void validateSchemaNameFormat_TooLong_ThrowsException() {
        // Arrange
        String longSchema = "a".repeat(76);

        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateSchemaNameFormat(longSchema))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 75 characters");
    }

    @Test
    @DisplayName("Should accept schema name with 75 characters")
    void validateSchemaNameFormat_ExactlyMaxLength_NoException() {
        // Arrange
        String maxLengthSchema = "a".repeat(75);

        // Act & Assert
        assertThatCode(() -> tenantService.validateSchemaNameFormat(maxLengthSchema))
                .doesNotThrowAnyException();
    }

    // ==================== validateAndGetCustomer(Long, boolean) Tests ====================

    @Test
    @DisplayName("Should validate and return customer")
    void validateAndGetCustomer_ValidCustomer_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        Customer result = tenantService.validateAndGetCustomer(1L, false);

        // Assert
        assertThat(result).isEqualTo(validCustomer);
        verify(customerRepository).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when customer not found")
    void validateAndGetCustomer_CustomerNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateAndGetCustomer(999L, false))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    @DisplayName("Should throw exception when tenant schema is null")
    void validateAndGetCustomer_NullTenantSchema_ThrowsException() {
        // Arrange
        validCustomer.setTenantSchema(null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateAndGetCustomer(1L, false))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("does not have a tenant schema configured");
    }

    @Test
    @DisplayName("Should throw exception when tenant schema is empty")
    void validateAndGetCustomer_EmptyTenantSchema_ThrowsException() {
        // Arrange
        validCustomer.setTenantSchema("");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateAndGetCustomer(1L, false))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("does not have a tenant schema configured");
    }

    @Test
    @DisplayName("Should accept inactive customer when not requiring active")
    void validateAndGetCustomer_InactiveNotRequired_ReturnsCustomer() {
        // Arrange
        validCustomer.setStatus(Customer.CustomerStatus.SUSPENDED);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        Customer result = tenantService.validateAndGetCustomer(1L, false);

        // Assert
        assertThat(result).isEqualTo(validCustomer);
        assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Should accept active customer when requiring active")
    void validateAndGetCustomer_ActiveRequired_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        Customer result = tenantService.validateAndGetCustomer(1L, true);

        // Assert
        assertThat(result).isEqualTo(validCustomer);
        assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw exception when customer is not active but required")
    void validateAndGetCustomer_InactiveButRequired_ThrowsException() {
        // Arrange
        validCustomer.setStatus(Customer.CustomerStatus.TRIAL);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act & Assert
        assertThatThrownBy(() -> tenantService.validateAndGetCustomer(1L, true))
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("is not active")
                .hasMessageContaining("TRIAL");
    }

    // ==================== validateAndGetCustomer(Long) Tests ====================

    @Test
    @DisplayName("Should validate and return customer without active check")
    void validateAndGetCustomer_WithoutActiveCheck_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        Customer result = tenantService.validateAndGetCustomer(1L);

        // Assert
        assertThat(result).isEqualTo(validCustomer);
    }

    // ==================== isCustomerActive Tests ====================

    @Test
    @DisplayName("Should return true when customer is active")
    void isCustomerActive_ActiveCustomer_ReturnsTrue() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        boolean result = tenantService.isCustomerActive(1L);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when customer is not active")
    void isCustomerActive_InactiveCustomer_ReturnsFalse() {
        // Arrange
        validCustomer.setStatus(Customer.CustomerStatus.SUSPENDED);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        boolean result = tenantService.isCustomerActive(1L);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when customer not found")
    void isCustomerActive_CustomerNotFound_ReturnsFalse() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        boolean result = tenantService.isCustomerActive(999L);

        // Assert
        assertThat(result).isFalse();
    }

    // ==================== getCustomer Tests ====================

    @Test
    @DisplayName("Should get customer by ID")
    void getCustomer_ExistingCustomer_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        Customer result = tenantService.getCustomer(1L);

        // Assert
        assertThat(result).isEqualTo(validCustomer);
    }

    @Test
    @DisplayName("Should throw exception when customer not found")
    void getCustomer_CustomerNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> tenantService.getCustomer(999L))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    // ==================== getCustomerBySchema Tests ====================

    @Test
    @DisplayName("Should get customer by schema")
    void getCustomerBySchema_ExistingSchema_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findByTenantSchema("example_com")).thenReturn(Optional.of(validCustomer));

        // Act
        Optional<Customer> result = tenantService.getCustomerBySchema("example_com");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(validCustomer);
    }

    @Test
    @DisplayName("Should return empty when schema not found")
    void getCustomerBySchema_NonExistingSchema_ReturnsEmpty() {
        // Arrange
        when(customerRepository.findByTenantSchema("non_existing")).thenReturn(Optional.empty());

        // Act
        Optional<Customer> result = tenantService.getCustomerBySchema("non_existing");

        // Assert
        assertThat(result).isEmpty();
    }
}
