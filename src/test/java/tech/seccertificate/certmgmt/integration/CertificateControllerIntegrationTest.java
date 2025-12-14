package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import tech.seccertificate.certmgmt.dto.certificate.GenerateCertificateRequest;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("CertificateController Integration Tests")
class CertificateControllerIntegrationTest extends BaseIntegrationTest {

    private final static Logger log = LoggerFactory
            .getLogger(CertificateControllerIntegrationTest.class);

    private Customer testCustomer;
    private Template testTemplate;
    private TemplateVersion testTemplateVersion;

    @BeforeEach
    void setUp() {
        cleanup();
        initMockMvc();

        var uniqueCustomerName = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[0-9]", "");

        testCustomer = createTestCustomer("Test Customer", "test.example.com", uniqueCustomerName);
        setTenantContext(testCustomer.getId());
        testTemplate = createTestTemplate(testCustomer);
        testTemplateVersion = createTestTemplateVersion(testTemplate);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("POST /api/certificates - Should generate certificate")
    void generateCertificate_ValidRequest_ReturnsCreated() throws Exception {
        // Arrange
        var request = GenerateCertificateRequest.builder()
                .templateVersionId(testTemplateVersion.getId())
                .issuedBy(UUID.randomUUID())
                .recipientData(Map.of(
                        "name", "John Doe",
                        "email", "john@example.com"
                ))
                .synchronous(true)
                .build();


        // Act & Assert
        // This will fail initially until we have proper template versions
        // For now, we expect either 400/404 (bad request/not found) or 201 (created)
        var result = mockMvc.perform(
                        withTenantHeader(post("/api/certificates"), testCustomer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andReturn();

        // Accept either success or client error (template version may not exist)
        int status = result.getResponse().getStatus();
        assertThat(status).isEqualTo(201);
    }

    @Test
    @DisplayName("GET /api/certificates - Should return all certificates")
    void getAllCertificates_ReturnsAllCertificates() throws Exception {
        // Act & Assert
        mockMvc.perform(
                        withTenantHeader(get("/api/certificates"), testCustomer.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}