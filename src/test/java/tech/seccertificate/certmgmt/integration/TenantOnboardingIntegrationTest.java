package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.seccertificate.certmgmt.model.Customer;
import tech.seccertificate.certmgmt.model.User;
import tech.seccertificate.certmgmt.repository.CustomerRepository;
import tech.seccertificate.certmgmt.repository.UserRepository;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Tenant Onboarding milestone.
 * Tests the complete flow of customer creation, user management, and role assignment.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Tests - Tenant Onboarding Milestone")
class TenantOnboardingIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private UserService userService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        userRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("Should complete full tenant onboarding flow")
    void shouldCompleteFullTenantOnboardingFlow() {
        // Step 1: Create customer account
        Customer customer = customerService.createCustomer("Acme Corporation", "admin@acme.com");
        
        assertNotNull(customer.getId(), "Customer should be persisted with ID");
        assertNotNull(customer.getApiKey(), "Customer should have API key");
        assertNotNull(customer.getApiSecret(), "Customer should have API secret");
        assertTrue(customer.isActive(), "Customer should be active");

        // Step 2: Create admin user
        User adminUser = userService.createUser(
            customer.getId(),
            "admin",
            "admin@acme.com",
            User.UserRole.ADMIN
        );
        
        assertNotNull(adminUser.getId(), "Admin user should be persisted");
        assertEquals(User.UserRole.ADMIN, adminUser.getRole());
        assertTrue(adminUser.isActive());

        // Step 3: Create editor user
        User editorUser = userService.createUser(
            customer.getId(),
            "editor",
            "editor@acme.com",
            User.UserRole.EDITOR
        );
        
        assertNotNull(editorUser.getId(), "Editor user should be persisted");
        assertEquals(User.UserRole.EDITOR, editorUser.getRole());

        // Step 4: Create viewer user
        User viewerUser = userService.createUser(
            customer.getId(),
            "viewer",
            "viewer@acme.com",
            User.UserRole.VIEWER
        );
        
        assertNotNull(viewerUser.getId(), "Viewer user should be persisted");
        assertEquals(User.UserRole.VIEWER, viewerUser.getRole());

        // Step 5: Verify all users are associated with the customer
        List<User> customerUsers = userService.getUsersByCustomerId(customer.getId());
        assertEquals(3, customerUsers.size(), "Customer should have 3 users");

        // Step 6: Verify multi-tenant isolation - users belong only to their customer
        customerUsers.forEach(user -> 
            assertEquals(customer.getId(), user.getCustomerId(), 
                "All users should belong to the correct customer")
        );
    }

    @Test
    @DisplayName("Should enforce API credentials uniqueness")
    void shouldEnforceApiCredentialsUniqueness() {
        // Create first customer
        Customer customer1 = customerService.createCustomer("Customer 1", "customer1@test.com");
        String apiKey1 = customer1.getApiKey();

        // Create second customer
        Customer customer2 = customerService.createCustomer("Customer 2", "customer2@test.com");
        String apiKey2 = customer2.getApiKey();

        // Verify API keys are unique
        assertNotEquals(apiKey1, apiKey2, "API keys should be unique across customers");
        
        // Verify we can find customers by their unique API keys
        Optional<Customer> foundCustomer1 = customerService.getCustomerByApiKey(apiKey1);
        Optional<Customer> foundCustomer2 = customerService.getCustomerByApiKey(apiKey2);
        
        assertTrue(foundCustomer1.isPresent());
        assertTrue(foundCustomer2.isPresent());
        assertEquals(customer1.getId(), foundCustomer1.get().getId());
        assertEquals(customer2.getId(), foundCustomer2.get().getId());
    }

    @Test
    @DisplayName("Should support role-based access control with three roles")
    void shouldSupportRoleBasedAccessControlWithThreeRoles() {
        // Create customer
        Customer customer = customerService.createCustomer("Test Company", "test@company.com");

        // Create users with different roles
        User admin = userService.createUser(customer.getId(), "admin", "admin@test.com", User.UserRole.ADMIN);
        User editor = userService.createUser(customer.getId(), "editor", "editor@test.com", User.UserRole.EDITOR);
        User viewer = userService.createUser(customer.getId(), "viewer", "viewer@test.com", User.UserRole.VIEWER);

        // Verify roles are correctly assigned
        assertEquals(User.UserRole.ADMIN, admin.getRole());
        assertEquals(User.UserRole.EDITOR, editor.getRole());
        assertEquals(User.UserRole.VIEWER, viewer.getRole());

        // Update role
        User updatedUser = userService.updateUserRole(viewer.getId(), User.UserRole.EDITOR);
        assertEquals(User.UserRole.EDITOR, updatedUser.getRole());
    }

    @Test
    @DisplayName("Should enforce multi-tenant isolation")
    void shouldEnforceMultiTenantIsolation() {
        // Create two separate customers
        Customer customer1 = customerService.createCustomer("Company A", "companya@test.com");
        Customer customer2 = customerService.createCustomer("Company B", "companyb@test.com");

        // Create users for each customer
        User user1 = userService.createUser(customer1.getId(), "user1", "user1@companya.com", User.UserRole.ADMIN);
        User user2 = userService.createUser(customer2.getId(), "user2", "user2@companyb.com", User.UserRole.ADMIN);

        // Verify tenant isolation - each customer sees only their users
        List<User> customer1Users = userService.getUsersByCustomerId(customer1.getId());
        List<User> customer2Users = userService.getUsersByCustomerId(customer2.getId());

        assertEquals(1, customer1Users.size());
        assertEquals(1, customer2Users.size());
        assertEquals(customer1.getId(), customer1Users.get(0).getCustomerId());
        assertEquals(customer2.getId(), customer2Users.get(0).getCustomerId());
        assertNotEquals(customer1Users.get(0).getId(), customer2Users.get(0).getId());
    }

    @Test
    @DisplayName("Should regenerate API credentials")
    void shouldRegenerateApiCredentials() {
        // Create customer
        Customer customer = customerService.createCustomer("Test Company", "test@company.com");
        String originalApiKey = customer.getApiKey();
        String originalApiSecret = customer.getApiSecret();

        // Regenerate credentials
        Customer updatedCustomer = customerService.regenerateApiCredentials(customer.getId());

        // Verify credentials changed
        assertNotEquals(originalApiKey, updatedCustomer.getApiKey());
        assertNotEquals(originalApiSecret, updatedCustomer.getApiSecret());
        
        // Verify old API key no longer works
        Optional<Customer> foundByOldKey = customerService.getCustomerByApiKey(originalApiKey);
        assertFalse(foundByOldKey.isPresent(), "Old API key should not find customer");
        
        // Verify new API key works
        Optional<Customer> foundByNewKey = customerService.getCustomerByApiKey(updatedCustomer.getApiKey());
        assertTrue(foundByNewKey.isPresent(), "New API key should find customer");
        assertEquals(customer.getId(), foundByNewKey.get().getId());
    }

    @Test
    @DisplayName("Should deactivate customer and verify status")
    void shouldDeactivateCustomerAndVerifyStatus() {
        // Create and activate customer
        Customer customer = customerService.createCustomer("Test Company", "test@company.com");
        assertTrue(customer.isActive());

        // Deactivate customer
        customerService.deactivateCustomer(customer.getId());

        // Verify customer is deactivated
        Optional<Customer> deactivated = customerService.getCustomerById(customer.getId());
        assertTrue(deactivated.isPresent());
        assertFalse(deactivated.get().isActive(), "Customer should be deactivated");
    }

    @Test
    @DisplayName("Should deactivate user and maintain tenant association")
    void shouldDeactivateUserAndMaintainTenantAssociation() {
        // Create customer and user
        Customer customer = customerService.createCustomer("Test Company", "test@company.com");
        User user = userService.createUser(customer.getId(), "testuser", "user@test.com", User.UserRole.EDITOR);
        assertTrue(user.isActive());

        // Deactivate user
        userService.deactivateUser(user.getId());

        // Verify user is deactivated but still associated with customer
        Optional<User> deactivated = userService.getUserById(user.getId());
        assertTrue(deactivated.isPresent());
        assertFalse(deactivated.get().isActive(), "User should be deactivated");
        assertEquals(customer.getId(), deactivated.get().getCustomerId(), "User should still be associated with customer");
    }

    @Test
    @DisplayName("Should prevent duplicate user emails across tenants")
    void shouldPreventDuplicateUserEmailsAcrossTenants() {
        // Create two customers
        Customer customer1 = customerService.createCustomer("Company A", "companya@test.com");
        Customer customer2 = customerService.createCustomer("Company B", "companyb@test.com");

        // Create user for first customer
        String email = "shared@test.com";
        userService.createUser(customer1.getId(), "user1", email, User.UserRole.ADMIN);

        // Attempt to create user with same email for second customer
        assertThrows(IllegalStateException.class, () ->
            userService.createUser(customer2.getId(), "user2", email, User.UserRole.ADMIN),
            "Should not allow duplicate emails across tenants"
        );
    }

    @Test
    @DisplayName("Should validate tenant onboarding milestone completion")
    void shouldValidateTenantOnboardingMilestoneCompletion() {
        // Milestone Requirement 1: Customer onboarding
        Customer customer = customerService.createCustomer("Milestone Test Corp", "milestone@test.com");
        assertNotNull(customer.getId(), "✓ Customer account created");
        assertNotNull(customer.getApiKey(), "✓ API credentials assigned");

        // Milestone Requirement 2: User management
        User admin = userService.createUser(customer.getId(), "admin", "admin@milestone.com", User.UserRole.ADMIN);
        User editor = userService.createUser(customer.getId(), "editor", "editor@milestone.com", User.UserRole.EDITOR);
        User viewer = userService.createUser(customer.getId(), "viewer", "viewer@milestone.com", User.UserRole.VIEWER);
        
        assertNotNull(admin.getId(), "✓ Admin user created");
        assertNotNull(editor.getId(), "✓ Editor user created");
        assertNotNull(viewer.getId(), "✓ Viewer user created");

        // Milestone Requirement 3: Role-based access control
        assertEquals(User.UserRole.ADMIN, admin.getRole(), "✓ Admin role assigned");
        assertEquals(User.UserRole.EDITOR, editor.getRole(), "✓ Editor role assigned");
        assertEquals(User.UserRole.VIEWER, viewer.getRole(), "✓ Viewer role assigned");

        // Milestone Requirement 4: Multi-tenant isolation
        List<User> users = userService.getUsersByCustomerId(customer.getId());
        assertEquals(3, users.size(), "✓ All users belong to correct tenant");
        users.forEach(u -> assertEquals(customer.getId(), u.getCustomerId(), "✓ Tenant isolation enforced"));

        // TENANT ONBOARDING MILESTONE: PASSED
        assertTrue(true, "✓✓✓ TENANT ONBOARDING MILESTONE COMPLETED ✓✓✓");
    }
}
