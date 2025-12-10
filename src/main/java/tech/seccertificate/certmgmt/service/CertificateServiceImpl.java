package tech.seccertificate.certmgmt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.config.TenantSchemaValidator;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.CertificateHash;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.exception.CustomerNotFoundException;
import tech.seccertificate.certmgmt.repository.CertificateHashRepository;
import tech.seccertificate.certmgmt.repository.CertificateRepository;
import tech.seccertificate.certmgmt.repository.CustomerRepository;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Implementation of CertificateService.
 * Handles certificate generation (sync + async), CRUD operations, status management,
 * and certificate verification.
 *
 * <p>All operations require tenant context to be set (via TenantResolutionFilter
 * or programmatically using TenantService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final CertificateRepository certificateRepository;
    private final CertificateHashRepository certificateHashRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final TemplateService templateService;
    private final TenantSchemaValidator tenantSchemaValidator;
    private final PdfGenerationService pdfGenerationService;
    private final StorageService storageService;
    private final MessageQueueService messageQueueService;
    private final FieldSchemaValidator fieldSchemaValidator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Certificate generateCertificate(Certificate certificate, boolean isPreview) {
        log.info("Generating certificate {} for template version: {}",
                isPreview ? "preview" : "synchronously", certificate.getTemplateVersionId());

        tenantSchemaValidator.validateTenantSchema("generateCertificate");
        validateCertificate(certificate);

        // Set customer ID from tenant context if not provided
        if (certificate.getCustomerId() == null) {
            var customerId = getCustomerIdFromTenantContext();
            certificate.setCustomerId(customerId);
        }

        // Generate certificate number if not provided
        if (certificate.getCertificateNumber() == null || certificate.getCertificateNumber().isEmpty()) {
            var templateVersion = getTemplateVersion(certificate.getTemplateVersionId());
            var template = templateService.findById(templateVersion.getTemplate().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));
            certificate.setCertificateNumber(generateCertificateNumber(template.getCode()));
        }

        // Validate certificate number uniqueness
        if (isCertificateNumberTaken(certificate.getCertificateNumber())) {
            throw new IllegalArgumentException(
                    "Certificate number is already in use: " + certificate.getCertificateNumber()
            );
        }

        // Set defaults
        if (certificate.getStatus() == null) {
            certificate.setStatus(Certificate.CertificateStatus.PENDING);
        }
        if (certificate.getIssuedAt() == null) {
            certificate.setIssuedAt(LocalDateTime.now());
        }
        if (certificate.getRecipientData() == null || certificate.getRecipientData().isEmpty()) {
            throw new IllegalArgumentException("Recipient data is required");
        }

        // Set issuedBy from current authenticated user if not provided
        if (certificate.getIssuedBy() == null) {
            UUID currentUserId = getCurrentUserId();
            if (currentUserId != null) {
                certificate.setIssuedBy(currentUserId);
            }
        }

        // Validate customer limits
        validateCustomerLimits(certificate.getCustomerId());

        try {
            // Save certificate with PENDING status
            var savedCertificate = certificateRepository.save(certificate);
            log.info("Certificate created with ID: {} and status: {}",
                    savedCertificate.getId(), savedCertificate.getStatus());

            // TODO: Implement synchronous PDF generation
            // 1. Load template version
            // 2. Render HTML with recipient data
            // 3. Convert HTML to PDF
            // 4. Upload PDF to S3/MinIO
            // 5. Generate hash and sign
            // 6. Update certificate status to ISSUED

            // For now, mark as PROCESSING (will be updated by PDF generation)
            savedCertificate.setStatus(Certificate.CertificateStatus.PROCESSING);
            savedCertificate = certificateRepository.save(savedCertificate);

            // Generate PDF (preview or final)
            processCertificateGeneration(savedCertificate, isPreview);

            return savedCertificate;
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to create certificate due to data integrity violation", e);
            throw new IllegalArgumentException("Certificate data violates constraints: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Certificate generateCertificateAsync(Certificate certificate, boolean isPreview) {
        log.info("Generating certificate {} for template version: {}",
                isPreview ? "preview asynchronously" : "asynchronously", certificate.getTemplateVersionId());

        tenantSchemaValidator.validateTenantSchema("generateCertificateAsync");
        validateCertificate(certificate);

        if (certificate.getCustomerId() == null) {
            var customerId = getCustomerIdFromTenantContext();
            certificate.setCustomerId(customerId);
        }

        if (certificate.getCertificateNumber() == null || certificate.getCertificateNumber().isEmpty()) {
            var templateVersion = getTemplateVersion(certificate.getTemplateVersionId());
            var template = templateService.findById(templateVersion.getTemplate().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));
            certificate.setCertificateNumber(generateCertificateNumber(template.getCode()));
        }

        if (isCertificateNumberTaken(certificate.getCertificateNumber())) {
            throw new IllegalArgumentException(
                    "Certificate number is already in use: " + certificate.getCertificateNumber()
            );
        }

        certificate.setStatus(Certificate.CertificateStatus.PENDING);
        if (certificate.getIssuedAt() == null) {
            certificate.setIssuedAt(LocalDateTime.now());
        }
        if (certificate.getRecipientData() == null || certificate.getRecipientData().isEmpty()) {
            throw new IllegalArgumentException("Recipient data is required");
        }

        // Set issuedBy from current authenticated user if not provided
        if (certificate.getIssuedBy() == null) {
            UUID currentUserId = getCurrentUserId();
            if (currentUserId != null) {
                certificate.setIssuedBy(currentUserId);
            }
        }

        validateCustomerLimits(certificate.getCustomerId());

        try {
            var savedCertificate = certificateRepository.save(certificate);
            log.info("Certificate queued for async processing with ID: {}", savedCertificate.getId());

            // Send message to PDF generation queue
            messageQueueService.sendCertificateGenerationMessage(
                    savedCertificate.getId(),
                    TenantContext.getTenantSchema(),
                    isPreview
            );

            return savedCertificate;
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to create certificate due to data integrity violation", e);
            throw new IllegalArgumentException("Certificate data violates constraints: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public List<Certificate> generateCertificatesBatch(List<Certificate> certificates) {
        log.info("Generating {} certificates synchronously", certificates.size());

        tenantSchemaValidator.validateTenantSchema("generateCertificatesBatch");

        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("Certificates list cannot be null or empty");
        }

        return certificates.stream()
                .map(cert -> generateCertificate(cert, false))
                .toList();
    }

    @Override
    @Transactional
    public List<Certificate> generateCertificatesBatchAsync(List<Certificate> certificates) {
        log.info("Generating {} certificates asynchronously", certificates.size());

        tenantSchemaValidator.validateTenantSchema("generateCertificatesBatchAsync");

        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("Certificates list cannot be null or empty");
        }

        return certificates.stream()
                .map(cert -> generateCertificateAsync(cert, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findById(UUID certificateId) {
        tenantSchemaValidator.validateTenantSchema("findById");
        return certificateRepository.findById(certificateId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> findByCertificateNumber(String certificateNumber) {
        if (certificateNumber == null || certificateNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        tenantSchemaValidator.validateTenantSchema("findByCertificateNumber");
        return certificateRepository.findByCertificateNumber(certificateNumber.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByCustomerId(Long customerId) {
        tenantSchemaValidator.validateTenantSchema("findByCustomerId");
        return certificateRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByCustomerIdAndStatus(Long customerId, Certificate.CertificateStatus status) {
        tenantSchemaValidator.validateTenantSchema("findByCustomerIdAndStatus");
        return certificateRepository.findByCustomerIdAndStatus(customerId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByTemplateVersionId(UUID templateVersionId) {
        tenantSchemaValidator.validateTenantSchema("findByTemplateVersionId");
        return certificateRepository.findByTemplateVersionId(templateVersionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByStatus(Certificate.CertificateStatus status) {
        tenantSchemaValidator.validateTenantSchema("findByStatus");
        return certificateRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findIssuedBetween(LocalDateTime start, LocalDateTime end) {
        tenantSchemaValidator.validateTenantSchema("findIssuedBetween");
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        return certificateRepository.findByIssuedAtBetween(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findExpiringBefore(LocalDateTime date) {
        tenantSchemaValidator.validateTenantSchema("findExpiringBefore");
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return certificateRepository.findByExpiresAtBefore(date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findExpiringAfter(LocalDateTime date) {
        tenantSchemaValidator.validateTenantSchema("findExpiringAfter");
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return certificateRepository.findByExpiresAtAfter(date);
    }

    @Override
    @Transactional
    public Certificate updateCertificate(Certificate certificate) {
        if (certificate == null || certificate.getId() == null) {
            throw new IllegalArgumentException("Certificate and certificate ID must not be null");
        }

        tenantSchemaValidator.validateTenantSchema("updateCertificate");

        var existingCertificate = certificateRepository.findById(certificate.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificate.getId()
                ));

        // Update allowed fields
        if (certificate.getRecipientData() != null) {
            existingCertificate.setRecipientData(certificate.getRecipientData());
        }
        if (certificate.getMetadata() != null) {
            existingCertificate.setMetadata(certificate.getMetadata());
        }
        if (certificate.getExpiresAt() != null) {
            existingCertificate.setExpiresAt(certificate.getExpiresAt());
        }

        // Certificate number cannot be changed
        // Status should be updated via dedicated methods
        // Storage path and hash are managed by the system

        try {
            var updatedCertificate = certificateRepository.save(existingCertificate);
            log.info("Certificate updated with ID: {}", updatedCertificate.getId());
            return updatedCertificate;
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to update certificate due to data integrity violation", e);
            throw new IllegalArgumentException("Certificate data violates constraints: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Certificate updateStatus(UUID certificateId, Certificate.CertificateStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }

        tenantSchemaValidator.validateTenantSchema("updateStatus");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        certificate.setStatus(status);
        var updatedCertificate = certificateRepository.save(certificate);
        log.info("Certificate status updated to {} for ID: {}", status, certificateId);
        return updatedCertificate;
    }

    @Override
    @Transactional
    public Certificate markAsIssued(UUID certificateId, UUID issuedBy) {
        tenantSchemaValidator.validateTenantSchema("markAsIssued");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        certificate.setStatus(Certificate.CertificateStatus.ISSUED);
        // Only update issuedBy if a non-null value is provided (preserve existing value otherwise)
        if (issuedBy != null) {
            certificate.setIssuedBy(issuedBy);
        }
        if (certificate.getIssuedAt() == null) {
            certificate.setIssuedAt(LocalDateTime.now());
        }

        var updatedCertificate = certificateRepository.save(certificate);
        log.info("Certificate marked as ISSUED with ID: {}", certificateId);
        return updatedCertificate;
    }

    @Override
    @Transactional
    public Certificate markAsProcessing(UUID certificateId) {
        tenantSchemaValidator.validateTenantSchema("markAsProcessing");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        certificate.setStatus(Certificate.CertificateStatus.PROCESSING);
        var updatedCertificate = certificateRepository.save(certificate);
        log.info("Certificate marked as PROCESSING with ID: {}", certificateId);
        return updatedCertificate;
    }

    @Override
    @Transactional
    public Certificate revokeCertificate(UUID certificateId) {
        tenantSchemaValidator.validateTenantSchema("revokeCertificate");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        certificate.setStatus(Certificate.CertificateStatus.REVOKED);
        var updatedCertificate = certificateRepository.save(certificate);
        log.info("Certificate revoked with ID: {}", certificateId);
        return updatedCertificate;
    }

    @Override
    @Transactional
    public Certificate markAsFailed(UUID certificateId, String errorMessage) {
        tenantSchemaValidator.validateTenantSchema("markAsFailed");
        return markAsFailedInternal(certificateId, errorMessage);
    }

    /**
     * Internal helper method to mark certificate as failed.
     * This method does not have @Transactional to avoid self-invocation issues
     * when called from within an existing transaction.
     *
     * @param certificateId The certificate ID
     * @param errorMessage Optional error message to store in metadata
     * @return The failed certificate
     */
    private Certificate markAsFailedInternal(UUID certificateId, String errorMessage) {
        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        certificate.setStatus(Certificate.CertificateStatus.FAILED);

        if (errorMessage != null && !errorMessage.isEmpty()) {
            try {
                var currentMetadata = certificate.getMetadata();
                Map<String, Object> metadataMap;

                if (currentMetadata == null || currentMetadata.trim().isEmpty()) {
                    metadataMap = new HashMap<>();
                } else {
                    try {
                        metadataMap = objectMapper
                                .readValue(currentMetadata, objectMapper
                                        .getTypeFactory()
                                        .constructMapType(Map.class, String.class, Object.class)
                        );
                    } catch (Exception e) {
                        log.warn("Failed to parse existing metadata JSON for certificate {}, creating new metadata: {}",
                                certificateId, e.getMessage());
                        metadataMap = new HashMap<>();
                    }
                }
                metadataMap.put("error", errorMessage);
                metadataMap.put("errorTimestamp", LocalDateTime.now().toString());

                certificate.setMetadata(objectMapper.writeValueAsString(metadataMap));
            } catch (Exception e) {
                log.error("Failed to update metadata with error message for certificate {}: {}",
                        certificateId, e.getMessage(), e);

                certificate.setMetadata("{\"error\":\"" +
                        errorMessage.replace("\"", "\\\"") + "\"}");
            }
        }

        var updatedCertificate = certificateRepository.save(certificate);
        log.error("Certificate marked as FAILED with ID: {} - Error: {}", certificateId, errorMessage);
        return updatedCertificate;
    }

    @Override
    @Transactional
    public void deleteCertificate(UUID certificateId) {
        tenantSchemaValidator.validateTenantSchema("deleteCertificate");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        // Delete associated certificate hash if exists
        certificateHashRepository.findByCertificateId(certificateId)
                .ifPresent(certificateHashRepository::delete);

        certificateRepository.delete(certificate);
        log.info("Certificate deleted with ID: {}", certificateId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCertificateNumberTaken(String certificateNumber) {
        if (certificateNumber == null || certificateNumber.trim().isEmpty()) {
            return false;
        }
        tenantSchemaValidator.validateTenantSchema("isCertificateNumberTaken");
        return certificateRepository.existsByCertificateNumber(certificateNumber.trim());
    }

    @Override
    public String generateCertificateNumber(String templateCode) {
        // Format: {TEMPLATE_CODE}-{YYYYMMDD}-{RANDOM}
        // Example: JAVA-20240115-A3B2C1
        var datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        var randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        if (templateCode != null && !templateCode.isEmpty()) {
            return String.format("%s-%s-%s", templateCode.toUpperCase(), datePrefix, randomSuffix);
        }

        return String.format("CERT-%s-%s", datePrefix, randomSuffix);
    }

    @Override
    public void validateCertificate(Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("Certificate cannot be null");
        }

        // Validate template version exists and is PUBLISHED
        if (certificate.getTemplateVersionId() == null) {
            throw new IllegalArgumentException("Template version ID is required");
        }

        var templateVersion = getTemplateVersion(certificate.getTemplateVersionId());
        if (templateVersion.getStatus() != TemplateVersion.TemplateVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException(
                    "Template version must be PUBLISHED. Current status: " + templateVersion.getStatus()
            );
        }

        // Validate recipient data
        if (certificate.getRecipientData() == null || certificate.getRecipientData().isEmpty()) {
            throw new IllegalArgumentException("Recipient data is required");
        }

        // Validate recipient data against field schema
        try {
            fieldSchemaValidator.validateRecipientData(
                    certificate.getRecipientData(),
                    templateVersion.getFieldSchema()
            );
        } catch (IllegalArgumentException e) {
            log.error("Recipient data validation failed for certificate: {}", e.getMessage());
            throw e;
        }

        // Validate certificate number format if provided
        if (certificate.getCertificateNumber() != null && !certificate.getCertificateNumber().isEmpty()) {
            if (certificate.getCertificateNumber().length() > 100) {
                throw new IllegalArgumentException("Certificate number must not exceed 100 characters");
            }
        }

        // Validate dates
        if (certificate.getIssuedAt() != null && certificate.getExpiresAt() != null) {
            if (certificate.getIssuedAt().isAfter(certificate.getExpiresAt())) {
                throw new IllegalArgumentException("Issue date must be before expiration date");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyCertificate(UUID certificateId) {
        tenantSchemaValidator.validateTenantSchema("verifyCertificate");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        // Check certificate status
        if (certificate.getStatus() != Certificate.CertificateStatus.ISSUED) {
            log.warn("Certificate {} verification failed: status is {}", certificateId, certificate.getStatus());
            return false;
        }

        // Check if certificate hash exists
        var certificateHash = certificateHashRepository.findByCertificateId(certificateId);
        if (certificateHash.isEmpty()) {
            log.warn("Certificate {} verification failed: no hash found", certificateId);
            return false;
        }

        // Verify hash matches PDF content
        try {
            // Does the storage path exist?
            if (certificate.getStoragePath() == null || certificate.getStoragePath().isEmpty()) {
                log.warn("Certificate {} verification failed: no storage path", certificateId);
                return false;
            }

            var bucketName = storageService.getDefaultBucketName();
            if (!storageService.fileExists(bucketName, certificate.getStoragePath())) {
                log.warn("Certificate {} verification failed: PDF not found in storage at {}",
                        certificateId, certificate.getStoragePath());
                return false;
            }

            try (var pdfInputStream = storageService.downloadFile(bucketName, certificate.getStoragePath())) {
                var pdfBytes = pdfInputStream.readAllBytes();

                var computedHash = generateHashFromPdfContent(pdfBytes);

                var storedHash = certificateHash.get().getHashValue();
                var hashMatches = constantTimeEquals(computedHash, storedHash);

                if (!hashMatches) {
                    log.warn("Certificate {} verification failed: hash mismatch. Stored: {}, Computed: {}",
                            certificateId, storedHash.substring(0, Math.min(20, storedHash.length())),
                            computedHash.substring(0, Math.min(20, computedHash.length())));
                    return false;
                }

                log.info("Certificate {} verification successful: hash matches", certificateId);
                return true;
            }
        } catch (Exception e) {
            log.error("Certificate {} verification failed due to error: {}", certificateId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> verifyCertificateByHash(String hash) {
        // This is a public endpoint, so we don't validate tenant schema
        // Search across all tenant schemas to find the certificate with matching hash

        if (hash == null || hash.trim().isEmpty()) {
            log.warn("Hash verification failed: hash is null or empty");
            return Optional.empty();
        }

        // Get all active customers to search their tenant schemas
        var customers = customerService.findActiveCustomers();

        log.debug("Searching for certificate with hash across {} tenant schemas", customers.size());

        // Search each tenant schema for the hash
        for (var customer : customers) {
            try {
                var tenantSchema = customer.getTenantSchema();
                var certificateHashOpt = certificateHashRepository
                        .findByHashValueInSchema(tenantSchema, hash);

                if (certificateHashOpt.isPresent()) {
                    var certificateHash = certificateHashOpt.get();
                    var certificate = certificateHash.getCertificate();

                    // Verify certificate is issued and valid
                    if (certificate.getStatus() == Certificate.CertificateStatus.ISSUED) {
                        log.info("Certificate found with hash in tenant schema: {}", tenantSchema);
                        return Optional.of(certificate);
                    } else {
                        log.debug("Certificate found but not issued (status: {}) in schema: {}",
                                certificate.getStatus(), tenantSchema);
                    }
                }
            } catch (Exception e) {
                log.warn("Error searching tenant schema {} for hash: {}",
                        customer.getTenantSchema(), e.getMessage());
                // Continue searching other schemas
            }
        }

        log.debug("No certificate found with hash: {}", hash.substring(0, Math.min(20, hash.length())));
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCustomerId(Long customerId) {
        tenantSchemaValidator.validateTenantSchema("countByCustomerId");
        return certificateRepository.countByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(Certificate.CertificateStatus status) {
        tenantSchemaValidator.validateTenantSchema("countByStatus");
        return certificateRepository.countByStatus(status);
    }

    @Override
    public String getCertificateDownloadUrl(UUID certificateId, Integer expirationMinutes) {
        tenantSchemaValidator.validateTenantSchema("getCertificateDownloadUrl");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        if (certificate.getStoragePath() == null || certificate.getStoragePath().isEmpty()) {
            throw new IllegalArgumentException(
                    "Certificate does not have a storage path. PDF may not be generated yet."
            );
        }

        // Generate signed URL from MinIO/S3
        int expiration = expirationMinutes != null ? expirationMinutes : 60;
        return storageService.generateSignedUrl(
                storageService.getDefaultBucketName(),
                certificate.getStoragePath(),
                expiration
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String getQrCodeVerificationUrl(UUID certificateId) {
        tenantSchemaValidator.validateTenantSchema("getQrCodeVerificationUrl");

        certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        var certificateHash = certificateHashRepository.findByCertificateId(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate hash not found for certificate ID: " + certificateId
                ));

        // Build verification URL: /api/certificates/verify/{hash}
        String hash = certificateHash.getHashValue();
        String baseUrl = getBaseUrl();
        return baseUrl + "/api/certificates/verify/" + hash;
    }

    /**
     * Get base URL for generating verification URLs.
     * Uses configuration property or defaults to localhost:8080.
     */
    private String getBaseUrl() {
        // Try to get from environment or configuration
        // For prototype, default to localhost:8080
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = System.getProperty("app.base-url");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        // Remove trailing slash if present
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // Helper methods

    private TemplateVersion getTemplateVersion(UUID templateVersionId) {
        return templateService.findVersionById(templateVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template version not found with ID: " + templateVersionId
                ));
    }

    private Long getCustomerIdFromTenantContext() {
        var tenantSchema = TenantContext.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            throw new IllegalStateException("Tenant context is not set");
        }

        var customer = customerRepository.findByTenantSchema(tenantSchema)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found for tenant schema: " + tenantSchema
                ));

        return customer.getId();
    }

    private void validateCustomerLimits(Long customerId) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));

        // Check monthly certificate limit
        var currentMonthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        var currentMonthEnd = currentMonthStart.plusMonths(1).minusSeconds(1);

        // Get all certificates for customer issued this month
        var allCertificates = certificateRepository.findByCustomerId(customerId);
        var certificatesThisMonth = allCertificates.stream()
                .filter(cert -> cert.getIssuedAt() != null
                        && !cert.getIssuedAt().isBefore(currentMonthStart)
                        && !cert.getIssuedAt().isAfter(currentMonthEnd))
                .count();

        if (customer.getMaxCertificatesPerMonth() != null
                && certificatesThisMonth >= customer.getMaxCertificatesPerMonth()) {
            throw new IllegalArgumentException(
                    String.format("Customer %d has reached monthly certificate limit (%d)",
                            customerId, customer.getMaxCertificatesPerMonth())
            );
        }
    }

    /**
     * Process certificate generation (synchronous path).
     * Generates PDF, uploads to storage, and creates hash record.
     *
     * @param certificate The certificate to process
     * @param isPreview Whether this is a preview (PENDING status)
     */
    private void processCertificateGeneration(Certificate certificate, boolean isPreview) {
        log.info("Processing certificate generation for ID: {} (preview: {})", certificate.getId(), isPreview);

        try {
            // Optimized two-pass generation:
            // Pass 1: Render HTML once, generate PDF WITHOUT footer → calculate hash → save to DB
            // Pass 2: Append footer to cached HTML, generate final PDF → store this PDF
            // Optimization: Reuse HTML rendering to avoid expensive template processing twice

            // 1. Load template version
            var templateVersion = getTemplateVersion(certificate.getTemplateVersionId());

            // 2. Render HTML without footer (this is the expensive part - do it once)
            log.debug("Rendering HTML template for certificate: {}", certificate.getId());
            var htmlWithoutFooter = pdfGenerationService.renderHtml(templateVersion, certificate, false);

            // 3. Generate PDF from rendered HTML (for hash calculation)
            log.debug("Generating PDF without footer for hash calculation: {}", certificate.getId());
            var pdfWithoutFooter = pdfGenerationService.convertHtmlToPdf(htmlWithoutFooter, templateVersion);
            var hashValue = generateHashFromPdfContent(pdfWithoutFooter.toByteArray());

            // 4. Save hash to database
            certificate.setSignedHash(hashValue);
            var certificateHash = CertificateHash.builder()
                    .certificate(certificate)
                    .hashAlgorithm("SHA-256")
                    .hashValue(hashValue)
                    .build();
            certificateHashRepository.save(certificateHash);

            // 5. Append verification footer to cached HTML (fast string operation)
            log.debug("Appending verification footer to rendered HTML: {}", certificate.getId());
            var htmlWithFooter = pdfGenerationService.appendVerificationFooterToHtml(
                    htmlWithoutFooter, certificate);

            // 6. Generate final PDF from HTML with footer (reusing rendered content)
            log.debug("Generating final PDF with verification footer: {}", certificate.getId());
            var finalPdf = pdfGenerationService.convertHtmlToPdf(htmlWithFooter, templateVersion);

            // 7. Generate storage path
            var storagePath = generateStoragePath(certificate);
            certificate.setStoragePath(storagePath);

            // 6. Upload final PDF to storage
            log.debug("Uploading final PDF to storage: {}", storagePath);
            storageService.uploadFile(
                    storageService.getDefaultBucketName(),
                    storagePath,
                    finalPdf.toByteArray(),
                    PDF_CONTENT_TYPE
            );

            // 7. Mark as issued or pending (preview)
            if (isPreview) {
                certificate.setStatus(Certificate.CertificateStatus.PENDING);
                certificate.setPreviewGeneratedAt(LocalDateTime.now());
                log.info("Certificate preview generated for ID: {}, storage path: {}",
                        certificate.getId(), storagePath);
            } else {
                certificate.setStatus(Certificate.CertificateStatus.ISSUED);
                if (certificate.getIssuedBy() == null) {
                    // Set issuedBy from current authenticated user if not already set
                    UUID currentUserId = getCurrentUserId();
                    if (currentUserId != null) {
                        certificate.setIssuedBy(currentUserId);
                    }
                }
                log.info("Certificate generation completed for ID: {}, storage path: {}",
                        certificate.getId(), storagePath);
            }
            certificateRepository.save(certificate);
        } catch (Exception e) {
            log.error("Failed to process certificate generation for ID: {}", certificate.getId(), e);
            // Use internal helper to avoid @Transactional self-invocation warning
            markAsFailedInternal(certificate.getId(), e.getMessage());
            throw new RuntimeException("Certificate generation failed", e);
        }
    }

    /**
     * Generate storage path for certificate PDF.
     * Format: {tenant_schema}/certificates/{year}/{month}/{certificate_id}.pdf
     */
    private String generateStoragePath(Certificate certificate) {
        var tenantSchema = TenantContext.getTenantSchema();
        var now = LocalDateTime.now();
        return String.format("%s/certificates/%d/%02d/%s.pdf",
                tenantSchema != null ? tenantSchema : "default",
                now.getYear(),
                now.getMonthValue(),
                certificate.getId());
    }

    /**
     * Generate SHA-256 hash from actual PDF content.
     *
     * @param pdfContent The PDF file content as byte array
     * @return Base64-encoded SHA-256 hash
     */
    private String generateHashFromPdfContent(byte[] pdfContent) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashBytes = digest.digest(pdfContent);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     *
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Get the current authenticated user's ID from the security context.
     *
     * @return UUID of the current user, or null if not authenticated
     */
    private UUID getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("No authenticated user found in security context");
                return null;
            }

            // Extract user ID from JWT token
            Object principal = authentication.getPrincipal();
            log.debug("Principal type: {}", principal != null ? principal.getClass().getName() : "null");

            if (principal instanceof Jwt jwt) {
                // Log all claims for debugging
                log.debug("JWT claims: {}", jwt.getClaims());

                // Try to get user ID from different claim names
                String userIdStr = jwt.getClaimAsString("user_id");
                log.debug("Claim 'user_id': {}", userIdStr);

                if (userIdStr == null || userIdStr.isEmpty()) {
                    userIdStr = jwt.getClaimAsString("userId");
                    log.debug("Claim 'userId': {}", userIdStr);
                }
                if (userIdStr == null || userIdStr.isEmpty()) {
                    userIdStr = jwt.getClaimAsString("sub");
                    log.debug("Claim 'sub': {}", userIdStr);
                }

                if (userIdStr != null && !userIdStr.isEmpty()) {
                    log.debug("Successfully extracted user ID: {}", userIdStr);
                    return UUID.fromString(userIdStr);
                } else {
                    log.warn("User ID claim not found in JWT token. Available claims: {}", jwt.getClaims().keySet());
                    return null;
                }
            } else {
                log.warn("Principal is not a JWT token: {}", principal.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to extract user ID from security context", e);
            return null;
        }
    }

    @Override
    @Transactional
    public Certificate issueCertificate(UUID certificateId) {
        log.info("Issuing preview certificate with ID: {}", certificateId);

        tenantSchemaValidator.validateTenantSchema("issueCertificate");

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        // Validate certificate is in PENDING status
        if (certificate.getStatus() != Certificate.CertificateStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Certificate must be in PENDING status to be issued. Current status: " + certificate.getStatus()
            );
        }

        // Validate preview was generated
        if (certificate.getPreviewGeneratedAt() == null) {
            throw new IllegalArgumentException(
                    "Certificate does not have a preview. Please generate a preview first."
            );
        }

        // Validate preview PDF exists
        if (certificate.getStoragePath() == null || certificate.getStoragePath().isEmpty()) {
            throw new IllegalArgumentException(
                    "Certificate does not have a storage path. Preview PDF may not have been generated."
            );
        }

        var bucketName = storageService.getDefaultBucketName();
        if (!storageService.fileExists(bucketName, certificate.getStoragePath())) {
            throw new IllegalArgumentException(
                    "Preview PDF not found in storage. It may have been cleaned up."
            );
        }

        // Promote to ISSUED status and reuse existing PDF
        certificate.setStatus(Certificate.CertificateStatus.ISSUED);
        if (certificate.getIssuedBy() == null) {
            UUID currentUserId = getCurrentUserId();
            if (currentUserId != null) {
                certificate.setIssuedBy(currentUserId);
            }
        }
        if (certificate.getIssuedAt() == null) {
            certificate.setIssuedAt(LocalDateTime.now());
        }

        var issuedCertificate = certificateRepository.save(certificate);
        log.info("Certificate issued successfully with ID: {}", certificateId);
        return issuedCertificate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findPreviewCertificatesForCleanup(int minutesOld) {
        tenantSchemaValidator.validateTenantSchema("findPreviewCertificatesForCleanup");

        var cutoffTime = LocalDateTime.now().minusMinutes(minutesOld);
        log.debug("Finding preview certificates older than {} minutes (cutoff: {})", minutesOld, cutoffTime);

        return certificateRepository.findByStatusAndPreviewGeneratedAtBefore(
                Certificate.CertificateStatus.PENDING,
                cutoffTime
        );
    }

    @Override
    @Transactional
    public int cleanupOldPreviewPdfs(int minutesOld) {
        log.info("Starting cleanup of preview PDFs older than {} minutes", minutesOld);

        tenantSchemaValidator.validateTenantSchema("cleanupOldPreviewPdfs");

        var certificatesToCleanup = findPreviewCertificatesForCleanup(minutesOld);
        var bucketName = storageService.getDefaultBucketName();
        int cleanedCount = 0;

        for (var certificate : certificatesToCleanup) {
            try {
                if (certificate.getStoragePath() != null && !certificate.getStoragePath().isEmpty()) {
                    // Delete PDF from storage
                    storageService.deleteFile(bucketName, certificate.getStoragePath());

                    // Clear storage path and preview timestamp, mark as revoked
                    certificate.setStoragePath(null);
                    certificate.setPreviewGeneratedAt(null);
                    certificate.setStatus(Certificate.CertificateStatus.REVOKED);
                    certificateRepository.save(certificate);

                    cleanedCount++;
                    log.info("Cleaned up preview PDF and marked as REVOKED for certificate ID: {}", certificate.getId());
                }
            } catch (Exception e) {
                log.error("Failed to cleanup preview PDF for certificate ID: {}", certificate.getId(), e);
                // Continue with next certificate
            }
        }

        log.info("Cleaned up {} preview PDFs", cleanedCount);
        return cleanedCount;
    }
}
