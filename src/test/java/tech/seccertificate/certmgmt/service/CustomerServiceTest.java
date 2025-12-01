package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.seccertificate.certmgmt.model.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService Unit Tests - Core Services Milestone")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        // Setup common test data if needed
    }

    @Test
    @DisplayName("Should create customer with valid data")
    void shouldCreateCustomerWithValidData() {
        // Arrange
        String name = "Test Company";
        String email = "test@company.com";
        when(customerRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(1L);
            return customer;
        });

        // Act
        Customer result = customerService.createCustomer(name, email);

        // Assert
        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(email, result.getEmail());
        assertNotNull(result.getApiKey());
        assertNotNull(result.getApiSecret());
        assertTrue(result.getApiKey().startsWith("ak_"));
        assertTrue(result.getApiSecret().startsWith("as_"));
        assertTrue(result.isActive());
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when creating customer with null name")
    void shouldThrowExceptionWhenCreatingCustomerWithNullName() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> customerService.createCustomer(null, "test@company.com")
        );
        assertEquals("Customer name cannot be empty", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when creating customer with empty name")
    void shouldThrowExceptionWhenCreatingCustomerWithEmptyName() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> customerService.createCustomer("   ", "test@company.com")
        );
        assertEquals("Customer name cannot be empty", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when creating customer with null email")
    void shouldThrowExceptionWhenCreatingCustomerWithNullEmail() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> customerService.createCustomer("Test Company", null)
        );
        assertEquals("Customer email cannot be empty", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should throw exception when creating customer with duplicate email")
    void shouldThrowExceptionWhenCreatingCustomerWithDuplicateEmail() {
        // Arrange
        String email = "test@company.com";
        Customer existingCustomer = new Customer("Existing Company", email);
        when(customerRepository.findByEmail(email)).thenReturn(Optional.of(existingCustomer));

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> customerService.createCustomer("New Company", email)
        );
        assertTrue(exception.getMessage().contains("already exists"));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Should get customer by ID")
    void shouldGetCustomerById() {
        // Arrange
        Long customerId = 1L;
        Customer customer = new Customer("Test Company", "test@company.com");
        customer.setId(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        // Act
        Optional<Customer> result = customerService.getCustomerById(customerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(customerId, result.get().getId());
        assertEquals("Test Company", result.get().getName());
        verify(customerRepository, times(1)).findById(customerId);
    }

    @Test
    @DisplayName("Should get customer by email")
    void shouldGetCustomerByEmail() {
        // Arrange
        String email = "test@company.com";
        Customer customer = new Customer("Test Company", email);
        when(customerRepository.findByEmail(email)).thenReturn(Optional.of(customer));

        // Act
        Optional<Customer> result = customerService.getCustomerByEmail(email);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(email, result.get().getEmail());
        verify(customerRepository, times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("Should get customer by API key")
    void shouldGetCustomerByApiKey() {
        // Arrange
        String apiKey = "ak_test123";
        Customer customer = new Customer("Test Company", "test@company.com");
        customer.setApiKey(apiKey);
        when(customerRepository.findByApiKey(apiKey)).thenReturn(Optional.of(customer));

        // Act
        Optional<Customer> result = customerService.getCustomerByApiKey(apiKey);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(apiKey, result.get().getApiKey());
        verify(customerRepository, times(1)).findByApiKey(apiKey);
    }

    @Test
    @DisplayName("Should update customer information")
    void shouldUpdateCustomerInformation() {
        // Arrange
        Long customerId = 1L;
        Customer customer = new Customer("Old Name", "old@company.com");
        customer.setId(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Customer result = customerService.updateCustomer(customerId, "New Name", "new@company.com");

        // Assert
        assertEquals("New Name", result.getName());
        assertEquals("new@company.com", result.getEmail());
        verify(customerRepository, times(1)).save(customer);
    }

    @Test
    @DisplayName("Should deactivate customer")
    void shouldDeactivateCustomer() {
        // Arrange
        Long customerId = 1L;
        Customer customer = new Customer("Test Company", "test@company.com");
        customer.setId(customerId);
        customer.setActive(true);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        customerService.deactivateCustomer(customerId);

        // Assert
        assertFalse(customer.isActive());
        verify(customerRepository, times(1)).save(customer);
    }

    @Test
    @DisplayName("Should regenerate API credentials")
    void shouldRegenerateApiCredentials() {
        // Arrange
        Long customerId = 1L;
        Customer customer = new Customer("Test Company", "test@company.com");
        customer.setId(customerId);
        customer.setApiKey("old_key");
        customer.setApiSecret("old_secret");
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Customer result = customerService.regenerateApiCredentials(customerId);

        // Assert
        assertNotEquals("old_key", result.getApiKey());
        assertNotEquals("old_secret", result.getApiSecret());
        assertTrue(result.getApiKey().startsWith("ak_"));
        assertTrue(result.getApiSecret().startsWith("as_"));
        verify(customerRepository, times(1)).save(customer);
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent customer")
    void shouldThrowExceptionWhenUpdatingNonExistentCustomer() {
        // Arrange
        Long customerId = 999L;
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> customerService.updateCustomer(customerId, "New Name", "new@email.com")
        );
        verify(customerRepository, never()).save(any(Customer.class));
    }
}
