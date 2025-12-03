package tech.seccertificate.certmgmt.integration.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.template.TemplateDTO;
import tech.seccertificate.certmgmt.dto.template.TemplateVersionDTO;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.integration.BaseIntegrationTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test for template versioning workflow.
 * 
 * <p>Tests the complete versioning flow:
 * <ol>
 *   <li>Create template</li>
 *   <li>Create version 1 (DRAFT)</li>
 *   <li>Create version 2 (ACTIVE)</li>
 *   <li>Verify version history</li>
 *   <li>Test certificate generation with specific version</li>
 * </ol>
 */
@DisplayName("Template Versioning End-to-End Tests")
class TemplateVersioningE2ETest extends BaseIntegrationTest {

    private Customer testCustomer;
    private Long templateId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        initMockMvc();
        
        var uniqueSchema = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");
        testCustomer = createTestCustomer("Versioning Test Customer", 
                uniqueSchema + ".example.com", uniqueSchema);
        setTenantContext(testCustomer.getId());

        // Create template
        var templateCode = "VERSION_TEMPLATE_" + System.currentTimeMillis();
        var templateDTO = TemplateDTO.builder()
                .customerId(testCustomer.getId())
                .name("Versioning Template")
                .code(templateCode)
                .metadata(Map.of())
                .build();

        var templateResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        var createdTemplate = objectMapper.readValue(
                templateResult.getResponse().getContentAsString(), 
                TemplateDTO.class);
        templateId = createdTemplate.getId();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("E2E: Template versioning workflow - Create multiple versions")
    void templateVersioningWorkflow() throws Exception {
        // Step 1: Create Version 1 (DRAFT)
        var version1DTO = TemplateVersionDTO.builder()
                .version(1)
                .htmlContent("<html><body>Version 1 Content</body></html>")
                .fieldSchema(Map.of("name", "string"))
                .cssStyles("body { color: black; }")
                .status(TemplateVersion.TemplateVersionStatus.DRAFT)
                .build();

        var version1Result = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", templateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(version1DTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        var version1 = objectMapper.readValue(
                version1Result.getResponse().getContentAsString(),
                TemplateVersionDTO.class);
        assertThat(version1.getId()).isNotNull();

        // Step 2: Create Version 2 (ACTIVE)
        var version2DTO = TemplateVersionDTO.builder()
                .version(2)
                .htmlContent("<html><body>Version 2 Content - Updated</body></html>")
                .fieldSchema(Map.of("name", "string", "email", "string"))
                .cssStyles("body { color: blue; }")
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .build();

        var version2Result = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", templateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(version2DTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        var version2 = objectMapper.readValue(
                version2Result.getResponse().getContentAsString(),
                TemplateVersionDTO.class);
        assertThat(version2.getId()).isNotNull();
        assertThat(version2.getVersion()).isGreaterThan(version1.getVersion());

        // Step 3: Get all versions for template
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{templateId}/versions", templateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").exists())
                .andExpect(jsonPath("$[1].version").exists());

        // Step 4: Get specific version
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{templateId}/versions/{versionId}", 
                                templateId, version2.getId()), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(version2.getId()))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("E2E: Version activation workflow")
    void versionActivationWorkflow() throws Exception {
        // Create version 1 (DRAFT)
        var version1DTO = TemplateVersionDTO.builder()
                .version(1)
                .htmlContent("<html><body>Draft Version</body></html>")
                .fieldSchema(Map.of("name", "string"))
                .cssStyles("body { }")
                .status(TemplateVersion.TemplateVersionStatus.DRAFT)
                .build();

        var version1Result = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", templateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(version1DTO)))
                .andExpect(status().isCreated())
                .andReturn();

        var version1 = objectMapper.readValue(
                version1Result.getResponse().getContentAsString(),
                TemplateVersionDTO.class);

        // Activate version (update status to ACTIVE)
        var activatedVersionDTO = TemplateVersionDTO.builder()
                .id(version1.getId())
                .version(version1.getVersion())
                .htmlContent(version1.getHtmlContent())
                .fieldSchema(version1.getFieldSchema())
                .cssStyles(version1.getCssStyles())
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .build();

        mockMvc.perform(
                        withTenantHeader(put("/api/templates/{templateId}/versions/{versionId}", 
                                templateId, version1.getId()), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(activatedVersionDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }
}
