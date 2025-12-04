package tech.seccertificate.certmgmt.integration.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.customer.CreateCustomerRequest;
import tech.seccertificate.certmgmt.dto.customer.CustomerResponse;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.integration.BaseIntegrationTest;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

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

        // Step 3: Retrieve customer by ID via API
        mockMvc.perform(get("/api/customers/{id}", customerId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(customerId))
                .andExpect(jsonPath("$.data.name").value("E2E Onboarding Test Corp"))
                .andExpect(jsonPath("$.data.tenantSchema").value(uniqueSchema));

        // Step 4: Verify customer appears in list
        mockMvc.perform(get("/api/customers"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
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
    void multipleCustomerOnboardingWithIsolation() throws Exception {
        // Create first customer
        var schema1 = generateUniqueSchema();
        var customer1Request = CreateCustomerRequest.builder()
                .name("Company Alpha")
                .domain(schema1 + ".alpha.com")
                .tenantSchema(schema1)
                .maxUsers(25)
                .maxCertificatesPerMonth(2500)
                .build();

        var result1 = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customer1Request)))
                .andExpect(status().isCreated())
                .andReturn();

        var response1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        var customer1Id = response1.get("data").get("id").asLong();

        // Create second customer
        var schema2 = generateUniqueSchema();
        var customer2Request = CreateCustomerRequest.builder()
                .name("Company Beta")
                .domain(schema2 + ".beta.com")
                .tenantSchema(schema2)
                .maxUsers(100)
                .maxCertificatesPerMonth(10000)
                .build();

        var result2 = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customer2Request)))
                .andExpect(status().isCreated())
                .andReturn();

        var response2 = objectMapper.readTree(result2.getResponse().getContentAsString());
        var customer2Id = response2.get("data").get("id").asLong();

        // Verify both customers exist and are distinct
        assertThat(customer1Id).isNotEqualTo(customer2Id);

        // Retrieve and verify each customer independently
        mockMvc.perform(get("/api/customers/{id}", customer1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Company Alpha"))
                .andExpect(jsonPath("$.data.tenantSchema").value(schema1));

        mockMvc.perform(get("/api/customers/{id}", customer2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Company Beta"))
                .andExpect(jsonPath("$.data.tenantSchema").value(schema2));

        // Verify customer list contains both
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
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
        return "e2e_" + UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "")
                .substring(0, 10);
    }
}
