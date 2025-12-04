package tech.seccertificate.certmgmt.integration.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.template.TemplateResponse;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.integration.BaseIntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test for the complete template management workflow.
 * 
 * <p>Tests the full template lifecycle:
 * <ol>
 *   <li>Template creation</li>
 *   <li>Template retrieval by ID and code</li>
 *   <li>Template listing</li>
 *   <li>Template update</li>
 *   <li>Template deletion</li>
 *   <li>Template metadata handling</li>
 * </ol>
 */
@DisplayName("Template Management End-to-End Tests")
class TemplateManagementE2ETest extends BaseIntegrationTest {

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        cleanup();
        initMockMvc();
        
        // Setup: Create a customer for template operations
        var uniqueSchema = generateUniqueSchema();
        testCustomer = createTestCustomer("Template Test Customer", 
                uniqueSchema + ".template.com", uniqueSchema);
        setTenantContext(testCustomer.getId());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("E2E: Complete template CRUD workflow")
    void completeTemplateCrudWorkflow() throws Exception {
        // Step 1: Create a template
        var templateCode = "CRUD_TEMPLATE_" + System.currentTimeMillis();
        var createDTO = TemplateResponse.builder()
                .customerId(testCustomer.getId())
                .name("CRUD Test Template")
                .code(templateCode)
                .description("Template for CRUD E2E testing")
                .metadata(Map.of(
                        "category", "testing",
                        "version", "1.0"
                ))
                .build();

        var createResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("CRUD Test Template"))
                .andExpect(jsonPath("$.data.code").value(templateCode))
                .andExpect(jsonPath("$.data.description").value("Template for CRUD E2E testing"))
                .andReturn();

        // Extract template ID
        var responseJson = createResult.getResponse().getContentAsString();
        var responseNode = objectMapper.readTree(responseJson);
        var templateId = responseNode.get("data").get("id").asLong();
        assertThat(templateId).isPositive();

        // Step 2: Read template by ID
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", templateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(templateId))
                .andExpect(jsonPath("$.data.code").value(templateCode));

        // Step 3: Read template by code
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/code/{code}", templateCode), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(templateId))
                .andExpect(jsonPath("$.data.name").value("CRUD Test Template"));

        // Step 4: Update template
        var updateDTO = TemplateResponse.builder()
                .id(templateId)
                .customerId(testCustomer.getId())
                .name("Updated CRUD Test Template")
                .code(templateCode)
                .description("Updated description for CRUD E2E testing")
                .metadata(Map.of(
                        "category", "testing",
                        "version", "2.0",
                        "updated", true
                ))
                .build();

        mockMvc.perform(
                        withTenantHeader(put("/api/templates/{id}", templateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated CRUD Test Template"))
                .andExpect(jsonPath("$.data.description").value("Updated description for CRUD E2E testing"));

        // Step 5: Verify update persisted
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", templateId), testCustomer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated CRUD Test Template"));

        // Step 6: Delete template
        mockMvc.perform(
                        withTenantHeader(delete("/api/templates/{id}", templateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Step 7: Verify deletion
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", templateId), testCustomer.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Template listing for tenant")
    void templateListingForTenant() throws Exception {
        // Create multiple templates
        var timestamp = System.currentTimeMillis();
        
        for (int i = 1; i <= 3; i++) {
            var templateDTO = TemplateResponse.builder()
                    .customerId(testCustomer.getId())
                    .name("List Test Template " + i)
                    .code("LIST_TEMPLATE_" + timestamp + "_" + i)
                    .metadata(Map.of("index", i))
                    .build();

            mockMvc.perform(
                            withTenantHeader(post("/api/templates"), testCustomer.getId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(templateDTO)))
                    .andExpect(status().isCreated());
        }

        // List all templates
        mockMvc.perform(
                        withTenantHeader(get("/api/templates"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("E2E: Template with complex metadata")
    void templateWithComplexMetadata() throws Exception {
        var templateCode = "METADATA_TEMPLATE_" + System.currentTimeMillis();
        var complexMetadata = Map.of(
                "category", "certificate",
                "tags", java.util.List.of("official", "verified", "premium"),
                "settings", Map.of(
                        "pageSize", "A4",
                        "orientation", "landscape",
                        "margins", Map.of("top", 20, "bottom", 20, "left", 25, "right", 25)
                ),
                "version", 1,
                "isActive", true
        );

        var templateDTO = TemplateResponse.builder()
                .customerId(testCustomer.getId())
                .name("Complex Metadata Template")
                .code(templateCode)
                .metadata(complexMetadata)
                .build();

        var result = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.metadata").exists())
                .andExpect(jsonPath("$.data.metadata.category").value("certificate"))
                .andReturn();

        // Verify metadata structure
        var responseJson = result.getResponse().getContentAsString();
        var responseNode = objectMapper.readTree(responseJson);
        var metadata = responseNode.get("data").get("metadata");
        assertThat(metadata.has("category")).isTrue();
        assertThat(metadata.has("settings")).isTrue();
    }

    @Test
    @DisplayName("E2E: Template not found - Invalid ID")
    void templateNotFound_InvalidId() throws Exception {
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", 999999L), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Template not found - Invalid code")
    void templateNotFound_InvalidCode() throws Exception {
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/code/{code}", "NONEXISTENT_CODE"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Template update - Non-existent template")
    void templateUpdate_NonExistent() throws Exception {
        var updateDTO = TemplateResponse.builder()
                .id(999999L)
                .customerId(testCustomer.getId())
                .name("Update Non-existent")
                .code("NONEXISTENT")
                .build();

        mockMvc.perform(
                        withTenantHeader(put("/api/templates/{id}", 999999L), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDTO)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Template delete - Non-existent template")
    void templateDelete_NonExistent() throws Exception {
        mockMvc.perform(
                        withTenantHeader(delete("/api/templates/{id}", 999999L), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Template with minimal required fields")
    void templateWithMinimalFields() throws Exception {
        var templateCode = "MINIMAL_TEMPLATE_" + System.currentTimeMillis();
        var templateDTO = TemplateResponse.builder()
                .customerId(testCustomer.getId())
                .name("Minimal Template")
                .code(templateCode)
                // No description, no metadata
                .build();

        mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Minimal Template"))
                .andExpect(jsonPath("$.data.code").value(templateCode));
    }

    @Test
    @DisplayName("E2E: Multiple templates with unique codes")
    void multipleTemplatesWithUniqueCodes() throws Exception {
        var baseCode = "UNIQUE_" + System.currentTimeMillis();
        
        // Create first template
        var template1 = TemplateResponse.builder()
                .customerId(testCustomer.getId())
                .name("Unique Template 1")
                .code(baseCode + "_1")
                .build();

        var result1 = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(template1)))
                .andExpect(status().isCreated())
                .andReturn();

        // Create second template with different code
        var template2 = TemplateResponse.builder()
                .customerId(testCustomer.getId())
                .name("Unique Template 2")
                .code(baseCode + "_2")
                .build();

        var result2 = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(template2)))
                .andExpect(status().isCreated())
                .andReturn();

        // Verify both templates exist
        var response1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        var response2 = objectMapper.readTree(result2.getResponse().getContentAsString());
        
        assertThat(response1.get("data").get("id").asLong())
                .isNotEqualTo(response2.get("data").get("id").asLong());
    }

    /**
     * Generate a unique schema name for testing.
     */
    private String generateUniqueSchema() {
        return "tmpl" + UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "")
                .substring(0, 10);
    }
}
