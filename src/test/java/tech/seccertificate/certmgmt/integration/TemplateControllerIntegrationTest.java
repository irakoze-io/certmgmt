package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.template.TemplateResponse;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.service.TemplateService;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("TemplateController Integration Tests")
class TemplateControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TemplateService templateService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        cleanup();
        initMockMvc();
        // Use unique schema and domain to avoid conflicts
        var timestamp = System.currentTimeMillis();
        var uniqueSchema = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");

        var uniqueDomain = "test" + timestamp + ".example.com";
        testCustomer = createTestCustomer("Test Customer", uniqueDomain, uniqueSchema);
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
        var uniqueCode = "TEST_TEMPLATE_" + System.currentTimeMillis();
        var templateResponse = TemplateResponse.builder()
                .customerId(testCustomer.getId())
                .name("Test Template")
                .code(uniqueCode)
                .description("A test template")
                .metadata(Map.of("category", "certification"))
                .build();

        // Act & Assert
        var result = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateResponse)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Template"))
                .andExpect(jsonPath("$.code").value(uniqueCode))
                .andExpect(jsonPath("$.customerId").value(testCustomer.getId()))
                .andExpect(header().exists("Location"));

        // Verify template was created
        var responseBody = result.andReturn().getResponse().getContentAsString();
        var createdTemplate = objectMapper.readValue(responseBody, TemplateResponse.class);
        
        assertThat(createdTemplate.getId()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/templates/{id} - Should return template by ID")
    void getTemplate_ValidId_ReturnsTemplate() throws Exception {
        // Arrange - create template via service first
        setTenantContext(testCustomer.getId());
        String uniqueCode = "TEST_TEMPLATE_" + System.currentTimeMillis();
        var template = Template.builder()
                .customerId(testCustomer.getId())
                .name("Test Template")
                .code(uniqueCode)
                .description("A test template")
                .metadata("{}")
                .build();
        var createdTemplate = templateService.createTemplate(template);

        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", createdTemplate.getId()), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdTemplate.getId()))
                .andExpect(jsonPath("$.name").value("Test Template"))
                .andExpect(jsonPath("$.code").value(uniqueCode));
    }

    @Test
    @DisplayName("GET /api/templates/{id} - Should return 404 for non-existent template")
    void getTemplate_NonExistentId_ReturnsNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", 99999L), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/templates - Should return all templates")
    void getAllTemplates_ReturnsAllTemplates() throws Exception {
        // Arrange - create some templates
        setTenantContext(testCustomer.getId());
        var timestamp = System.currentTimeMillis();
        var template1 = Template.builder()
                .customerId(testCustomer.getId())
                .name("Template 1")
                .code("TEMPLATE_1_" + timestamp)
                .metadata("{}")
                .build();
        var template2 = Template.builder()
                .customerId(testCustomer.getId())
                .name("Template 2")
                .code("TEMPLATE_2_" + timestamp)
                .metadata("{}")
                .build();
        templateService.createTemplate(template1);
        templateService.createTemplate(template2);

        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(get("/api/templates"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/templates/code/{code} - Should return template by code")
    void getTemplateByCode_ValidCode_ReturnsTemplate() throws Exception {
        // Arrange - create template via service
        setTenantContext(testCustomer.getId());
        var uniqueCode = "TEST_TEMPLATE_" + System.currentTimeMillis();
        var template = Template.builder()
                .customerId(testCustomer.getId())
                .name("Test Template")
                .code(uniqueCode)
                .description("A test template")
                .metadata("{}")
                .build();
        templateService.createTemplate(template);

        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/code/{code}", uniqueCode), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(uniqueCode))
                .andExpect(jsonPath("$.name").value("Test Template"));
    }

    @Test
    @DisplayName("GET /api/templates/code/{code} - Should return 404 for non-existent code")
    void getTemplateByCode_NonExistentCode_ReturnsNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/code/{code}", "NON_EXISTENT_CODE"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/templates/{id} - Should update template")
    void updateTemplate_ValidRequest_ReturnsUpdated() throws Exception {
        // Arrange - create template first
        setTenantContext(testCustomer.getId());
        var uniqueCode = "TEST_TEMPLATE_" + System.currentTimeMillis();
        var template = Template.builder()
                .customerId(testCustomer.getId())
                .name("Original Name")
                .code(uniqueCode)
                .description("Original description")
                .metadata("{}")
                .build();
        var createdTemplate = templateService.createTemplate(template);

        // Prepare update response
        var updateResponse = TemplateResponse.builder()
                .id(createdTemplate.getId())
                .customerId(testCustomer.getId())
                .name("Updated Name")
                .code(uniqueCode)
                .description("Updated description")
                .metadata(Map.of("updated", true))
                .build();

        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(put("/api/templates/{id}", createdTemplate.getId()), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateResponse)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdTemplate.getId()))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    @DisplayName("PUT /api/templates/{id} - Should return 404 for non-existent template")
    void updateTemplate_NonExistentId_ReturnsNotFound() throws Exception {
        // Arrange
        var updateResponse = TemplateResponse.builder()
                .id(99999L)
                .customerId(testCustomer.getId())
                .name("Updated Name")
                .code("NON_EXISTENT")
                .build();

        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(put("/api/templates/{id}", 99999L), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateResponse)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/templates/{id} - Should delete template")
    void deleteTemplate_ValidId_ReturnsNoContent() throws Exception {
        // Arrange - create template first
        setTenantContext(testCustomer.getId());
        var uniqueCode = "TEST_TEMPLATE_" + System.currentTimeMillis();
        var template = Template.builder()
                .customerId(testCustomer.getId())
                .name("Test Template")
                .code(uniqueCode)
                .metadata("{}")
                .build();
        var createdTemplate = templateService.createTemplate(template);

        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(delete("/api/templates/{id}", createdTemplate.getId()), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verify template was deleted
        setTenantContext(testCustomer.getId());
        assertThat(templateService.findById(createdTemplate.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/templates/{id} - Should return 404 for non-existent template")
    void deleteTemplate_NonExistentId_ReturnsNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(delete("/api/templates/{id}", 99999L), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/templates - Should fail with invalid request")
    void createTemplate_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Arrange - missing required fields
        var templateResponse = TemplateResponse.builder()
                .name("") // Invalid: empty name
                .code("") // Invalid: empty code
                .build();

        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateResponse)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
