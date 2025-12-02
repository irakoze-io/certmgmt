package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.exception.CustomerNotFoundException;
import tech.seccertificate.certmgmt.exception.TenantSchemaCreationException;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerServiceImpl Unit Tests")
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer validCustomer;

    @BeforeEach
    void setUp() {
        validCustomer = Customer.builder()
                .name("Test Customer")
                .domain("example.com")
                .tenantSchema("example_com")
                .status(Customer.CustomerStatus.ACTIVE)
                .maxUsers(10)
                .maxCertificatesPerMonth(1000)
                .settings("{}")
                .build();
    }

    @AfterEach
    void tearDown() {
        // Clean up if needed
    }

    // ==================== onboardCustomer Tests ====================

    @Test
    @DisplayName("Should successfully onboard a customer with all fields provided")
    void onboardCustomer_WithAllFields_Success() throws SQLException {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);
        when(customerRepository.existsByDomain(anyString())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenReturn(validCustomer);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.execute()).thenReturn(true);

        // Act
        Customer result = customerService.onboardCustomer(validCustomer);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Customer");
        assertThat(result.getDomain()).isEqualTo("example.com");
        verify(customerRepository).save(any(Customer.class));
        verify(preparedStatement).setString(1, "example_com");
        verify(preparedStatement).execute();
    }

    @Test
    @DisplayName("Should auto-generate tenant schema when not provided")
    void onboardCustomer_WithoutTenantSchema_AutoGenerates() throws SQLException {
        // Arrange
        Customer customerWithoutSchema = Customer.builder()
                .name("Test Customer")
                .domain("example.com")
                .build();

        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);
        when(customerRepository.existsByDomain(anyString())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.execute()).thenReturn(true);

        // Act
        Customer result = customerService.onboardCustomer(customerWithoutSchema);

        // Assert
        assertThat(result.getTenantSchema()).isNotNull();
        assertThat(result.getTenantSchema()).isEqualTo("example_com");
    }

    @Test
    @DisplayName("Should set default values when not provided")
    void onboardCustomer_WithoutDefaults_SetsDefaults() throws SQLException {
        // Arrange
        Customer customerWithoutDefaults = Customer.builder()
                .name("Test Customer")
                .domain("example.com")
                .status(null) // Explicitly set to null to test default setting
                .build();

        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);
        when(customerRepository.existsByDomain(anyString())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.execute()).thenReturn(true);

        // Act
        Customer result = customerService.onboardCustomer(customerWithoutDefaults);

        // Assert
        assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.TRIAL);
        assertThat(result.getMaxUsers()).isEqualTo(10);
        assertThat(result.getMaxCertificatesPerMonth()).isEqualTo(1000);
        assertThat(result.getSettings()).isEqualTo("{}");
    }

    @Test
    @DisplayName("Should throw exception when tenant schema is already taken")
    void onboardCustomer_TenantSchemaTaken_ThrowsException() {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> customerService.onboardCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant schema")
                .hasMessageContaining("already in use");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when domain is already taken")
    void onboardCustomer_DomainTaken_ThrowsException() {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);
        when(customerRepository.existsByDomain(anyString())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> customerService.onboardCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Domain")
                .hasMessageContaining("already in use");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should rollback customer when schema creation fails")
    void onboardCustomer_SchemaCreationFails_RollbacksCustomer() throws SQLException {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);
        when(customerRepository.existsByDomain(anyString())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenReturn(validCustomer);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Schema creation failed"));

        // Act & Assert
        assertThatThrownBy(() -> customerService.onboardCustomer(validCustomer))
                .isInstanceOf(TenantSchemaCreationException.class)
                .hasMessageContaining("Failed to create tenant schema");

        verify(customerRepository).save(any(Customer.class));
        verify(customerRepository).delete(validCustomer);
    }

    @Test
    @DisplayName("Should throw exception when customer data violates constraints")
    void onboardCustomer_DataIntegrityViolation_ThrowsException() {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);
        when(customerRepository.existsByDomain(anyString())).thenReturn(false);
        when(customerRepository.save(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("Constraint violation"));

        // Act & Assert
        assertThatThrownBy(() -> customerService.onboardCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer data violates constraints");
    }

    // ==================== findById Tests ====================

    @Test
    @DisplayName("Should find customer by ID")
    void findById_ExistingId_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act
        Optional<Customer> result = customerService.findById(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(validCustomer);
        verify(customerRepository).findById(1L);
    }

    @Test
    @DisplayName("Should return empty when customer not found by ID")
    void findById_NonExistingId_ReturnsEmpty() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Customer> result = customerService.findById(999L);

        // Assert
        assertThat(result).isEmpty();
        verify(customerRepository).findById(999L);
    }

    // ==================== findByDomain Tests ====================

    @Test
    @DisplayName("Should find customer by domain")
    void findByDomain_ExistingDomain_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findByDomain("example.com")).thenReturn(Optional.of(validCustomer));

        // Act
        Optional<Customer> result = customerService.findByDomain("example.com");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getDomain()).isEqualTo("example.com");
        verify(customerRepository).findByDomain("example.com");
    }

    @Test
    @DisplayName("Should return empty when customer not found by domain")
    void findByDomain_NonExistingDomain_ReturnsEmpty() {
        // Arrange
        when(customerRepository.findByDomain("notfound.com")).thenReturn(Optional.empty());

        // Act
        Optional<Customer> result = customerService.findByDomain("notfound.com");

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== findByTenantSchema Tests ====================

    @Test
    @DisplayName("Should find customer by tenant schema")
    void findByTenantSchema_ExistingSchema_ReturnsCustomer() {
        // Arrange
        when(customerRepository.findByTenantSchema("example_com")).thenReturn(Optional.of(validCustomer));

        // Act
        Optional<Customer> result = customerService.findByTenantSchema("example_com");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getTenantSchema()).isEqualTo("example_com");
    }

    // ==================== findAll Tests ====================

    @Test
    @DisplayName("Should find all customers")
    void findAll_ReturnsAllCustomers() {
        // Arrange
        List<Customer> customers = List.of(validCustomer);
        when(customerRepository.findAll()).thenReturn(customers);

        // Act
        List<Customer> result = customerService.findAll();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result).contains(validCustomer);
    }

    // ==================== findActiveCustomers Tests ====================

    @Test
    @DisplayName("Should find only active customers")
    void findActiveCustomers_ReturnsOnlyActive() {
        // Arrange
        List<Customer> activeCustomers = List.of(validCustomer);
        when(customerRepository.findByStatus(Customer.CustomerStatus.ACTIVE)).thenReturn(activeCustomers);

        // Act
        List<Customer> result = customerService.findActiveCustomers();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
    }

    // ==================== updateCustomer Tests ====================

    @Test
    @DisplayName("Should update customer successfully")
    void updateCustomer_ValidUpdate_Success() {
        // Arrange
        validCustomer.setId(1L);
        Customer updatedCustomer = Customer.builder()
                .id(1L)
                .name("Updated Name")
                .domain("example.com")
                .tenantSchema("example_com")
                .maxUsers(20)
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(validCustomer);

        // Act
        Customer result = customerService.updateCustomer(updatedCustomer);

        // Assert
        assertThat(result).isNotNull();
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent customer")
    void updateCustomer_NonExistent_ThrowsException() {
        // Arrange
        validCustomer.setId(999L);
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> customerService.updateCustomer(validCustomer))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    @DisplayName("Should throw exception when updating with null customer")
    void updateCustomer_NullCustomer_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> customerService.updateCustomer(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer and customer ID must not be null");
    }

    @Test
    @DisplayName("Should throw exception when updating with null customer ID")
    void updateCustomer_NullCustomerId_ThrowsException() {
        // Arrange
        Customer customerWithoutId = Customer.builder().name("Test").build();

        // Act & Assert
        assertThatThrownBy(() -> customerService.updateCustomer(customerWithoutId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer and customer ID must not be null");
    }

    @Test
    @DisplayName("Should throw exception when trying to change tenant schema")
    void updateCustomer_ChangeTenantSchema_ThrowsException() {
        // Arrange
        validCustomer.setId(1L);
        Customer updatedCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .domain("example.com")
                .tenantSchema("different_schema")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));

        // Act & Assert
        assertThatThrownBy(() -> customerService.updateCustomer(updatedCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant schema cannot be changed");
    }

    @Test
    @DisplayName("Should throw exception when updating to a taken domain")
    void updateCustomer_DomainTaken_ThrowsException() {
        // Arrange
        validCustomer.setId(1L);
        Customer updatedCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .domain("taken.com")
                .tenantSchema("example_com")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(customerRepository.existsByDomain("taken.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> customerService.updateCustomer(updatedCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Domain is already in use");
    }

    // ==================== updateStatus Tests ====================

    @Test
    @DisplayName("Should update customer status")
    void updateStatus_ValidCustomer_Success() {
        // Arrange
        validCustomer.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(validCustomer);

        // Act
        Customer result = customerService.updateStatus(1L, Customer.CustomerStatus.SUSPENDED);

        // Assert
        assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.SUSPENDED);
        verify(customerRepository).save(validCustomer);
    }

    @Test
    @DisplayName("Should throw exception when updating status of non-existent customer")
    void updateStatus_NonExistent_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> customerService.updateStatus(999L, Customer.CustomerStatus.ACTIVE))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    // ==================== suspendCustomer Tests ====================

    @Test
    @DisplayName("Should suspend customer")
    void suspendCustomer_ValidCustomer_Success() {
        // Arrange
        validCustomer.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(validCustomer);

        // Act
        Customer result = customerService.suspendCustomer(1L);

        // Assert
        assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.SUSPENDED);
    }

    // ==================== activateCustomer Tests ====================

    @Test
    @DisplayName("Should activate customer")
    void activateCustomer_ValidCustomer_Success() {
        // Arrange
        validCustomer.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(validCustomer);

        // Act
        Customer result = customerService.activateCustomer(1L);

        // Assert
        assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
    }

    // ==================== isDomainTaken Tests ====================

    @Test
    @DisplayName("Should return true when domain is taken")
    void isDomainTaken_TakenDomain_ReturnsTrue() {
        // Arrange
        when(customerRepository.existsByDomain("taken.com")).thenReturn(true);

        // Act
        boolean result = customerService.isDomainTaken("taken.com");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when domain is not taken")
    void isDomainTaken_AvailableDomain_ReturnsFalse() {
        // Arrange
        when(customerRepository.existsByDomain("available.com")).thenReturn(false);

        // Act
        boolean result = customerService.isDomainTaken("available.com");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when domain is null")
    void isDomainTaken_NullDomain_ReturnsFalse() {
        // Act
        boolean result = customerService.isDomainTaken(null);

        // Assert
        assertThat(result).isFalse();
        verify(customerRepository, never()).existsByDomain(anyString());
    }

    @Test
    @DisplayName("Should return false when domain is empty")
    void isDomainTaken_EmptyDomain_ReturnsFalse() {
        // Act
        boolean result = customerService.isDomainTaken("");

        // Assert
        assertThat(result).isFalse();
        verify(customerRepository, never()).existsByDomain(anyString());
    }

    // ==================== isTenantSchemaTaken Tests ====================

    @Test
    @DisplayName("Should return true when tenant schema is taken")
    void isTenantSchemaTaken_TakenSchema_ReturnsTrue() {
        // Arrange
        when(customerRepository.existsByTenantSchema("taken_schema")).thenReturn(true);

        // Act
        boolean result = customerService.isTenantSchemaTaken("taken_schema");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when tenant schema is not taken")
    void isTenantSchemaTaken_AvailableSchema_ReturnsFalse() {
        // Arrange
        when(customerRepository.existsByTenantSchema("available_schema")).thenReturn(false);

        // Act
        boolean result = customerService.isTenantSchemaTaken("available_schema");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when tenant schema is null")
    void isTenantSchemaTaken_NullSchema_ReturnsFalse() {
        // Act
        boolean result = customerService.isTenantSchemaTaken(null);

        // Assert
        assertThat(result).isFalse();
        verify(customerRepository, never()).existsByTenantSchema(anyString());
    }

    // ==================== validateCustomer Tests ====================

    @Test
    @DisplayName("Should validate customer successfully")
    void validateCustomer_ValidCustomer_NoException() {
        // Act & Assert
        assertThatCode(() -> customerService.validateCustomer(validCustomer))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when customer is null")
    void validateCustomer_NullCustomer_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void validateCustomer_NullName_ThrowsException() {
        // Arrange
        validCustomer.setName(null);

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer name is required");
    }

    @Test
    @DisplayName("Should throw exception when name is empty")
    void validateCustomer_EmptyName_ThrowsException() {
        // Arrange
        validCustomer.setName("   ");

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer name is required");
    }

    @Test
    @DisplayName("Should throw exception when name exceeds 75 characters")
    void validateCustomer_NameTooLong_ThrowsException() {
        // Arrange
        validCustomer.setName("a".repeat(76));

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 75 characters");
    }

    @Test
    @DisplayName("Should throw exception when domain is null")
    void validateCustomer_NullDomain_ThrowsException() {
        // Arrange
        validCustomer.setDomain(null);

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer domain is required");
    }

    @Test
    @DisplayName("Should throw exception when domain is empty")
    void validateCustomer_EmptyDomain_ThrowsException() {
        // Arrange
        validCustomer.setDomain("   ");

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer domain is required");
    }

    @Test
    @DisplayName("Should throw exception when domain has invalid format")
    void validateCustomer_InvalidDomainFormat_ThrowsException() {
        // Arrange
        validCustomer.setDomain("invalid domain with spaces");

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid domain format");
    }

    @Test
    @DisplayName("Should validate domain with subdomain")
    void validateCustomer_DomainWithSubdomain_Success() {
        // Arrange
        validCustomer.setDomain("sub.example.com");

        // Act & Assert
        assertThatCode(() -> customerService.validateCustomer(validCustomer))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when tenant schema has invalid characters")
    void validateCustomer_InvalidTenantSchemaFormat_ThrowsException() {
        // Arrange
        validCustomer.setTenantSchema("invalid-schema!");

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only alphanumeric characters and underscores");
    }

    @Test
    @DisplayName("Should throw exception when tenant schema exceeds 75 characters")
    void validateCustomer_TenantSchemaTooLong_ThrowsException() {
        // Arrange
        validCustomer.setTenantSchema("a".repeat(76));

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 75 characters");
    }

    @Test
    @DisplayName("Should throw exception when maxUsers is less than 1")
    void validateCustomer_MaxUsersLessThanOne_ThrowsException() {
        // Arrange
        validCustomer.setMaxUsers(0);

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max users must be at least 1");
    }

    @Test
    @DisplayName("Should throw exception when maxCertificatesPerMonth is less than 1")
    void validateCustomer_MaxCertificatesLessThanOne_ThrowsException() {
        // Arrange
        validCustomer.setMaxCertificatesPerMonth(0);

        // Act & Assert
        assertThatThrownBy(() -> customerService.validateCustomer(validCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max certificates per month must be at least 1");
    }

    // ==================== generateTenantSchemaName Tests ====================

    @Test
    @DisplayName("Should generate schema name from domain")
    void generateTenantSchemaName_FromDomain_Success() {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);

        // Act
        String result = customerService.generateTenantSchemaName("example.com", "Test Customer");

        // Assert
        assertThat(result).isEqualTo("example_com");
        assertThat(result).matches("^[a-z0-9_]+$");
    }

    @Test
    @DisplayName("Should generate schema name from name when domain is too short")
    void generateTenantSchemaName_ShortDomain_UsesName() {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);

        // Act
        String result = customerService.generateTenantSchemaName("ab", "Test Customer");

        // Assert
        assertThat(result).isEqualTo("test_customer");
    }

    @Test
    @DisplayName("Should prepend tenant when schema doesn't start with letter")
    void generateTenantSchemaName_StartsWithNumber_PrependsTenant() {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);

        // Act
        String result = customerService.generateTenantSchemaName("123example.com", "Test");

        // Assert
        assertThat(result).startsWith("tenant_");
        assertThat(result).matches("^[a-zA-Z].*");
    }

    @Test
    @DisplayName("Should truncate schema name to 75 characters")
    void generateTenantSchemaName_TooLong_Truncates() {
        // Arrange
        String longDomain = "a".repeat(80) + ".com";
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);

        // Act
        String result = customerService.generateTenantSchemaName(longDomain, "Test");

        // Assert
        assertThat(result.length()).isLessThanOrEqualTo(75);
    }

    @Test
    @DisplayName("Should increment suffix when schema is taken")
    void generateTenantSchemaName_SchemaTaken_IncrementsSuffix() {
        // Arrange
        when(customerRepository.existsByTenantSchema("example_com")).thenReturn(true);
        when(customerRepository.existsByTenantSchema("example_com_1")).thenReturn(false);

        // Act
        String result = customerService.generateTenantSchemaName("example.com", "Test");

        // Assert
        assertThat(result).isEqualTo("example_com_1");
    }

    @Test
    @DisplayName("Should use default when both domain and name are null")
    void generateTenantSchemaName_NullInputs_UsesDefault() {
        // Arrange
        when(customerRepository.existsByTenantSchema("tenant")).thenReturn(false);

        // Act
        String result = customerService.generateTenantSchemaName(null, null);

        // Assert
        assertThat(result).isEqualTo("tenant");
    }

    @Test
    @DisplayName("Should convert to lowercase")
    void generateTenantSchemaName_MixedCase_ConvertsToLowercase() {
        // Arrange
        when(customerRepository.existsByTenantSchema(anyString())).thenReturn(false);

        // Act
        String result = customerService.generateTenantSchemaName("EXAMPLE.COM", "Test");

        // Assert
        assertThat(result).isEqualTo("example_com");
        assertThat(result).isLowerCase();
    }
}
