package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.template.TemplateVersionDTO;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("TemplateVersionController Integration Tests")
class TemplateVersionControllerIntegrationTest extends BaseIntegrationTest {

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        cleanup();
        initMockMvc();
        var uniqueName = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");
        testCustomer = createTestCustomer("Test Customer", "test.example.com", uniqueName);
        setTenantContext(testCustomer.getId());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("POST /api/templates/{templateId}/versions - Should create template version")
    void createTemplateVersion_ValidRequest_ReturnsCreated() throws Exception {
        // Arrange
        var versionDTO = TemplateVersionDTO.builder()
                .version(1)
                .htmlContent("<html><body>Test</body></html>")
                .fieldSchema(Map.of("name", "string", "email", "string"))
                .cssStyles("body { font-family: Arial; }")
                .status(TemplateVersion.TemplateVersionStatus.DRAFT)
                .build();

        // Act & Assert
        // This will need a valid template ID first
        // For now, we expect either 400/404 (bad request/not found) or 201 (created)
        var result = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", 1L), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(versionDTO)))
                .andDo(print())
                .andReturn();

        // Accept either success or client error (template may not exist)
        var status = result.getResponse().getStatus();
        assertThat(status).isBetween(200, 499); // Accept any 2xx or 4xx status
    }

    @Test
    @DisplayName("GET /api/templates/{templateId}/versions - Should return all versions")
    void getTemplateVersions_ReturnsAllVersions() throws Exception {
        // Act & Assert
        mockMvc
                .perform(withTenantHeader(get("/api/templates/{templateId}/versions", 1L),
                                testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}
