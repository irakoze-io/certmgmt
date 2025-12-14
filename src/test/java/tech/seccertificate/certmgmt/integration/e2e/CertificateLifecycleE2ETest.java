package tech.seccertificate.certmgmt.integration.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import tech.seccertificate.certmgmt.dto.certificate.CertificateResponse;
import tech.seccertificate.certmgmt.dto.certificate.GenerateCertificateRequest;
import tech.seccertificate.certmgmt.dto.template.TemplateResponse;
import tech.seccertificate.certmgmt.dto.template.TemplateVersionResponse;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.integration.BaseIntegrationTest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test for the complete certificate lifecycle.
 *
 * <p>Tests the full certificate lifecycle including:
 * <ol>
 *   <li>Certificate generation (sync and async)</li>
 *   <li>Certificate retrieval by ID and number</li>
 *   <li>Certificate update</li>
 *   <li>Certificate revocation</li>
 *   <li>Certificate deletion</li>
 *   <li>Certificate filtering by status, customer, and template version</li>
 * </ol>
 */
@DisplayName("Certificate Lifecycle End-to-End Tests")
class CertificateLifecycleE2ETest extends BaseIntegrationTest {

    private final static Logger log = LoggerFactory.getLogger(CertificateLifecycleE2ETest.class);

    private Customer testCustomer;
    private UUID templateVersionId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        initMockMvc();

        // Setup: Create customer
        var uniqueSchema = generateUniqueSchema();
        log.info("Unique schema generated: {}", uniqueSchema);

        testCustomer = createTestCustomer("CL Test Customer",
                uniqueSchema + ".lifecycle.com", uniqueSchema);
        setTenantContext(testCustomer.getId());

        // Setup: Create template
        var templateCode = "LIFECYCLE_TEMPLATE_" + System.currentTimeMillis();
        var templateDTO = TemplateResponse.builder()
                .customerId(testCustomer.getId())
                .name("Lifecycle Template")
                .code(templateCode)
                .metadata(Map.of())
                .build();

        var templateResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        var templateResponse = objectMapper.readTree(templateResult.getResponse().getContentAsString());
        Long templateId = templateResponse.get("data").get("id").asLong();

        // Setup: Create template version
        var versionDTO = TemplateVersionResponse.builder()
                .version(1)
                .htmlContent("<html><body><h1>Certificate for {{name}}</h1><p>Email: {{email}}</p></body></html>")
                .fieldSchema(Map.of("name", "string", "email", "string"))
                .cssStyles("body { font-family: Arial; }")
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .createdBy(UUID.randomUUID())
                .build();

        var result = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", templateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(versionDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        var tvr = objectMapper.readTree(result.getResponse().getContentAsString());

        templateVersionId = UUID.fromString(tvr
                .get("data")
                .get("id").asText());
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("E2E: Complete certificate lifecycle - Create → Read → Update → Revoke → Delete")
    void completeCertificateLifecycle() throws Exception {
        // Step 1: Generate certificate synchronously
        var certNumber = "LIFECYCLE-" + System.currentTimeMillis();
        var generateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber(certNumber)
                .recipientData(Map.of(
                        "name", "John Smith",
                        "email", "john.smith@example.com"
                ))
                .metadata(Map.of(
                        "purpose", "testing",
                        "issueReason", "certification exam passed"
                ))
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusYears(1))
                .issuedBy(UUID.randomUUID())
                .synchronous(true)
                .build();

        var createResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(generateRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.certificateNumber").value(certNumber))
                .andExpect(jsonPath("$.data.recipientData.name").value("John Smith"))
                .andReturn();

        // Extract certificate ID
        var responseJson = createResult.getResponse().getContentAsString();
        var responseNode = objectMapper.readTree(responseJson);
        var certificateId = UUID.fromString(responseNode.get("data").get("id").asText());

        // Step 2: Read certificate by ID
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", certificateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(certificateId.toString()))
                .andExpect(jsonPath("$.data.certificateNumber").value(certNumber));

        // Step 3: Read certificate by number
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/number/{number}", certNumber), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(certificateId.toString()))
                .andExpect(jsonPath("$.data.certificateNumber").value(certNumber));

        // Step 4: Update certificate
        var updateDTO = CertificateResponse.builder()
                .id(certificateId)
                .customerId(testCustomer.getId())
                .templateVersionId(templateVersionId)
                .certificateNumber(certNumber)
                .recipientData(Map.of(
                        "name", "John Smith Jr.",
                        "email", "john.smith.jr@example.com"
                ))
                .metadata(Map.of(
                        "purpose", "testing",
                        "updated", true
                ))
                .status(Certificate.CertificateStatus.ISSUED)
                .build();

        mockMvc.perform(
                        withTenantHeader(put("/api/certificates/{id}", certificateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientData.name").value("John Smith Jr."));

        // Step 5: Revoke certificate
        mockMvc.perform(
                        withTenantHeader(post("/api/certificates/{id}/revoke", certificateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        // Step 6: Verify revocation persisted
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", certificateId), testCustomer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        // Step 7: Delete certificate
        mockMvc.perform(
                        withTenantHeader(delete("/api/certificates/{id}", certificateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Step 8: Verify deletion
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", certificateId), testCustomer.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Certificate revocation workflow")
    void certificateRevocationWorkflow() throws Exception {
        // Create certificate
        var certNumber = "REVOKE-" + System.currentTimeMillis();
        var generateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber(certNumber)
                .recipientData(Map.of("name", "Revoke Test User"))
                .synchronous(true)
                .build();

        var createResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var responseNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        var certificateId = UUID.fromString(responseNode.get("data").get("id").asText());

        // Verify initial status
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", certificateId), testCustomer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(org.hamcrest.Matchers.not("REVOKED")));

        // Revoke the certificate
        mockMvc.perform(
                        withTenantHeader(post("/api/certificates/{id}/revoke", certificateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Certificate revoked successfully"))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        // Verify revoked status persisted
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", certificateId), testCustomer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    @DisplayName("E2E: Certificate filtering by status")
    void certificateFilteringByStatus() throws Exception {
        // Create multiple certificates
        var timestamp = System.currentTimeMillis();

        for (int i = 1; i <= 3; i++) {
            var generateRequest = GenerateCertificateRequest.builder()
                    .templateVersionId(templateVersionId)
                    .certificateNumber("FILTER-STATUS-" + timestamp + "-" + i)
                    .recipientData(Map.of("name", "User " + i))
                    .synchronous(true)
                    .build();

            mockMvc.perform(
                            withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(generateRequest)))
                    .andExpect(status().isCreated());
        }

        // Filter by status
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates")
                                .param("status", "ISSUED"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("E2E: Certificate filtering by customer ID")
    void certificateFilteringByCustomerId() throws Exception {
        // Create a certificate
        var generateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber("FILTER-CUSTOMER-" + System.currentTimeMillis())
                .recipientData(Map.of("name", "Customer Filter Test"))
                .synchronous(true)
                .build();

        mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isCreated());

        // Filter by customer ID
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates")
                                .param("customerId", testCustomer.getId().toString()), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("E2E: Certificate filtering by template version ID")
    void certificateFilteringByTemplateVersionId() throws Exception {
        // Create a certificate
        var generateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber("FILTER-VERSION-" + System.currentTimeMillis())
                .recipientData(Map.of("name", "Version Filter Test"))
                .synchronous(true)
                .build();

        mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isCreated());

        // Filter by template version ID
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates")
                                .param("templateVersionId", templateVersionId.toString()), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("E2E: Certificate not found - Invalid ID")
    void certificateNotFound_InvalidId() throws Exception {
        var nonExistentId = UUID.randomUUID();

        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", nonExistentId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Certificate not found - Invalid certificate number")
    void certificateNotFound_InvalidNumber() throws Exception {
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/number/{number}", "NONEXISTENT-12345"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Revoke non-existent certificate")
    void revokeNonExistentCertificate() throws Exception {
        var nonExistentId = UUID.randomUUID();

        mockMvc.perform(
                        withTenantHeader(post("/api/certificates/{id}/revoke", nonExistentId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                /*
                 * {
                 *      "success":false,
                 *      "message":"Certificate not found with ID: 9f05b1b1-a13d-4cdd-beb1-5765663d3e73",
                 *      "error":
                 *              {
                 *                  "errorCode":"INVALID_REQUEST",
                 *                  "errorType":"Validation Error",
                 *                  "details":[
                 *                                  "Certificate not found with ID: 9f05b1b1-a13d-4cdd-beb1-5765663d3e73"
                 *                            ]
                 *              }
                 * }
                 * Expect the response body to contain "Certificate not found with ID: ...
                 * */
                .andExpect(jsonPath("$.message").value("Certificate not found with ID: " + nonExistentId.toString()));
    }

    @Test
    @DisplayName("E2E: Delete non-existent certificate")
    void deleteNonExistentCertificate() throws Exception {
        var nonExistentId = UUID.randomUUID();

        mockMvc.perform(
                        withTenantHeader(delete("/api/certificates/{id}", nonExistentId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Certificate with expiration dates")
    void certificateWithExpirationDates() throws Exception {
        var certNumber = "EXPIRY-" + System.currentTimeMillis();
        var now = LocalDateTime.now();
        var expiryDate = now.plusYears(2);

        var generateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber(certNumber)
                .recipientData(Map.of("name", "Expiry Test User"))
                .issuedAt(now)
                .expiresAt(expiryDate)
                .synchronous(true)
                .build();

        var result = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(generateRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.issuedAt").exists())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andReturn();

        // Verify dates in response
        var responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseNode.get("data").get("issuedAt")).isNotNull();
        assertThat(responseNode.get("data").get("expiresAt")).isNotNull();
    }

    @Test
    @DisplayName("E2E: Certificate download URL generation")
    void certificateDownloadUrlGeneration() throws Exception {
        // Create a certificate
        var certNumber = "URL" + System.currentTimeMillis();
        var generateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber(certNumber)
                .recipientData(Map.of("name", "Download URL Test"))
                .synchronous(true)
                .build();

        var createResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var responseNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        var certificateId = UUID.fromString(responseNode.get("data").get("id").asText());

        // Get download URL
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}/download-url", certificateId), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.downloadUrl").exists());
    }

    @Test
    @DisplayName("E2E: Certificate download URL with custom expiration")
    void certificateDownloadUrlWithCustomExpiration() throws Exception {
        // Create a certificate
        var certNumber = "DOWNLOAD-EXPIRY-" + System.currentTimeMillis();
        var generateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(templateVersionId)
                .certificateNumber(certNumber)
                .recipientData(Map.of("name", "Custom Expiry Test"))
                .synchronous(true)
                .build();

        var createResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var responseNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        var certificateId = UUID.fromString(responseNode.get("data").get("id").asText());

        // Get download URL with custom expiration (30 minutes)
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}/download-url", certificateId)
                                .param("expirationMinutes", "30"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").exists());
    }

    /**
     * Generate a unique schema name for testing.
     */
    private String generateUniqueSchema() {
        return "life" + UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");
    }
}
