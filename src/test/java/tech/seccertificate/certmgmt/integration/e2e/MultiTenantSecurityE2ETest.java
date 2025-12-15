package tech.seccertificate.certmgmt.integration.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.certificate.GenerateCertificateRequest;
import tech.seccertificate.certmgmt.dto.template.TemplateResponse;
import tech.seccertificate.certmgmt.dto.template.TemplateVersionResponse;
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
 * End-to-end test for multi-tenant security and isolation.
 * 
 * <p>Tests strict tenant isolation:
 * <ol>
 *   <li>Tenant A cannot access Tenant B's templates</li>
 *   <li>Tenant A cannot access Tenant B's template versions</li>
 *   <li>Tenant A cannot access Tenant B's certificates</li>
 *   <li>Cross-tenant operations are properly blocked</li>
 *   <li>Tenant context is properly enforced</li>
 * </ol>
 */
@DisplayName("Multi-Tenant Security End-to-End Tests")
class MultiTenantSecurityE2ETest extends BaseIntegrationTest {

    private Customer tenantA;
    private Customer tenantB;
    private Long tenantATemplateId;
    private UUID tenantATemplateVersionId;
    private UUID tenantACertificateId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        initMockMvc();
        
        // Setup: Create Tenant A
        var schemaA = generateUniqueSchema("a");
        tenantA = createTestCustomer("Tenant Alpha Corp", 
                schemaA + ".alpha.com", schemaA);
        
        // Setup: Create Tenant B
        var schemaB = generateUniqueSchema("b");
        tenantB = createTestCustomer("Tenant Beta Inc", 
                schemaB + ".beta.com", schemaB);
        
        // Setup: Create resources for Tenant A
        setTenantContext(tenantA.getId());
        setupTenantAResources();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void setupTenantAResources() throws Exception {
        // Create template for Tenant A
        var templateCode = "TENANT_A_TEMPLATE_" + System.currentTimeMillis();
        var templateDTO = TemplateResponse.builder()
                .customerId(tenantA.getId())
                .name("Tenant A Confidential Template")
                .code(templateCode)
                .metadata(Map.of("confidential", true))
                .build();

        var templateResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), tenantA.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        var templateResponse = objectMapper.readTree(templateResult.getResponse().getContentAsString());
        tenantATemplateId = templateResponse.get("data").get("id").asLong();

        // Create template version for Tenant A
        var versionDTO = TemplateVersionResponse.builder()
                .version(1)
                .htmlContent("<html><body>Tenant A Certificate</body></html>")
                .fieldSchema(Map.of("name", "string"))
                .cssStyles("body { }")
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .createdBy(UUID.randomUUID())
                .build();

        var versionResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", tenantATemplateId), tenantA.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(versionDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        var versionResponse = objectMapper.readTree(versionResult.getResponse().getContentAsString());
        tenantATemplateVersionId = UUID.fromString(versionResponse.get("id").asText());

        // Create certificate for Tenant A
        var certRequest = GenerateCertificateRequest.builder()
                .templateVersionId(tenantATemplateVersionId)
                .certificateNumber("TENANT-A-CERT-" + System.currentTimeMillis())
                .recipientData(Map.of("name", "Tenant A User"))
                .synchronous(true)
                .build();

        var certResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), tenantA.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(certRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var certResponse = objectMapper.readTree(certResult.getResponse().getContentAsString());
        tenantACertificateId = UUID.fromString(certResponse.get("data").get("id").asText());
    }

    @Test
    @DisplayName("E2E: Tenant B cannot access Tenant A's template by ID")
    void tenantBCannotAccessTenantATemplateById() throws Exception {
        // Set context to Tenant B
        setTenantContext(tenantB.getId());
        
        // Tenant B tries to access Tenant A's template
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", tenantATemplateId), tenantB.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Tenant B cannot access Tenant A's template version")
    void tenantBCannotAccessTenantATemplateVersion() throws Exception {
        // Set context to Tenant B
        setTenantContext(tenantB.getId());
        
        // Tenant B tries to access Tenant A's template version
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{templateId}/versions/{versionId}", 
                                tenantATemplateId, tenantATemplateVersionId), tenantB.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Tenant B cannot access Tenant A's certificate")
    void tenantBCannotAccessTenantACertificate() throws Exception {
        // Set context to Tenant B
        setTenantContext(tenantB.getId());
        
        // Tenant B tries to access Tenant A's certificate
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", tenantACertificateId), tenantB.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Tenant B cannot update Tenant A's template")
    void tenantBCannotUpdateTenantATemplate() throws Exception {
        // Set context to Tenant B
        setTenantContext(tenantB.getId());
        
        var updateDTO = TemplateResponse.builder()
                .id(tenantATemplateId)
                .customerId(tenantB.getId()) // Trying to claim it as Tenant B's
                .name("Hijacked Template")
                .code("HIJACKED_CODE")
                .build();
        
        // Tenant B tries to update Tenant A's template
        mockMvc.perform(
                        withTenantHeader(put("/api/templates/{id}", tenantATemplateId), tenantB.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDTO)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Tenant B cannot delete Tenant A's template")
    void tenantBCannotDeleteTenantATemplate() throws Exception {
        // Set context to Tenant B
        setTenantContext(tenantB.getId());
        
        // Tenant B tries to delete Tenant A's template
        mockMvc.perform(
                        withTenantHeader(delete("/api/templates/{id}", tenantATemplateId), tenantB.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Tenant B cannot revoke Tenant A's certificate")
    void tenantBCannotRevokeTenantACertificate() throws Exception {
        // Set context to Tenant B
        setTenantContext(tenantB.getId());
        
        // Tenant B tries to revoke Tenant A's certificate
        mockMvc.perform(
                        withTenantHeader(post("/api/certificates/{id}/revoke", tenantACertificateId), tenantB.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Tenant B cannot get download URL for Tenant A's certificate")
    void tenantBCannotGetDownloadUrlForTenantACertificate() throws Exception {
        // Set context to Tenant B
        setTenantContext(tenantB.getId());
        
        // Tenant B tries to get download URL for Tenant A's certificate
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}/download-url", tenantACertificateId), tenantB.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Each tenant sees only their own templates")
    void eachTenantSeesOnlyTheirOwnTemplates() throws Exception {
        // Create template for Tenant B
        setTenantContext(tenantB.getId());
        var templateCode = "TENANT_B_TEMPLATE_" + System.currentTimeMillis();
        var templateDTO = TemplateResponse.builder()
                .customerId(tenantB.getId())
                .name("Tenant B Template")
                .code(templateCode)
                .build();

        mockMvc.perform(
                        withTenantHeader(post("/api/templates"), tenantB.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andExpect(status().isCreated());

        // Tenant A lists templates - should only see Tenant A's templates
        setTenantContext(tenantA.getId());
        var tenantAListResult = mockMvc.perform(
                        withTenantHeader(get("/api/templates"), tenantA.getId()))
                .andExpect(status().isOk())
                .andReturn();

        var tenantATemplates = objectMapper.readTree(tenantAListResult.getResponse().getContentAsString());
        // Verify no Tenant B templates in Tenant A's list
        var tenantATemplatesList = tenantATemplates;
        if (tenantATemplatesList.isArray()) {
            for (var template : tenantATemplatesList) {
                assertThat(template.get("customerId").asLong()).isEqualTo(tenantA.getId());
            }
        }

        // Tenant B lists templates - should only see Tenant B's templates
        setTenantContext(tenantB.getId());
        var tenantBListResult = mockMvc.perform(
                        withTenantHeader(get("/api/templates"), tenantB.getId()))
                .andExpect(status().isOk())
                .andReturn();

        var tenantBTemplates = objectMapper.readTree(tenantBListResult.getResponse().getContentAsString());
        // Verify no Tenant A templates in Tenant B's list
        var tenantBTemplatesList = tenantBTemplates;
        if (tenantBTemplatesList.isArray()) {
            for (var template : tenantBTemplatesList) {
                assertThat(template.get("customerId").asLong()).isEqualTo(tenantB.getId());
            }
        }
    }

    @Test
    @DisplayName("E2E: Tenant isolation with concurrent operations")
    void tenantIsolationWithConcurrentOperations() throws Exception {
        // Create resources for both tenants
        var timestampA = System.currentTimeMillis();
        var timestampB = timestampA + 1;
        
        // Create template for Tenant A
        setTenantContext(tenantA.getId());
        var templateA = TemplateResponse.builder()
                .customerId(tenantA.getId())
                .name("Concurrent Template A")
                .code("CONCURRENT_A_" + timestampA)
                .build();
        
        var resultA = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), tenantA.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateA)))
                .andExpect(status().isCreated())
                .andReturn();
        
        // Create template for Tenant B
        setTenantContext(tenantB.getId());
        var templateB = TemplateResponse.builder()
                .customerId(tenantB.getId())
                .name("Concurrent Template B")
                .code("CONCURRENT_B_" + timestampB)
                .build();
        
        var resultB = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), tenantB.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateB)))
                .andExpect(status().isCreated())
                .andReturn();
        
        // Extract IDs
        var templateIdA = objectMapper.readTree(resultA.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        var templateIdB = objectMapper.readTree(resultB.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        
        // Verify isolation: Tenant A can access Template A
        setTenantContext(tenantA.getId());
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", templateIdA), tenantA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Concurrent Template A"));
        
        // Verify isolation: Tenant A cannot access Template B
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", templateIdB), tenantA.getId()))
                .andExpect(status().isNotFound());
        
        // Verify isolation: Tenant B can access Template B
        setTenantContext(tenantB.getId());
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", templateIdB), tenantB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Concurrent Template B"));
        
        // Verify isolation: Tenant B cannot access Template A
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", templateIdA), tenantB.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Certificate generated with Tenant A's template version cannot be accessed by Tenant B")
    void certificateGeneratedWithTenantAVersionCannotBeAccessedByTenantB() throws Exception {
        // Tenant A creates a new certificate
        setTenantContext(tenantA.getId());
        var certRequest = GenerateCertificateRequest.builder()
                .templateVersionId(tenantATemplateVersionId)
                .certificateNumber("ISOLATED-CERT-" + System.currentTimeMillis())
                .recipientData(Map.of("name", "Isolated Test"))
                .synchronous(true)
                .build();

        var certResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), tenantA.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(certRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var newCertId = UUID.fromString(objectMapper.readTree(certResult.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Verify Tenant A can access it
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", newCertId), tenantA.getId()))
                .andExpect(status().isOk());

        // Verify Tenant B cannot access it
        setTenantContext(tenantB.getId());
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", newCertId), tenantB.getId()))
                .andExpect(status().isNotFound());
    }

    /**
     * Generate a unique schema name for testing with a prefix.
     */
    private String generateUniqueSchema(String prefix) {
        return "sec" + prefix + UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");
    }
}
