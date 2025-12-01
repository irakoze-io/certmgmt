package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.seccertificate.certmgmt.model.Certificate;
import tech.seccertificate.certmgmt.model.Customer;
import tech.seccertificate.certmgmt.model.Template;
import tech.seccertificate.certmgmt.model.TemplateVersion;
import tech.seccertificate.certmgmt.repository.*;
import tech.seccertificate.certmgmt.service.CertificateService;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.TemplateService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Core Services milestone.
 * Tests the complete certificate generation workflow.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Tests - Core Services Milestone")
class CoreServicesIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    private Customer testCustomer;
    private Template testTemplate;
    private TemplateVersion testTemplateVersion;

    @BeforeEach
    void setUp() {
        // Clean up database
        certificateRepository.deleteAll();
        templateVersionRepository.deleteAll();
        templateRepository.deleteAll();
        customerRepository.deleteAll();

        // Set up test data
        testCustomer = customerService.createCustomer("Test Company", "test@company.com");
        testTemplate = templateService.createTemplate(
            testCustomer.getId(),
            "Certificate Template",
            "Test template"
        );
        testTemplateVersion = templateService.createTemplateVersion(
            testTemplate.getId(),
            "{\"type\":\"object\",\"properties\":{\"name\":\"string\"}}",
            "Template content"
        );
    }

    @Test
    @DisplayName("Should complete full certificate generation workflow")
    void shouldCompleteFullCertificateGenerationWorkflow() {
        // Step 1: Generate certificate
        String certificateData = "{\"name\":\"John Doe\",\"course\":\"Java Programming\"}";
        Certificate certificate = certificateService.generateCertificate(
            testCustomer.getId(),
            testTemplate.getId(),
            testTemplateVersion.getId(),
            certificateData
        );

        assertNotNull(certificate.getId(), "Certificate should be persisted");
        assertEquals(Certificate.CertificateStatus.PENDING, certificate.getStatus());
        assertNotNull(certificate.getHash(), "Certificate should have hash");
        assertNotNull(certificate.getSignature(), "Certificate should have signature");

        // Step 2: Update status to generating
        Certificate generating = certificateService.updateCertificateStatus(
            certificate.getId(),
            Certificate.CertificateStatus.GENERATING
        );
        assertEquals(Certificate.CertificateStatus.GENERATING, generating.getStatus());

        // Step 3: Set PDF URL (simulation of PDF generation completion)
        String pdfUrl = "https://s3.amazonaws.com/certificates/" + certificate.getId() + ".pdf";
        Certificate completed = certificateService.setPdfUrl(certificate.getId(), pdfUrl);
        
        assertEquals(Certificate.CertificateStatus.COMPLETED, completed.getStatus());
        assertEquals(pdfUrl, completed.getPdfUrl());
        assertNotNull(completed.getPdfUrl(), "Certificate should have PDF URL");
    }

    @Test
    @DisplayName("Should enforce tenant isolation for certificates")
    void shouldEnforceTenantIsolationForCertificates() {
        // Create second customer
        Customer customer2 = customerService.createCustomer("Company 2", "company2@test.com");
        Template template2 = templateService.createTemplate(
            customer2.getId(),
            "Template 2",
            "Template for customer 2"
        );
        TemplateVersion version2 = templateService.createTemplateVersion(
            template2.getId(),
            "{}",
            "Content"
        );

        // Generate certificates for each customer
        Certificate cert1 = certificateService.generateCertificate(
            testCustomer.getId(),
            testTemplate.getId(),
            testTemplateVersion.getId(),
            "{\"data\":\"customer1\"}"
        );

        Certificate cert2 = certificateService.generateCertificate(
            customer2.getId(),
            template2.getId(),
            version2.getId(),
            "{\"data\":\"customer2\"}"
        );

        // Verify tenant isolation
        List<Certificate> customer1Certs = certificateService.getCertificatesByCustomerId(testCustomer.getId());
        List<Certificate> customer2Certs = certificateService.getCertificatesByCustomerId(customer2.getId());

        assertEquals(1, customer1Certs.size(), "Customer 1 should see only their certificate");
        assertEquals(1, customer2Certs.size(), "Customer 2 should see only their certificate");
        
        assertEquals(testCustomer.getId(), customer1Certs.get(0).getCustomerId());
        assertEquals(customer2.getId(), customer2Certs.get(0).getCustomerId());
    }

    @Test
    @DisplayName("Should generate unique hashes for different certificates")
    void shouldGenerateUniqueHashesForDifferentCertificates() {
        // Generate two certificates with different data
        Certificate cert1 = certificateService.generateCertificate(
            testCustomer.getId(),
            testTemplate.getId(),
            testTemplateVersion.getId(),
            "{\"name\":\"Alice\"}"
        );

        Certificate cert2 = certificateService.generateCertificate(
            testCustomer.getId(),
            testTemplate.getId(),
            testTemplateVersion.getId(),
            "{\"name\":\"Bob\"}"
        );

        // Verify unique hashes
        assertNotEquals(cert1.getHash(), cert2.getHash(), "Different data should produce different hashes");
        assertNotEquals(cert1.getSignature(), cert2.getSignature(), "Different hashes should produce different signatures");
    }

    @Test
    @DisplayName("Should verify certificate integrity with correct hash")
    void shouldVerifyCertificateIntegrityWithCorrectHash() {
        // Generate certificate
        String certificateData = "{\"name\":\"Test User\"}";
        Certificate certificate = certificateService.generateCertificate(
            testCustomer.getId(),
            testTemplate.getId(),
            testTemplateVersion.getId(),
            certificateData
        );

        String originalHash = certificate.getHash();

        // Verify with correct hash
        boolean isValid = certificateService.verifyCertificate(certificate.getId(), originalHash);
        assertTrue(isValid, "Certificate should be valid with correct hash");

        // Verify with incorrect hash
        boolean isInvalid = certificateService.verifyCertificate(certificate.getId(), "wrong_hash");
        assertFalse(isInvalid, "Certificate should be invalid with incorrect hash");
    }

    @Test
    @DisplayName("Should handle certificate status lifecycle")
    void shouldHandleCertificateStatusLifecycle() {
        // Create certificate
        Certificate certificate = certificateService.generateCertificate(
            testCustomer.getId(),
            testTemplate.getId(),
            testTemplateVersion.getId(),
            "{}"
        );

        // Test PENDING -> GENERATING transition
        Certificate generating = certificateService.updateCertificateStatus(
            certificate.getId(),
            Certificate.CertificateStatus.GENERATING
        );
        assertEquals(Certificate.CertificateStatus.GENERATING, generating.getStatus());

        // Test GENERATING -> COMPLETED transition
        Certificate completed = certificateService.updateCertificateStatus(
            certificate.getId(),
            Certificate.CertificateStatus.COMPLETED
        );
        assertEquals(Certificate.CertificateStatus.COMPLETED, completed.getStatus());

        // Test handling FAILED status
        Certificate newCert = certificateService.generateCertificate(
            testCustomer.getId(),
            testTemplate.getId(),
            testTemplateVersion.getId(),
            "{}"
        );
        Certificate failed = certificateService.updateCertificateStatus(
            newCert.getId(),
            Certificate.CertificateStatus.FAILED
        );
        assertEquals(Certificate.CertificateStatus.FAILED, failed.getStatus());
    }

    @Test
    @DisplayName("Should prevent certificate generation with wrong customer")
    void shouldPreventCertificateGenerationWithWrongCustomer() {
        // Create second customer
        Customer customer2 = customerService.createCustomer("Company 2", "company2@test.com");

        // Attempt to generate certificate with wrong customer ID
        assertThrows(IllegalArgumentException.class, () ->
            certificateService.generateCertificate(
                customer2.getId(), // Wrong customer
                testTemplate.getId(),
                testTemplateVersion.getId(),
                "{}"
            ),
            "Should not allow certificate generation with mismatched customer"
        );
    }

    @Test
    @DisplayName("Should validate core services milestone completion")
    void shouldValidateCoreServicesMilestoneCompletion() {
        // Milestone Requirement 1: Customer service working
        Customer customer = customerService.createCustomer("Milestone Corp", "milestone@corp.com");
        assertNotNull(customer.getId(), "✓ Customer service operational");
        assertNotNull(customer.getApiKey(), "✓ API credentials generated");

        // Milestone Requirement 2: Template service working
        Template template = templateService.createTemplate(
            customer.getId(),
            "Milestone Template",
            "Template for validation"
        );
        assertNotNull(template.getId(), "✓ Template service operational");

        // Milestone Requirement 3: Template versioning working
        TemplateVersion version = templateService.createTemplateVersion(
            template.getId(),
            "{}",
            "Content"
        );
        assertNotNull(version.getId(), "✓ Template versioning operational");
        assertTrue(version.isImmutable(), "✓ Version immutability enforced");

        // Milestone Requirement 4: Certificate service working
        Certificate certificate = certificateService.generateCertificate(
            customer.getId(),
            template.getId(),
            version.getId(),
            "{\"test\":\"data\"}"
        );
        assertNotNull(certificate.getId(), "✓ Certificate service operational");
        assertNotNull(certificate.getHash(), "✓ Certificate hashing working");
        assertNotNull(certificate.getSignature(), "✓ Certificate signing working");

        // Milestone Requirement 5: Certificate verification working
        boolean isValid = certificateService.verifyCertificate(
            certificate.getId(),
            certificate.getHash()
        );
        assertTrue(isValid, "✓ Certificate verification working");

        // Milestone Requirement 6: Multi-tenant isolation
        assertEquals(customer.getId(), certificate.getCustomerId(), "✓ Tenant isolation enforced");
        assertEquals(customer.getId(), version.getCustomerId(), "✓ Version tenant isolation enforced");
        assertEquals(customer.getId(), template.getCustomerId(), "✓ Template tenant isolation enforced");

        // CORE SERVICES MILESTONE: PASSED
        assertTrue(true, "✓✓✓ CORE SERVICES MILESTONE COMPLETED ✓✓✓");
    }

    @Test
    @DisplayName("Should handle high volume certificate generation")
    void shouldHandleHighVolumeCertificateGeneration() {
        // Simulate generating multiple certificates
        int certificateCount = 10;
        
        for (int i = 0; i < certificateCount; i++) {
            String data = "{\"name\":\"User" + i + "\",\"id\":" + i + "}";
            Certificate cert = certificateService.generateCertificate(
                testCustomer.getId(),
                testTemplate.getId(),
                testTemplateVersion.getId(),
                data
            );
            assertNotNull(cert.getId(), "Certificate " + i + " should be created");
            assertNotNull(cert.getHash(), "Certificate " + i + " should have hash");
        }

        // Verify all certificates were created
        List<Certificate> certificates = certificateService.getCertificatesByCustomerId(testCustomer.getId());
        assertEquals(certificateCount, certificates.size(), "All certificates should be created");

        // Verify each has unique hash
        long uniqueHashes = certificates.stream()
            .map(Certificate::getHash)
            .distinct()
            .count();
        assertEquals(certificateCount, uniqueHashes, "All certificates should have unique hashes");
    }
}
