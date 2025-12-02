package tech.seccertificate.certmgmt.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tech.seccertificate.certmgmt.dto.template.TemplateDTO;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.repository.TemplateRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("TemplateController Integration Tests")
class TemplateControllerIntegrationTest extends BaseIntegrationTest {

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        cleanup();
        initMockMvc();
        testCustomer = createTestCustomer("Test Customer", "test.example.com", "test_customer");
        setTenantContext(testCustomer.getId());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("POST /api/templates - Should create a new template")
    void createTemplate_ValidRequest_ReturnsCreated() throws Exception {
        // Arrange
        TemplateDTO templateDTO = TemplateDTO.builder()
                .customerId(testCustomer.getId())
                .name("Test Template")
                .code("TEST_TEMPLATE")
                .description("A test template")
                .metadata(Map.of("category", "certification"))
                .build();

        // Act & Assert
        ResultActions result = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Template"))
                .andExpect(jsonPath("$.code").value("TEST_TEMPLATE"))
                .andExpect(jsonPath("$.customerId").value(testCustomer.getId()))
                .andExpect(header().exists("Location"));

        // Verify template was created
        String responseBody = result.andReturn().getResponse().getContentAsString();
        TemplateDTO createdTemplate = objectMapper.readValue(responseBody, TemplateDTO.class);
        
        setTenantContext(testCustomer.getId());
        assertThat(createdTemplate.getId()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/templates/{id} - Should return template by ID")
    void getTemplate_ValidId_ReturnsTemplate() throws Exception {
        // Arrange - create template via service first
        setTenantContext(testCustomer.getId());
        Template template = Template.builder()
                .customerId(testCustomer.getId())
                .name("Test Template")
                .code("TEST_TEMPLATE")
                .description("A test template")
                .build();
        // We'll need to use the service to create it properly
        // For now, let's test the endpoint directly

        // This test will need to be updated once we can create templates properly
        // For now, we'll test the endpoint structure
    }

    @Test
    @DisplayName("GET /api/templates - Should return all templates")
    void getAllTemplates_ReturnsAllTemplates() throws Exception {
        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(get("/api/templates"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/templates/code/{code} - Should return template by code")
    void getTemplateByCode_ValidCode_ReturnsTemplate() throws Exception {
        // This will be implemented after we can create templates
    }

    @Test
    @DisplayName("PUT /api/templates/{id} - Should update template")
    void updateTemplate_ValidRequest_ReturnsUpdated() throws Exception {
        // This will be implemented after we can create templates
    }

    @Test
    @DisplayName("DELETE /api/templates/{id} - Should delete template")
    void deleteTemplate_ValidId_ReturnsNoContent() throws Exception {
        // This will be implemented after we can create templates
    }
}
