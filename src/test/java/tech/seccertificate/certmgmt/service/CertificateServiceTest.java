package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.seccertificate.certmgmt.model.Certificate;
import tech.seccertificate.certmgmt.model.TemplateVersion;
import tech.seccertificate.certmgmt.repository.CertificateRepository;
import tech.seccertificate.certmgmt.repository.TemplateVersionRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateService Unit Tests - Core Services Milestone")
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private TemplateVersionRepository templateVersionRepository;

    @InjectMocks
    private CertificateService certificateService;

    @Test
    @DisplayName("Should generate certificate with valid data")
    void shouldGenerateCertificateWithValidData() {
        // Arrange
        Long customerId = 1L;
        Long templateId = 1L;
        Long templateVersionId = 1L;
        String certificateData = "{\"name\":\"John Doe\",\"course\":\"Java\"}";
        
        TemplateVersion templateVersion = new TemplateVersion(templateId, customerId, 1, "{}");
        templateVersion.setId(templateVersionId);
        
        when(templateVersionRepository.findById(templateVersionId)).thenReturn(Optional.of(templateVersion));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate cert = invocation.getArgument(0);
            cert.setId(1L);
            return cert;
        });

        // Act
        Certificate result = certificateService.generateCertificate(
            customerId, templateId, templateVersionId, certificateData
        );

        // Assert
        assertNotNull(result);
        assertEquals(customerId, result.getCustomerId());
        assertEquals(templateId, result.getTemplateId());
        assertEquals(templateVersionId, result.getTemplateVersionId());
        assertEquals(certificateData, result.getCertificateData());
        assertEquals(Certificate.CertificateStatus.PENDING, result.getStatus());
        assertNotNull(result.getHash());
        assertNotNull(result.getSignature());
        assertTrue(result.getSignature().startsWith("sig_"));
        verify(certificateRepository, times(1)).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should throw exception when generating certificate with null customer ID")
    void shouldThrowExceptionWhenGeneratingCertificateWithNullCustomerId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> certificateService.generateCertificate(null, 1L, 1L, "{}")
        );
        assertEquals("Customer ID cannot be null", exception.getMessage());
        verify(certificateRepository, never()).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should throw exception when generating certificate with null template version ID")
    void shouldThrowExceptionWhenGeneratingCertificateWithNullTemplateVersionId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> certificateService.generateCertificate(1L, 1L, null, "{}")
        );
        assertEquals("Template version ID cannot be null", exception.getMessage());
        verify(certificateRepository, never()).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should throw exception when template version does not exist")
    void shouldThrowExceptionWhenTemplateVersionDoesNotExist() {
        // Arrange
        when(templateVersionRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> certificateService.generateCertificate(1L, 1L, 999L, "{}")
        );
        verify(certificateRepository, never()).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should throw exception when template version does not belong to customer")
    void shouldThrowExceptionWhenTemplateVersionDoesNotBelongToCustomer() {
        // Arrange
        Long customerId = 1L;
        Long wrongCustomerId = 2L;
        Long templateVersionId = 1L;
        
        TemplateVersion templateVersion = new TemplateVersion(1L, wrongCustomerId, 1, "{}");
        templateVersion.setId(templateVersionId);
        when(templateVersionRepository.findById(templateVersionId)).thenReturn(Optional.of(templateVersion));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> certificateService.generateCertificate(customerId, 1L, templateVersionId, "{}")
        );
        assertTrue(exception.getMessage().contains("does not belong to customer"));
        verify(certificateRepository, never()).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should get certificate by ID")
    void shouldGetCertificateById() {
        // Arrange
        Long certificateId = 1L;
        Certificate certificate = new Certificate(1L, 1L, 1L, "{}");
        certificate.setId(certificateId);
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certificate));

        // Act
        Optional<Certificate> result = certificateService.getCertificateById(certificateId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(certificateId, result.get().getId());
        verify(certificateRepository, times(1)).findById(certificateId);
    }

    @Test
    @DisplayName("Should get certificates by customer ID")
    void shouldGetCertificatesByCustomerId() {
        // Arrange
        Long customerId = 1L;
        Certificate cert1 = new Certificate(customerId, 1L, 1L, "{}");
        Certificate cert2 = new Certificate(customerId, 2L, 2L, "{}");
        List<Certificate> certificates = Arrays.asList(cert1, cert2);
        when(certificateRepository.findByCustomerId(customerId)).thenReturn(certificates);

        // Act
        List<Certificate> result = certificateService.getCertificatesByCustomerId(customerId);

        // Assert
        assertEquals(2, result.size());
        assertEquals(customerId, result.get(0).getCustomerId());
        assertEquals(customerId, result.get(1).getCustomerId());
        verify(certificateRepository, times(1)).findByCustomerId(customerId);
    }

    @Test
    @DisplayName("Should update certificate status")
    void shouldUpdateCertificateStatus() {
        // Arrange
        Long certificateId = 1L;
        Certificate certificate = new Certificate(1L, 1L, 1L, "{}");
        certificate.setId(certificateId);
        certificate.setStatus(Certificate.CertificateStatus.PENDING);
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certificate));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Certificate result = certificateService.updateCertificateStatus(
            certificateId, Certificate.CertificateStatus.COMPLETED
        );

        // Assert
        assertEquals(Certificate.CertificateStatus.COMPLETED, result.getStatus());
        verify(certificateRepository, times(1)).save(certificate);
    }

    @Test
    @DisplayName("Should set PDF URL and mark certificate as completed")
    void shouldSetPdfUrlAndMarkCertificateAsCompleted() {
        // Arrange
        Long certificateId = 1L;
        String pdfUrl = "https://s3.amazonaws.com/certificates/cert-123.pdf";
        Certificate certificate = new Certificate(1L, 1L, 1L, "{}");
        certificate.setId(certificateId);
        certificate.setStatus(Certificate.CertificateStatus.GENERATING);
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certificate));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Certificate result = certificateService.setPdfUrl(certificateId, pdfUrl);

        // Assert
        assertEquals(pdfUrl, result.getPdfUrl());
        assertEquals(Certificate.CertificateStatus.COMPLETED, result.getStatus());
        verify(certificateRepository, times(1)).save(certificate);
    }

    @Test
    @DisplayName("Should verify certificate with correct hash")
    void shouldVerifyCertificateWithCorrectHash() {
        // Arrange
        Long certificateId = 1L;
        String certificateData = "{\"name\":\"John Doe\"}";
        String hash = "abc123def456"; // Hash would be calculated from data
        Certificate certificate = new Certificate(1L, 1L, 1L, certificateData);
        certificate.setId(certificateId);
        certificate.setHash(hash);
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certificate));

        // Act
        boolean result = certificateService.verifyCertificate(certificateId, hash);

        // Assert
        assertTrue(result);
        verify(certificateRepository, times(1)).findById(certificateId);
    }

    @Test
    @DisplayName("Should fail to verify certificate with incorrect hash")
    void shouldFailToVerifyCertificateWithIncorrectHash() {
        // Arrange
        Long certificateId = 1L;
        Certificate certificate = new Certificate(1L, 1L, 1L, "{}");
        certificate.setId(certificateId);
        certificate.setHash("correct_hash");
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certificate));

        // Act
        boolean result = certificateService.verifyCertificate(certificateId, "wrong_hash");

        // Assert
        assertFalse(result);
        verify(certificateRepository, times(1)).findById(certificateId);
    }

    @Test
    @DisplayName("Should generate SHA-256 hash for certificate data")
    void shouldGenerateSHA256HashForCertificateData() {
        // Arrange
        Long customerId = 1L;
        Long templateId = 1L;
        Long templateVersionId = 1L;
        String certificateData = "test data";
        
        TemplateVersion templateVersion = new TemplateVersion(templateId, customerId, 1, "{}");
        templateVersion.setId(templateVersionId);
        
        when(templateVersionRepository.findById(templateVersionId)).thenReturn(Optional.of(templateVersion));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Certificate result = certificateService.generateCertificate(
            customerId, templateId, templateVersionId, certificateData
        );

        // Assert
        assertNotNull(result.getHash());
        assertEquals(64, result.getHash().length()); // SHA-256 produces 64 hex characters
        verify(certificateRepository, times(1)).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should support all certificate status transitions")
    void shouldSupportAllCertificateStatusTransitions() {
        // Arrange
        Long certificateId = 1L;
        Certificate certificate = new Certificate(1L, 1L, 1L, "{}");
        certificate.setId(certificateId);
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(certificate));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act & Assert - Test all status transitions
        Certificate pending = certificateService.updateCertificateStatus(
            certificateId, Certificate.CertificateStatus.PENDING
        );
        assertEquals(Certificate.CertificateStatus.PENDING, pending.getStatus());

        Certificate generating = certificateService.updateCertificateStatus(
            certificateId, Certificate.CertificateStatus.GENERATING
        );
        assertEquals(Certificate.CertificateStatus.GENERATING, generating.getStatus());

        Certificate completed = certificateService.updateCertificateStatus(
            certificateId, Certificate.CertificateStatus.COMPLETED
        );
        assertEquals(Certificate.CertificateStatus.COMPLETED, completed.getStatus());

        Certificate failed = certificateService.updateCertificateStatus(
            certificateId, Certificate.CertificateStatus.FAILED
        );
        assertEquals(Certificate.CertificateStatus.FAILED, failed.getStatus());
    }
}
