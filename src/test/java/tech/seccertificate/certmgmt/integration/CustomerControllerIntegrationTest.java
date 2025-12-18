package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.customer.CreateCustomerRequest;
import tech.seccertificate.certmgmt.dto.customer.CustomerResponse;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("CustomerController Integration Tests")
class CustomerControllerIntegrationTest extends BaseIntegrationTest {

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
        // Clean up test data - only delete customers created in tests
        // Note: In a real scenario, you might want to use @DirtiesContext or Testcontainers
        try {
            customerRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors in tests
        }
    }

    @Test
    @DisplayName("POST /api/customers - Should create a new customer")
    void createCustomer_ValidRequest_ReturnsCreated() throws Exception {
        // Arrange - use unique schema name to avoid conflicts
        var uniqueSchema = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");

        var uniqueDomain = uniqueSchema + System.currentTimeMillis() + ".example.com";
        var request = CreateCustomerRequest.builder()
                .name("Test Customer")
                .domain(uniqueDomain)
                .tenantSchema(uniqueSchema)
                .maxUsers(10)
                .maxCertificatesPerMonth(1000)
                .build();

        // Act & Assert
        var resultActions = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print());
        
        // Capture response for debugging
        var mvcResult = resultActions.andReturn();
        var responseBody = mvcResult.getResponse().getContentAsString();
        
        // Now assert
        resultActions.andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("Test Customer"))
                .andExpect(jsonPath("$.data.domain").value(uniqueDomain))
                .andExpect(jsonPath("$.data.tenantSchema").value(uniqueSchema))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(header().exists("Location"));

        var dataTree = objectMapper.readTree(responseBody).get("data");
        // Verify customer was created in database
        var createdCustomer = objectMapper.treeToValue(dataTree, CustomerResponse.class);
        
        assertThat(customerRepository.findById(createdCustomer.getId()))
                .isPresent()
                .hasValueSatisfying(customer -> {
                    assertThat(customer.getName()).isEqualTo("Test Customer");
                    assertThat(customer.getTenantSchema()).isEqualTo(uniqueSchema);
                });
    }

    @Test
    @DisplayName("POST /api/customers - Should fail with invalid request")
    void createCustomer_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Arrange - missing required fields
        var request = CreateCustomerRequest.builder()
                .name("") // Invalid: empty name
                .domain("") // Invalid: empty domain
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/customers/{id} - Should return customer by ID")
    void getCustomer_ValidId_ReturnsCustomer() throws Exception {
        // Arrange - use unique schema name to avoid conflicts
        var uniqueSchema = "test_customer_" + System.currentTimeMillis();
        var uniqueDomain = "test" + System.currentTimeMillis() + ".example.com";
        var customer = createTestCustomer("Test Customer", uniqueDomain, uniqueSchema);

        // Act & Assert
        mockMvc.perform(get("/api/customers/{id}", customer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.name").value("Test Customer"))
                .andExpect(jsonPath("$.domain").value(uniqueDomain));
    }

    @Test
    @DisplayName("GET /api/customers/{id} - Should return 404 for non-existent customer")
    void getCustomer_NonExistentId_ReturnsNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/customers/{id}", 99999L))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/customers - Should return all customers")
    void getAllCustomers_ReturnsAllCustomers() throws Exception {
        // Arrange - use unique schema names to avoid conflicts
        var timestamp = System.currentTimeMillis();
        var schema1 = "customer1_" + timestamp;
        var schema2 = "customer2_" + timestamp;
        var domain1 = "customer1" + timestamp + ".example.com";
        var domain2 = "customer2" + timestamp + ".example.com";

        createTestCustomer("Customer 1", domain1, schema1);
        createTestCustomer("Customer 2", domain2, schema2);

        // Act & Assert
        mockMvc.perform(get("/api/customers"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[1].id").exists());
    }
}
