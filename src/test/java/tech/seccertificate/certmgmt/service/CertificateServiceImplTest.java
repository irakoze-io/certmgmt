package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.config.TenantSchemaValidator;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.CertificateHash;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.exception.CustomerNotFoundException;
import tech.seccertificate.certmgmt.repository.CertificateHashRepository;
import tech.seccertificate.certmgmt.repository.CertificateRepository;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateServiceImpl Unit Tests")
class CertificateServiceImplTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateHashRepository certificateHashRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TemplateService templateService;

    @Mock
    private TenantSchemaValidator tenantSchemaValidator;

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private CertificateServiceImpl certificateService;

    private Certificate validCertificate;
    private TemplateVersion publishedTemplateVersion;
    private Template validTemplate;
    private Customer validCustomer;
    private UUID certificateId;
    private UUID templateVersionId;

    @BeforeEach
    void setUp() {
        certificateId = UUID.randomUUID();
        templateVersionId = UUID.randomUUID();

        validTemplate = Template.builder()
                .id(1L)
                .customerId(1L)
                .name("Test Template")
                .code("TEST_CERT")
                .currentVersion(1)
                .build();

        publishedTemplateVersion = TemplateVersion.builder()
                .id(templateVersionId)
                .template(validTemplate)
                .version(1)
                .htmlContent("<html>Test</html>")
                .fieldSchema("{\"fields\": []}")
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .createdBy(UUID.randomUUID())
                .build();

        validCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .domain("example.com")
                .tenantSchema("example_com")
                .status(Customer.CustomerStatus.ACTIVE)
                .maxCertificatesPerMonth(1000)
                .build();

        validCertificate = Certificate.builder()
                .templateVersionId(templateVersionId)
                .recipientData("{\"name\": \"John Doe\"}")
                .customerId(1L)
                .build();

        TenantContext.setTenantSchema("example_com");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== generateCertificate Tests ====================

    @Test
    @DisplayName("Should generate certificate successfully")
    void generateCertificate_ValidCertificate_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));
        when(templateService.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(certificateRepository.existsByCertificateNumber(anyString())).thenReturn(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(certificateRepository.findByCustomerId(1L)).thenReturn(List.of());
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate cert = invocation.getArgument(0);
            cert.setId(certificateId);
            return cert;
        });
        when(certificateHashRepository.save(any(CertificateHash.class))).thenReturn(new CertificateHash());
        
        // Mock PDF generation and storage
        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        pdfOutput.writeBytes("PDF content".getBytes());
        when(pdfGenerationService.generatePdf(any(TemplateVersion.class), any(Certificate.class)))
                .thenReturn(pdfOutput);
        when(storageService.getDefaultBucketName()).thenReturn("certificates");
        doNothing().when(storageService).uploadFile(anyString(), anyString(), any(byte[].class), anyString());

        // Act
        Certificate result = certificateService.generateCertificate(validCertificate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCertificateNumber()).isNotNull();
        assertThat(result.getStatus()).isIn(
                Certificate.CertificateStatus.PENDING,
                Certificate.CertificateStatus.PROCESSING,
                Certificate.CertificateStatus.ISSUED
        );
        verify(certificateRepository, atLeastOnce()).save(any(Certificate.class));
        verify(tenantSchemaValidator).validateTenantSchema("generateCertificate");
        verify(pdfGenerationService).generatePdf(any(TemplateVersion.class), any(Certificate.class));
        verify(storageService).uploadFile(anyString(), anyString(), any(byte[].class), eq("application/pdf"));
    }

    @Test
    @DisplayName("Should set customer ID from tenant context when not provided")
    void generateCertificate_NoCustomerId_SetsFromContext() {
        // Arrange
        validCertificate.setCustomerId(null);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));
        when(templateService.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(certificateRepository.existsByCertificateNumber(anyString())).thenReturn(false);
        when(customerRepository.findByTenantSchema("example_com")).thenReturn(Optional.of(validCustomer));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(certificateRepository.findByCustomerId(1L)).thenReturn(List.of());
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate cert = invocation.getArgument(0);
            cert.setId(certificateId);
            return cert;
        });
        when(certificateHashRepository.save(any(CertificateHash.class))).thenReturn(new CertificateHash());
        
        // Mock PDF generation and storage
        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        pdfOutput.writeBytes("PDF content".getBytes());
        when(pdfGenerationService.generatePdf(any(TemplateVersion.class), any(Certificate.class)))
                .thenReturn(pdfOutput);
        when(storageService.getDefaultBucketName()).thenReturn("certificates");
        doNothing().when(storageService).uploadFile(anyString(), anyString(), any(byte[].class), anyString());

        // Act
        Certificate result = certificateService.generateCertificate(validCertificate);

        // Assert
        assertThat(result.getCustomerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw exception when certificate number is already taken")
    void generateCertificate_DuplicateNumber_ThrowsException() {
        // Arrange
        validCertificate.setCertificateNumber("EXISTING-123");
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));
        when(certificateRepository.existsByCertificateNumber("EXISTING-123")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> certificateService.generateCertificate(validCertificate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate number is already in use");
    }

    @Test
    @DisplayName("Should throw exception when recipient data is missing")
    void generateCertificate_NoRecipientData_ThrowsException() {
        // Arrange
        validCertificate.setRecipientData(null);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));

        // Act & Assert
        assertThatThrownBy(() -> certificateService.generateCertificate(validCertificate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Recipient data is required");
    }

    @Test
    @DisplayName("Should throw exception when customer reaches monthly limit")
    void generateCertificate_MonthlyLimitReached_ThrowsException() {
        // Arrange
        validCustomer.setMaxCertificatesPerMonth(1);
        Certificate existingCert = Certificate.builder()
                .id(UUID.randomUUID())
                .customerId(1L)
                .issuedAt(LocalDateTime.now())
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));
        when(templateService.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(certificateRepository.existsByCertificateNumber(anyString())).thenReturn(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(certificateRepository.findByCustomerId(1L)).thenReturn(List.of(existingCert));

        // Act & Assert
        assertThatThrownBy(() -> certificateService.generateCertificate(validCertificate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monthly certificate limit");
    }

    // ==================== generateCertificateAsync Tests ====================

    @Test
    @DisplayName("Should generate certificate asynchronously")
    void generateCertificateAsync_ValidCertificate_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));
        when(templateService.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(certificateRepository.existsByCertificateNumber(anyString())).thenReturn(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(certificateRepository.findByCustomerId(1L)).thenReturn(List.of());
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate cert = invocation.getArgument(0);
            cert.setId(certificateId);
            return cert;
        });

        // Act
        Certificate result = certificateService.generateCertificateAsync(validCertificate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Certificate.CertificateStatus.PENDING);
        verify(certificateRepository).save(any(Certificate.class));
    }

    // ==================== generateCertificatesBatch Tests ====================

    @Test
    @DisplayName("Should generate multiple certificates in batch")
    void generateCertificatesBatch_ValidList_Success() {
        // Arrange
        Certificate cert1 = Certificate.builder()
                .templateVersionId(templateVersionId)
                .recipientData("{\"name\": \"John\"}")
                .customerId(1L)
                .build();
        Certificate cert2 = Certificate.builder()
                .templateVersionId(templateVersionId)
                .recipientData("{\"name\": \"Jane\"}")
                .customerId(1L)
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));
        when(templateService.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(certificateRepository.existsByCertificateNumber(anyString())).thenReturn(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(validCustomer));
        when(certificateRepository.findByCustomerId(1L)).thenReturn(List.of());
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate cert = invocation.getArgument(0);
            cert.setId(UUID.randomUUID());
            return cert;
        });
        when(certificateHashRepository.save(any(CertificateHash.class))).thenReturn(new CertificateHash());
        
        // Mock PDF generation and storage
        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        pdfOutput.writeBytes("PDF content".getBytes());
        when(pdfGenerationService.generatePdf(any(TemplateVersion.class), any(Certificate.class)))
                .thenReturn(pdfOutput);
        when(storageService.getDefaultBucketName()).thenReturn("certificates");
        doNothing().when(storageService).uploadFile(anyString(), anyString(), any(byte[].class), anyString());

        // Act
        List<Certificate> results = certificateService.generateCertificatesBatch(List.of(cert1, cert2));

        // Assert
        assertThat(results).hasSize(2);
        verify(certificateRepository, atLeast(2)).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should throw exception when batch list is empty")
    void generateCertificatesBatch_EmptyList_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());

        // Act & Assert
        assertThatThrownBy(() -> certificateService.generateCertificatesBatch(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    // ==================== findById Tests ====================

    @Test
    @DisplayName("Should find certificate by ID")
    void findById_ExistingCertificate_ReturnsCertificate() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        validCertificate.setId(certificateId);
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));

        // Act
        Optional<Certificate> result = certificateService.findById(certificateId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(validCertificate);
    }

    // ==================== findByCertificateNumber Tests ====================

    @Test
    @DisplayName("Should find certificate by certificate number")
    void findByCertificateNumber_ExistingNumber_ReturnsCertificate() {
        // Arrange
        String certNumber = "TEST-20240101-ABC123";
        validCertificate.setCertificateNumber(certNumber);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findByCertificateNumber(certNumber)).thenReturn(Optional.of(validCertificate));

        // Act
        Optional<Certificate> result = certificateService.findByCertificateNumber(certNumber);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getCertificateNumber()).isEqualTo(certNumber);
    }

    @Test
    @DisplayName("Should return empty when certificate number is null")
    void findByCertificateNumber_NullNumber_ReturnsEmpty() {
        // Act
        Optional<Certificate> result = certificateService.findByCertificateNumber(null);

        // Assert
        assertThat(result).isEmpty();
        verify(certificateRepository, never()).findByCertificateNumber(anyString());
    }

    // ==================== findByCustomerId Tests ====================

    @Test
    @DisplayName("Should find certificates by customer ID")
    void findByCustomerId_ExistingCustomer_ReturnsCertificates() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findByCustomerId(1L)).thenReturn(List.of(validCertificate));

        // Act
        List<Certificate> results = certificateService.findByCustomerId(1L);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo(validCertificate);
    }

    // ==================== findByStatus Tests ====================

    @Test
    @DisplayName("Should find certificates by status")
    void findByStatus_ValidStatus_ReturnsCertificates() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findByStatus(Certificate.CertificateStatus.ISSUED))
                .thenReturn(List.of(validCertificate));

        // Act
        List<Certificate> results = certificateService.findByStatus(Certificate.CertificateStatus.ISSUED);

        // Assert
        assertThat(results).hasSize(1);
    }

    // ==================== findIssuedBetween Tests ====================

    @Test
    @DisplayName("Should find certificates issued between dates")
    void findIssuedBetween_ValidDates_ReturnsCertificates() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findByIssuedAtBetween(start, end)).thenReturn(List.of(validCertificate));

        // Act
        List<Certificate> results = certificateService.findIssuedBetween(start, end);

        // Assert
        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Should throw exception when start date is null")
    void findIssuedBetween_NullStart_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());

        // Act & Assert
        assertThatThrownBy(() -> certificateService.findIssuedBetween(null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when start is after end")
    void findIssuedBetween_StartAfterEnd_ThrowsException() {
        // Arrange
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = LocalDateTime.now().minusDays(1);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());

        // Act & Assert
        assertThatThrownBy(() -> certificateService.findIssuedBetween(start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before end date");
    }

    // ==================== updateCertificate Tests ====================

    @Test
    @DisplayName("Should update certificate successfully")
    void updateCertificate_ValidUpdate_Success() {
        // Arrange
        validCertificate.setId(certificateId);
        Certificate updatedCert = Certificate.builder()
                .id(certificateId)
                .recipientData("{\"name\": \"Updated Name\"}")
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(certificateRepository.save(any(Certificate.class))).thenReturn(validCertificate);

        // Act
        Certificate result = certificateService.updateCertificate(updatedCert);

        // Assert
        assertThat(result).isNotNull();
        verify(certificateRepository).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent certificate")
    void updateCertificate_NonExistent_ThrowsException() {
        // Arrange
        validCertificate.setId(certificateId);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> certificateService.updateCertificate(validCertificate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate not found");
    }

    // ==================== updateStatus Tests ====================

    @Test
    @DisplayName("Should update certificate status")
    void updateStatus_ValidStatus_Success() {
        // Arrange
        validCertificate.setId(certificateId);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(certificateRepository.save(any(Certificate.class))).thenReturn(validCertificate);

        // Act
        Certificate result = certificateService.updateStatus(certificateId, Certificate.CertificateStatus.ISSUED);

        // Assert
        assertThat(result.getStatus()).isEqualTo(Certificate.CertificateStatus.ISSUED);
        verify(certificateRepository).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Should throw exception when status is null")
    void updateStatus_NullStatus_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> certificateService.updateStatus(certificateId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status cannot be null");
    }

    // ==================== markAsIssued Tests ====================

    @Test
    @DisplayName("Should mark certificate as issued")
    void markAsIssued_ValidCertificate_Success() {
        // Arrange
        UUID issuedBy = UUID.randomUUID();
        validCertificate.setId(certificateId);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(certificateRepository.save(any(Certificate.class))).thenReturn(validCertificate);

        // Act
        Certificate result = certificateService.markAsIssued(certificateId, issuedBy);

        // Assert
        assertThat(result.getStatus()).isEqualTo(Certificate.CertificateStatus.ISSUED);
        assertThat(result.getIssuedBy()).isEqualTo(issuedBy);
        assertThat(result.getIssuedAt()).isNotNull();
    }

    // ==================== revokeCertificate Tests ====================

    @Test
    @DisplayName("Should revoke certificate")
    void revokeCertificate_ValidCertificate_Success() {
        // Arrange
        validCertificate.setId(certificateId);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(certificateRepository.save(any(Certificate.class))).thenReturn(validCertificate);

        // Act
        Certificate result = certificateService.revokeCertificate(certificateId);

        // Assert
        assertThat(result.getStatus()).isEqualTo(Certificate.CertificateStatus.REVOKED);
    }

    // ==================== deleteCertificate Tests ====================

    @Test
    @DisplayName("Should delete certificate and its hash")
    void deleteCertificate_ValidCertificate_Success() {
        // Arrange
        validCertificate.setId(certificateId);
        CertificateHash hash = new CertificateHash();
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(certificateHashRepository.findByCertificateId(certificateId)).thenReturn(Optional.of(hash));
        doNothing().when(certificateHashRepository).delete(hash);
        doNothing().when(certificateRepository).delete(validCertificate);

        // Act
        certificateService.deleteCertificate(certificateId);

        // Assert
        verify(certificateHashRepository).delete(hash);
        verify(certificateRepository).delete(validCertificate);
    }

    // ==================== isCertificateNumberTaken Tests ====================

    @Test
    @DisplayName("Should return true when certificate number is taken")
    void isCertificateNumberTaken_TakenNumber_ReturnsTrue() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.existsByCertificateNumber("TAKEN-123")).thenReturn(true);

        // Act
        boolean result = certificateService.isCertificateNumberTaken("TAKEN-123");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when certificate number is null")
    void isCertificateNumberTaken_NullNumber_ReturnsFalse() {
        // Act
        boolean result = certificateService.isCertificateNumberTaken(null);

        // Assert
        assertThat(result).isFalse();
        verify(certificateRepository, never()).existsByCertificateNumber(anyString());
    }

    // ==================== generateCertificateNumber Tests ====================

    @Test
    @DisplayName("Should generate certificate number with template code")
    void generateCertificateNumber_WithTemplateCode_GeneratesCorrectFormat() {
        // Act
        String result = certificateService.generateCertificateNumber("JAVA_CERT");

        // Assert
        assertThat(result).startsWith("JAVA_CERT-");
        assertThat(result).hasSize(25); // JAVA_CERT-20240101-ABC12 format
    }

    @Test
    @DisplayName("Should generate default certificate number when template code is null")
    void generateCertificateNumber_NullCode_GeneratesDefault() {
        // Act
        String result = certificateService.generateCertificateNumber(null);

        // Assert
        assertThat(result).startsWith("CERT-");
    }

    // ==================== validateCertificate Tests ====================

    @Test
    @DisplayName("Should validate certificate successfully")
    void validateCertificate_ValidCertificate_NoException() {
        // Arrange
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));

        // Act & Assert
        assertThatCode(() -> certificateService.validateCertificate(validCertificate))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when certificate is null")
    void validateCertificate_NullCertificate_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> certificateService.validateCertificate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when template version is not published")
    void validateCertificate_NotPublishedTemplate_ThrowsException() {
        // Arrange
        publishedTemplateVersion.setStatus(TemplateVersion.TemplateVersionStatus.DRAFT);
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));

        // Act & Assert
        assertThatThrownBy(() -> certificateService.validateCertificate(validCertificate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be PUBLISHED");
    }

    @Test
    @DisplayName("Should throw exception when issue date is after expiry date")
    void validateCertificate_IssueDateAfterExpiry_ThrowsException() {
        // Arrange
        validCertificate.setIssuedAt(LocalDateTime.now());
        validCertificate.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(templateService.findVersionById(templateVersionId)).thenReturn(Optional.of(publishedTemplateVersion));

        // Act & Assert
        assertThatThrownBy(() -> certificateService.validateCertificate(validCertificate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before expiration date");
    }

    // ==================== verifyCertificate Tests ====================

    @Test
    @DisplayName("Should verify certificate successfully")
    void verifyCertificate_IssuedCertificateWithHash_ReturnsTrue() {
        // Arrange
        validCertificate.setId(certificateId);
        validCertificate.setStatus(Certificate.CertificateStatus.ISSUED);
        CertificateHash hash = new CertificateHash();
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(certificateHashRepository.findByCertificateId(certificateId)).thenReturn(Optional.of(hash));

        // Act
        boolean result = certificateService.verifyCertificate(certificateId);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when certificate is not issued")
    void verifyCertificate_NotIssuedCertificate_ReturnsFalse() {
        // Arrange
        validCertificate.setId(certificateId);
        validCertificate.setStatus(Certificate.CertificateStatus.PENDING);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));

        // Act
        boolean result = certificateService.verifyCertificate(certificateId);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when certificate hash not found")
    void verifyCertificate_NoHash_ReturnsFalse() {
        // Arrange
        validCertificate.setId(certificateId);
        validCertificate.setStatus(Certificate.CertificateStatus.ISSUED);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(certificateHashRepository.findByCertificateId(certificateId)).thenReturn(Optional.empty());

        // Act
        boolean result = certificateService.verifyCertificate(certificateId);

        // Assert
        assertThat(result).isFalse();
    }

    // ==================== countByCustomerId Tests ====================

    @Test
    @DisplayName("Should count certificates by customer ID")
    void countByCustomerId_ValidCustomerId_ReturnsCount() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.countByCustomerId(1L)).thenReturn(5L);

        // Act
        long result = certificateService.countByCustomerId(1L);

        // Assert
        assertThat(result).isEqualTo(5L);
    }

    // ==================== countByStatus Tests ====================

    @Test
    @DisplayName("Should count certificates by status")
    void countByStatus_ValidStatus_ReturnsCount() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.countByStatus(Certificate.CertificateStatus.ISSUED)).thenReturn(10L);

        // Act
        long result = certificateService.countByStatus(Certificate.CertificateStatus.ISSUED);

        // Assert
        assertThat(result).isEqualTo(10L);
    }

    // ==================== getCertificateDownloadUrl Tests ====================

    @Test
    @DisplayName("Should generate signed download URL for certificate")
    void getCertificateDownloadUrl_ValidCertificate_ReturnsSignedUrl() {
        // Arrange
        validCertificate.setId(certificateId);
        validCertificate.setStoragePath("/path/to/cert.pdf");
        String expectedUrl = "http://minio:9000/certificates/path/to/cert.pdf?signature=xyz";
        
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(storageService.getDefaultBucketName()).thenReturn("certificates");
        when(storageService.generateSignedUrl("certificates", "/path/to/cert.pdf", 60))
                .thenReturn(expectedUrl);

        // Act
        String result = certificateService.getCertificateDownloadUrl(certificateId, 60);

        // Assert
        assertThat(result).isEqualTo(expectedUrl);
        verify(storageService).generateSignedUrl("certificates", "/path/to/cert.pdf", 60);
    }

    @Test
    @DisplayName("Should use default expiration when null provided")
    void getCertificateDownloadUrl_NullExpiration_UsesDefault() {
        // Arrange
        validCertificate.setId(certificateId);
        validCertificate.setStoragePath("/path/to/cert.pdf");
        String expectedUrl = "http://minio:9000/certificates/path/to/cert.pdf?signature=xyz";
        
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));
        when(storageService.getDefaultBucketName()).thenReturn("certificates");
        when(storageService.generateSignedUrl("certificates", "/path/to/cert.pdf", 60))
                .thenReturn(expectedUrl);

        // Act
        String result = certificateService.getCertificateDownloadUrl(certificateId, null);

        // Assert
        assertThat(result).isEqualTo(expectedUrl);
        verify(storageService).generateSignedUrl("certificates", "/path/to/cert.pdf", 60);
    }

    @Test
    @DisplayName("Should throw exception when certificate has no storage path")
    void getCertificateDownloadUrl_NoStoragePath_ThrowsException() {
        // Arrange
        validCertificate.setId(certificateId);
        validCertificate.setStoragePath(null);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(certificateRepository.findById(certificateId)).thenReturn(Optional.of(validCertificate));

        // Act & Assert
        assertThatThrownBy(() -> certificateService.getCertificateDownloadUrl(certificateId, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not have a storage path");
    }
}
