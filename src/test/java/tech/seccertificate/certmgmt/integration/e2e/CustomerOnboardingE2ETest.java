package tech.seccertificate.certmgmt.integration.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.dto.customer.CreateCustomerRequest;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.User;
import tech.seccertificate.certmgmt.integration.BaseIntegrationTest;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test for the complete customer onboarding workflow.
 *
 * <p>Tests the full customer lifecycle:
 * <ol>
 *   <li>Customer registration/onboarding</li>
 *   <li>Tenant schema creation verification</li>
 *   <li>Customer retrieval by ID</li>
 *   <li>Customer listing</li>
 *   <li>Customer with custom settings</li>
 *   <li>Validation error handling</li>
 * </ol>
 */
@DisplayName("Customer Onboarding End-to-End Tests")
class CustomerOnboardingE2ETest extends BaseIntegrationTest {

    private final static Logger log = LoggerFactory.getLogger(CustomerOnboardingE2ETest.class);

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        cleanup();
        initMockMvc();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("E2E: Complete customer onboarding flow with tenant schema creation")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void completeCustomerOnboardingFlow() throws Exception {
        // Step 1: Create a new customer with all required fields
        var uniqueSchema = generateUniqueSchema();
        var uniqueDomain = uniqueSchema + ".example.com";

        var createRequest = CreateCustomerRequest.builder()
                .name("E2E Onboarding Test Corp")
                .domain(uniqueDomain)
                .tenantSchema(uniqueSchema)
                .maxUsers(50)
                .maxCertificatesPerMonth(5000)
                .settings(Map.of(
                        "theme", "corporate",
                        "timezone", "UTC",
                        "emailNotifications", true
                ))
                .build();

        // Act: Create customer
        var createResult = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("E2E Onboarding Test Corp"))
                .andExpect(jsonPath("$.data.domain").value(uniqueDomain))
                .andExpect(jsonPath("$.data.tenantSchema").value(uniqueSchema))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.maxUsers").value(50))
                .andExpect(jsonPath("$.data.maxCertificatesPerMonth").value(5000))
                .andReturn();

        // Extract created customer ID from response
        var responseJson = createResult.getResponse().getContentAsString();
        var responseNode = objectMapper.readTree(responseJson);
        var customerId = responseNode.get("data").get("id").asLong();
        assertThat(customerId).isPositive();

        // Step 2: Verify customer exists in database with correct schema
        var savedCustomer = customerRepository.findById(customerId);
        assertThat(savedCustomer).isPresent();
        assertThat(savedCustomer.get().getTenantSchema()).isEqualTo(uniqueSchema);
        assertThat(savedCustomer.get().getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);

        // Step 3: Create an ADMIN user for this tenant and login to obtain JWT (required for secured endpoints)
        // IMPORTANT: Explicitly set tenant context for the test transaction before touching tenant tables (e.g. users).
        clearTenantContext();
        setTenantContext(customerId);

        var adminPassword = UUID.randomUUID().toString()
                .replaceAll("-", "").substring(0, 12);

        mockMvc.perform(withTenantHeader(post("/auth/users"), customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "e2e.admin@email.com",
                                "password", adminPassword,
                                "firstName", "E2E",
                                "lastName", "Admin",
                                "role", User.UserRole.ADMIN
                        ))))
                .andDo(print())
                .andExpect(status().isCreated());

        var loginResult = mockMvc.perform(withTenantHeader(post("/auth/login"), customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "e2e.admin@email.com",
                                "password", adminPassword
                        ))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        var loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        var jwt = loginJson.get("data").get("token").asText();
        assertThat(jwt).isNotBlank();

        // Step 4: Retrieve customer by ID via API (secured)
        // Back to public schema operations
        clearTenantContext();
        mockMvc.perform(get("/api/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + jwt))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(customerId))
                .andExpect(jsonPath("$.data.name").value("E2E Onboarding Test Corp"))
                .andExpect(jsonPath("$.data.tenantSchema").value(uniqueSchema));

        // Step 5: Verify customer appears in list (requires ADMIN)
        // NOTE: These integration tests build MockMvc without Spring Security filters,
        // so we must seed an Authentication into the SecurityContext for @PreAuthorize checks.
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "e2e-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
        try {
            mockMvc.perform(get("/api/customers")
                            .header("Authorization", "Bearer " + jwt))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("E2E: Customer onboarding with auto-generated schema")
    void customerOnboardingWithAutoGeneratedSchema() throws Exception {
        // Create customer without specifying tenantSchema - should be auto-generated
        var timestamp = System.currentTimeMillis();
        var uniqueDomain = "autogen" + timestamp + ".example.com";

        var createRequest = CreateCustomerRequest.builder()
                .name("Auto Schema Corp")
                .domain(uniqueDomain)
                // tenantSchema is NOT set - should be auto-generated
                .maxUsers(10)
                .maxCertificatesPerMonth(1000)
                .build();

        var result = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantSchema").exists())
                .andReturn();

        // Verify schema was generated
        var responseJson = result.getResponse().getContentAsString();
        var responseNode = objectMapper.readTree(responseJson);
        var generatedSchema = responseNode.get("data").get("tenantSchema").asText();
        assertThat(generatedSchema).isNotBlank();
    }

    @Test
    @DisplayName("E2E: Multiple customer onboarding - Tenant isolation verification")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void multipleCustomerOnboardingWithIsolation() throws Exception {
        // Create first customer
        var schema1 = generateUniqueSchema();
        var customer1Request = CreateCustomerRequest.builder()
                .name("Company Alpha")
                .domain(schema1 + ".alpha.com")
                .tenantSchema(schema1)
                .maxUsers(10)
                .maxCertificatesPerMonth(750)
                .build();

        var result1 = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customer1Request)))
                .andExpect(status().isCreated())
                .andReturn();

        var response1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        var customer1Id = response1.get("data").get("id").asLong();
        clearTenantContext();
        setTenantContext(customer1Id);

        var user1Password = UUID.randomUUID().toString()
                .replaceAll("-", "").substring(0, 12);

        mockMvc
                .perform(withTenantHeader(post("/auth/users"), customer1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test1.user@email.com",
                                "password", user1Password,
                                "firstName", "Test1",
                                "lastName", "User",
                                "role", User.UserRole.ADMIN
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        // User for Customer 1 Login
        var user1Login = mockMvc
                .perform(withTenantHeader(post("/auth/login"), customer1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test1.user@email.com",
                                "password", user1Password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        var login1Data = objectMapper.readTree(user1Login.getResponse().getContentAsString());
        var login1Jwt = login1Data.get("data").get("token").asText();

        // Create second customer
        clearTenantContext(); // customer onboarding happens in public schema
        var schema2 = generateUniqueSchema();
        var customer2Request = CreateCustomerRequest.builder()
                .name("Company Beta")
                .domain(schema2 + ".beta.com")
                .tenantSchema(schema2)
                .maxUsers(10)
                .maxCertificatesPerMonth(500)
                .build();

        var result2 = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customer2Request)))
                .andExpect(status().isCreated())
                .andReturn();

        var response2 = objectMapper.readTree(result2.getResponse().getContentAsString());
        var customer2Id = response2.get("data").get("id").asLong();

        clearTenantContext();
        setTenantContext(customer2Id);

        var user2Password = UUID.randomUUID().toString()
                .replaceAll("-", "").substring(0, 12);

        mockMvc
                .perform(withTenantHeader(post("/auth/users"), customer2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test2.user@email.com",
                                "password", user2Password,
                                "firstName", "Test2",
                                "lastName", "User",
                                "role", User.UserRole.ADMIN
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        // User for Customer 2 Login
        var user2Login = mockMvc
                .perform(withTenantHeader(post("/auth/login"), customer2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test2.user@email.com",
                                "password", user2Password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        var login2Data = objectMapper.readTree(user2Login.getResponse().getContentAsString());
        var login2Jwt = login2Data.get("data").get("token").asText();

        // Verify both customers exist and are distinct
        assertThat(customer1Id).isNotEqualTo(customer2Id);

        // Retrieve and verify each customer independently
        clearTenantContext(); // customer retrieval is a public schema operation
        mockMvc.perform(get("/api/customers/{id}", customer1Id)
                        .header("Authorization",
                                "Bearer " + login1Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Company Alpha"))
                .andExpect(jsonPath("$.data.tenantSchema").value(schema1));

        mockMvc.perform(get("/api/customers/{id}", customer2Id)
                .header("Authorization",
                        "Bearer " + login2Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Company Beta"))
                .andExpect(jsonPath("$.data.tenantSchema").value(schema2));

        // Verify customer list contains both
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "e2e-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
        try {
            mockMvc.perform(get("/api/customers")
                            .header("Authorization", "Bearer " + login1Jwt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()")
                            .value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("E2E: Customer onboarding validation - Missing required fields")
    void customerOnboardingValidation_MissingRequiredFields() throws Exception {
        // Test with empty name
        var invalidRequest1 = CreateCustomerRequest.builder()
                .name("")
                .domain("valid.domain.com")
                .build();

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest1)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        // Test with empty domain
        var invalidRequest2 = CreateCustomerRequest.builder()
                .name("Valid Name")
                .domain("")
                .build();

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest2)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E2E: Customer onboarding validation - Invalid domain format")
    void customerOnboardingValidation_InvalidDomain() throws Exception {
        var invalidRequest = CreateCustomerRequest.builder()
                .name("Test Company")
                .domain("invalid domain with spaces")
                .build();

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E2E: Customer onboarding validation - Max users limits")
    void customerOnboardingValidation_MaxUsersLimits() throws Exception {
        var uniqueSchema = generateUniqueSchema();

        // Test with maxUsers exceeding limit (max is 10000)
        var invalidRequest = CreateCustomerRequest.builder()
                .name("Test Company")
                .domain(uniqueSchema + ".example.com")
                .tenantSchema(uniqueSchema)
                .maxUsers(20000) // Exceeds max
                .build();

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E2E: Customer retrieval - Non-existent customer")
    void customerRetrieval_NonExistentCustomer() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", 999999L))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Customer onboarding with default values")
    void customerOnboardingWithDefaults() throws Exception {
        var uniqueSchema = generateUniqueSchema();

        // Create customer with only required fields
        var createRequest = CreateCustomerRequest.builder()
                .name("Minimal Config Corp")
                .domain(uniqueSchema + ".minimal.com")
                .tenantSchema(uniqueSchema)
                // Not setting maxUsers and maxCertificatesPerMonth - should use defaults
                .build();

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Minimal Config Corp"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    /**
     * Generate a unique schema name for testing.
     */
    private String generateUniqueSchema() {
        return "e2e" + UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");
    }
}
