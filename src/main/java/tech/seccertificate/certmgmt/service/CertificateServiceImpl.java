package tech.seccertificate.certmgmt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of CertificateService.
 * Handles certificate generation (sync + async), CRUD operations, status management,
 * and certificate verification.
 * 
 * <p>All operations require tenant context to be set (via TenantRequestInterceptor
 * or programmatically using TenantService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private final CertificateRepository certificateRepository;
    private final CertificateHashRepository certificateHashRepository;
    private final CustomerRepository customerRepository;
    private final TemplateService templateService;
    private final TenantSchemaValidator tenantSchemaValidator;

    // TODO: Inject PDF generation service when implemented
    // private final PdfGenerationService pdfGenerationService;
    
    // TODO: Inject storage service (S3/MinIO) when implemented
    // private final StorageService storageService;
    
    // TODO: Inject message queue service (RabbitMQ) when implemented
    // private final MessageQueueService messageQueueService;
    
    // TODO: Inject crypto service for hash signing when implemented
    // private final CryptoService cryptoService;

    @Override
    @Transactional
    public Certificate generateCertificate(Certificate certificate) {
        log.info("Generating certificate synchronously for template version: {}", 
                certificate.getTemplateVersionId());

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

            // Simulate PDF generation (remove when actual implementation is added)
            processCertificateGeneration(savedCertificate);

            return savedCertificate;
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to create certificate due to data integrity violation", e);
            throw new IllegalArgumentException("Certificate data violates constraints: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Certificate generateCertificateAsync(Certificate certificate) {
        log.info("Generating certificate asynchronously for template version: {}", 
                certificate.getTemplateVersionId());

        tenantSchemaValidator.validateTenantSchema("generateCertificateAsync");
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
        certificate.setStatus(Certificate.CertificateStatus.PENDING);
        if (certificate.getIssuedAt() == null) {
            certificate.setIssuedAt(LocalDateTime.now());
        }
        if (certificate.getRecipientData() == null || certificate.getRecipientData().isEmpty()) {
            throw new IllegalArgumentException("Recipient data is required");
        }

        // Validate customer limits
        validateCustomerLimits(certificate.getCustomerId());

        try {
            var savedCertificate = certificateRepository.save(certificate);
            log.info("Certificate queued for async processing with ID: {}", savedCertificate.getId());

            // TODO: Send message to RabbitMQ queue for async processing
            // messageQueueService.sendCertificateGenerationMessage(savedCertificate.getId());

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
                .map(this::generateCertificate)
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
                .map(this::generateCertificateAsync)
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
        certificate.setIssuedBy(issuedBy);
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

        var certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Certificate not found with ID: " + certificateId
                ));

        certificate.setStatus(Certificate.CertificateStatus.FAILED);
        
        // Store error message in metadata
        // TODO: Parse existing metadata JSON and add error message
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // For now, append to metadata (simplified - should parse JSON properly)
            var currentMetadata = certificate.getMetadata();
            if (currentMetadata == null || currentMetadata.isEmpty()) {
                certificate.setMetadata("{\"error\":\"" + errorMessage + "\"}");
            } else {
                // Simple append (should use proper JSON parsing)
                certificate.setMetadata(currentMetadata.replace("}", ",\"error\":\"" + errorMessage + "\"}"));
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

        // TODO: Implement hash verification
        // 1. Retrieve PDF from storage
        // 2. Compute hash of PDF content
        // 3. Compare with stored hash
        // 4. Verify signature if signed hash exists

        // For now, return true if hash exists
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Certificate> verifyCertificateByHash(String hash) {
        // This is a public endpoint, so we don't validate tenant schema
        // However, we still need to search across all tenants
        
        // TODO: Implement hash lookup across all tenant schemas
        // For now, we need to search by hash value
        // Since CertificateHash doesn't have a direct findByHashValue, we'll need to:
        // 1. Search across all tenant schemas (requires custom implementation)
        // 2. Or use a different approach (e.g., store hash in certificate entity)
        
        // For now, return empty - this needs proper implementation with cross-tenant search
        log.warn("Hash verification not fully implemented - requires cross-tenant search");
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

        // TODO: Generate signed URL from S3/MinIO
        // return storageService.generateSignedUrl(certificate.getStoragePath(), expirationMinutes);
        
        // For now, return placeholder
        throw new UnsupportedOperationException(
                "Signed URL generation not yet implemented. Storage service required."
        );
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
     * TODO: Replace with actual PDF generation implementation.
     */
    private void processCertificateGeneration(Certificate certificate) {
        log.info("Processing certificate generation for ID: {}", certificate.getId());
        
        try {
            // TODO: Implement actual PDF generation
            // 1. Load template version
            // 2. Render HTML with recipient data
            // 3. Convert HTML to PDF
            // 4. Upload PDF to S3/MinIO
            // 5. Generate hash and sign
            // 6. Update certificate status to ISSUED
            
            // Simulate processing
            var storagePath = String.format("%s/certificates/%d/%02d/%s.pdf",
                    TenantContext.getTenantSchema(),
                    LocalDateTime.now().getYear(),
                    LocalDateTime.now().getMonthValue(),
                    certificate.getId());
            
            certificate.setStoragePath(storagePath);
            
            // Generate hash (simplified - should use actual PDF content)
            var hashValue = generateHashForCertificate(certificate);
            certificate.setSignedHash(hashValue);
            
            // Create certificate hash record
            var certificateHash = CertificateHash.builder()
                    .certificate(certificate)
                    .hashAlgorithm("SHA-256")
                    .hashValue(hashValue)
                    .build();
            
            certificateHashRepository.save(certificateHash);
            
            // Mark as issued
            certificate.setStatus(Certificate.CertificateStatus.ISSUED);
            certificateRepository.save(certificate);
            
            log.info("Certificate generation completed for ID: {}", certificate.getId());
        } catch (Exception e) {
            log.error("Failed to process certificate generation for ID: {}", certificate.getId(), e);
            markAsFailed(certificate.getId(), e.getMessage());
            throw new RuntimeException("Certificate generation failed", e);
        }
    }

    /**
     * Generate hash for certificate (simplified implementation).
     * TODO: Replace with actual hash generation from PDF content.
     */
    private String generateHashForCertificate(Certificate certificate) {
        // Simplified hash generation - should hash actual PDF content
        var content = String.format("%s-%s-%s-%s",
                certificate.getId(),
                certificate.getCertificateNumber(),
                certificate.getTemplateVersionId(),
                certificate.getRecipientData());
        
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashBytes = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
