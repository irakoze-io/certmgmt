package tech.seccertificate.certmgmt.integration.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
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
 * End-to-end test for the complete certificate generation workflow.
 * 
 * <p>Tests the full flow:
 * <ol>
 *   <li>Customer onboarding</li>
 *   <li>Template creation</li>
 *   <li>Template version creation</li>
 *   <li>Certificate generation</li>
 *   <li>Certificate retrieval</li>
 * </ol>
 */
@DisplayName("Certificate Generation End-to-End Tests")
class CertificateGenerationE2ETest extends BaseIntegrationTest {

    private Customer testCustomer;
    private Long templateId;
    private UUID templateVersionId;

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
    @DisplayName("Complete E2E: Customer → Template → Version → Certificate")
    void completeCertificateGenerationFlow() throws Exception {
        // Step 1: Onboard Customer
        var uniqueSchema = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");
        var uniqueDomain = uniqueSchema + System.currentTimeMillis() + ".example.com";
        
        testCustomer = createTestCustomer("E2E Test Customer", uniqueDomain, uniqueSchema);
        setTenantContext(testCustomer.getId());
        
        assertThat(testCustomer.getId()).isNotNull();
        assertThat(testCustomer.getTenantSchema()).isEqualTo(uniqueSchema);

        // Step 2: Create Template
        var templateCode = "E2E_TEMPLATE_" + System.currentTimeMillis();
        var templateDTO = TemplateDTO.builder()
                .customerId(testCustomer.getId())
                .name("E2E Test Template")
                .code(templateCode)
                .description("Template for E2E testing")
                .metadata(Map.of("category", "certification"))
                .build();

        var templateResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value(templateCode))
                .andReturn();

        var createdTemplate = objectMapper.readValue(
                templateResult.getResponse().getContentAsString(), 
                TemplateDTO.class);
        templateId = createdTemplate.getId();
        assertThat(templateId).isNotNull();

        // Step 3: Create Template Version
        var versionDTO = TemplateVersionDTO.builder()
                .version(1)
                .htmlContent("<html><body><h1>Certificate for {{name}}</h1><p>Email: {{email}}</p></body></html>")
                .fieldSchema(Map.of(
                        "name", "string",
                        "email", "string"
                ))
                .cssStyles("body { font-family: Arial; }")
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .build();

        var versionResult = mockMvc.perform(
                        withTenantHeader(post("/api/templates/{templateId}/versions", templateId), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(versionDTO)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        var createdVersion = objectMapper.readValue(
                versionResult.getResponse().getContentAsString(),
                TemplateVersionDTO.class);
        templateVersionId = createdVersion.getId();
        assertThat(templateVersionId).isNotNull();

        // Step 4: Generate Certificate
        var certificateRequest = GenerateCertificateRequest.builder()
                .templateVersionId(createdVersion.getId())
                .certificateNumber("CERT-E2E-001")
                .recipientData(Map.of(
                        "name", "John Doe",
                        "email", "john.doe@example.com"
                ))
                .synchronous(true)
                .build();

        var certificateResult = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(certificateRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.certificateNumber").value("CERT-E2E-001"))
                .andReturn();

        var createdCertificate = objectMapper.readValue(
                certificateResult.getResponse().getContentAsString(),
                CertificateDTO.class);
        assertThat(createdCertificate.getId()).isNotNull();
        assertThat(createdCertificate.getCertificateNumber()).isEqualTo("CERT-E2E-001");

        // Step 5: Retrieve Certificate
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates/{id}", createdCertificate.getId()), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdCertificate.getId()))
                .andExpect(jsonPath("$.certificateNumber").value("CERT-E2E-001"))
                .andExpect(jsonPath("$.recipientData.name").value("John Doe"))
                .andExpect(jsonPath("$.recipientData.email").value("john.doe@example.com"));
    }

    @Test
    @DisplayName("E2E: Multi-tenant isolation - Customer 1 cannot access Customer 2's templates")
    void multiTenantIsolationTest() throws Exception {
        // Create Customer 1
        var schema1 = UUID.randomUUID().toString().replaceAll("-", "").replaceAll("[0-9]", "");
        var customer1 = createTestCustomer("Customer 1", schema1 + ".example.com", schema1);
        setTenantContext(customer1.getId());

        // Create Template for Customer 1
        var templateCode1 = "TEMPLATE_C1_" + System.currentTimeMillis();
        var templateDTO1 = TemplateDTO.builder()
                .customerId(customer1.getId())
                .name("Customer 1 Template")
                .code(templateCode1)
                .metadata(Map.of())
                .build();

        var templateResult1 = mockMvc.perform(
                        withTenantHeader(post("/api/templates"), customer1.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateDTO1)))
                .andExpect(status().isCreated())
                .andReturn();

        var template1 = objectMapper.readValue(
                templateResult1.getResponse().getContentAsString(), 
                TemplateDTO.class);

        // Create Customer 2
        var schema2 = UUID.randomUUID().toString().replaceAll("-", "").replaceAll("[0-9]", "");
        var customer2 = createTestCustomer("Customer 2", schema2 + ".example.com", schema2);
        setTenantContext(customer2.getId());

        // Customer 2 should NOT be able to access Customer 1's template
        mockMvc.perform(
                        withTenantHeader(get("/api/templates/{id}", template1.getId()), customer2.getId()))
                .andDo(print())
                .andExpect(status().isNotFound()); // Should not find template from different tenant
    }
}
