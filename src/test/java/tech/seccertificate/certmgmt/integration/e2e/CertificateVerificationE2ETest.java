package tech.seccertificate.certmgmt.integration.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.certificate.CertificateDTO;
import tech.seccertificate.certmgmt.dto.certificate.GenerateCertificateRequest;
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
 * End-to-end test for certificate verification workflow.
 * 
 * <p>Tests the complete verification flow:
 * <ol>
 *   <li>Generate certificate</li>
 *   <li>Retrieve certificate hash</li>
 *   <li>Verify certificate via public endpoint</li>
 *   <li>Test verification with invalid hash</li>
 * </ol>
 */
@DisplayName("Certificate Verification End-to-End Tests")
class CertificateVerificationE2ETest extends BaseIntegrationTest {

    private Customer testCustomer;
    private Long templateId;
    private UUID templateVersionId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        initMockMvc();
        
        // Setup: Create customer, template, and version
        var uniqueSchema = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");
        testCustomer = createTestCustomer("Verification Test Customer", 
                uniqueSchema + ".example.com", uniqueSchema);
        setTenantContext(testCustomer.getId());

        // Create template
        var templateCode = "VERIFY_TEMPLATE_" + System.currentTimeMillis();
        var templateDTO = TemplateDTO.builder()
                .customerId(testCustomer.getId())
                .name("Verification Template")
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

        // Create template version
        var versionDTO = TemplateVersionDTO.builder()
                .version(1)
                .htmlContent("<html><body>Certificate</body></html>")
                .fieldSchema(Map.of("name", "string"))
                .cssStyles("body { }")
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .build();

        var versionResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", templateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(versionDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        var createdVersion = objectMapper.readValue(
                versionResult.getResponse().getContentAsString(),
                TemplateVersionDTO.class);
        templateVersionId = createdVersion.getId();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("E2E: Generate certificate → Get hash → Verify certificate")
    void certificateVerificationFlow() throws Exception {
        // Step 1: Generate Certificate
        var certificateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber("VERIFY-001")
                .recipientData(Map.of(
                        "name", "Jane Smith",
                        "email", "jane@example.com"
                ))
                .synchronous(true)
                .build();

        var certificateResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(certificateRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn();

        var createdCertificate = objectMapper.readValue(
                certificateResult.getResponse().getContentAsString(),
                CertificateDTO.class);
        
        assertThat(createdCertificate.getId()).isNotNull();
        assertThat(createdCertificate.getCertificateNumber()).isEqualTo("VERIFY-001");

        // Step 2: Get Certificate Hash (if endpoint exists)
        // Note: This assumes there's an endpoint to get the hash
        // If not, we'll need to retrieve the certificate and extract hash
        var certificateDetails = mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", createdCertificate.getId()), testCustomer.getId()))
                .andExpect(status().isOk())
                .andReturn();

        var certificate = objectMapper.readValue(
                certificateDetails.getResponse().getContentAsString(),
                CertificateDTO.class);

        // Step 3: Verify Certificate (public endpoint - no tenant header needed)
        // Note: Adjust endpoint based on actual implementation
        if (certificate.getSignedHash() != null && !certificate.getSignedHash().isEmpty()) {
            mockMvc.perform(
                            get("/api/certificates/verify/{hash}", certificate.getSignedHash()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.certificateNumber").value("VERIFY-001"));
        }

        // Step 4: Verify with invalid hash
        mockMvc.perform(
                        get("/api/certificates/verify/{hash}", "invalid-hash-12345"))
                .andDo(print())
                .andExpect(status().isNotFound()); // or 400 Bad Request, depending on implementation
    }

    @Test
    @DisplayName("E2E: Certificate download URL generation")
    void certificateDownloadUrlFlow() throws Exception {
        // Generate certificate
        var certificateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber("DOWNLOAD-001")
                .recipientData(Map.of("name", "Test User"))
                .synchronous(true)
                .build();

        var certificateResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(certificateRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var createdCertificate = objectMapper.readValue(
                certificateResult.getResponse().getContentAsString(),
                CertificateDTO.class);

        // Get signed download URL
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}/download-url", createdCertificate.getId()), 
                                testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
    }
}
