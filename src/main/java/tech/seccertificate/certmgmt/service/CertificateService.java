package tech.seccertificate.certmgmt.service;

import tech.seccertificate.certmgmt.entity.Certificate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for certificate management operations.
 * Handles certificate generation (sync + async), CRUD operations, status management,
 * and certificate verification.
 */
public interface CertificateService {

    /**
     * Generate a certificate synchronously.
     * This method generates the certificate immediately and returns it.
     * Use for small batches or when immediate response is required.
     *
     * @param certificate The certificate data to generate
     * @param isPreview Whether to generate as preview (PENDING status)
     * @return The generated certificate with status ISSUED (or PENDING if preview)
     * @throws IllegalArgumentException if certificate data is invalid
     */
    Certificate generateCertificate(Certificate certificate, boolean isPreview);

    /**
     * Generate a certificate asynchronously.
     * This method queues the certificate for async processing and returns immediately.
     * Use for large batches or when async processing is preferred.
     *
     * @param certificate The certificate data to generate
     * @param isPreview Whether to generate as preview (PENDING status)
     * @return The certificate with status PENDING or PROCESSING
     * @throws IllegalArgumentException if certificate data is invalid
     */
    Certificate generateCertificateAsync(Certificate certificate, boolean isPreview);

    /**
     * Generate multiple certificates synchronously.
     *
     * @param certificates List of certificate data to generate
     * @return List of generated certificates
     * @throws IllegalArgumentException if any certificate data is invalid
     */
    List<Certificate> generateCertificatesBatch(List<Certificate> certificates);

    /**
     * Generate multiple certificates asynchronously.
     *
     * @param certificates List of certificate data to generate
     * @return List of certificates queued for processing
     * @throws IllegalArgumentException if any certificate data is invalid
     */
    List<Certificate> generateCertificatesBatchAsync(List<Certificate> certificates);

    /**
     * Find a certificate by ID.
     *
     * @param certificateId The certificate ID
     * @return Optional containing the certificate if found
     */
    Optional<Certificate> findById(UUID certificateId);

    /**
     * Find a certificate by certificate number.
     *
     * @param certificateNumber The certificate number
     * @return Optional containing the certificate if found
     */
    Optional<Certificate> findByCertificateNumber(String certificateNumber);

    /**
     * Find all certificates for a customer.
     *
     * @param customerId The customer ID
     * @return List of certificates for the customer
     */
    List<Certificate> findByCustomerId(Long customerId);

    /**
     * Find certificates by customer and status.
     *
     * @param customerId The customer ID
     * @param status The certificate status
     * @return List of certificates matching the criteria
     */
    List<Certificate> findByCustomerIdAndStatus(Long customerId, Certificate.CertificateStatus status);

    /**
     * Find certificates by template version.
     *
     * @param templateVersionId The template version ID
     * @return List of certificates generated from this template version
     */
    List<Certificate> findByTemplateVersionId(UUID templateVersionId);

    /**
     * Find certificates by status.
     *
     * @param status The certificate status
     * @return List of certificates with the specified status
     */
    List<Certificate> findByStatus(Certificate.CertificateStatus status);

    /**
     * Find certificates issued within a date range.
     *
     * @param start The start date (inclusive)
     * @param end The end date (inclusive)
     * @return List of certificates issued in the date range
     */
    List<Certificate> findIssuedBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find certificates expiring before a specific date.
     *
     * @param date The expiration date
     * @return List of certificates expiring before the date
     */
    List<Certificate> findExpiringBefore(LocalDateTime date);

    /**
     * Find certificates expiring after a specific date.
     *
     * @param date The expiration date
     * @return List of certificates expiring after the date
     */
    List<Certificate> findExpiringAfter(LocalDateTime date);

    /**
     * Update certificate information.
     *
     * @param certificate The certificate with updated information
     * @return The updated certificate
     * @throws IllegalArgumentException if certificate is null or ID is missing
     */
    Certificate updateCertificate(Certificate certificate);

    /**
     * Update certificate status.
     *
     * @param certificateId The certificate ID
     * @param status The new status
     * @return The updated certificate
     * @throws IllegalArgumentException if certificate not found
     */
    Certificate updateStatus(UUID certificateId, Certificate.CertificateStatus status);

    /**
     * Mark certificate as issued.
     * Sets status to ISSUED and records issued timestamp.
     *
     * @param certificateId The certificate ID
     * @param issuedBy The user ID who issued the certificate
     * @return The updated certificate
     * @throws IllegalArgumentException if certificate not found
     */
    Certificate markAsIssued(UUID certificateId, UUID issuedBy);

    /**
     * Mark certificate as processing.
     * Sets status to PROCESSING.
     *
     * @param certificateId The certificate ID
     * @return The updated certificate
     * @throws IllegalArgumentException if certificate not found
     */
    Certificate markAsProcessing(UUID certificateId);

    /**
     * Revoke a certificate.
     * Sets status to REVOKED.
     *
     * @param certificateId The certificate ID
     * @return The revoked certificate
     * @throws IllegalArgumentException if certificate not found
     */
    Certificate revokeCertificate(UUID certificateId);

    /**
     * Mark certificate generation as failed.
     * Sets status to FAILED.
     *
     * @param certificateId The certificate ID
     * @param errorMessage Optional error message to store in metadata
     * @return The failed certificate
     * @throws IllegalArgumentException if certificate not found
     */
    Certificate markAsFailed(UUID certificateId, String errorMessage);

    /**
     * Delete a certificate by ID.
     * This will also delete associated certificate hash if exists.
     *
     * @param certificateId The certificate ID
     * @throws IllegalArgumentException if certificate not found
     */
    void deleteCertificate(UUID certificateId);

    /**
     * Check if a certificate number is already in use.
     *
     * @param certificateNumber The certificate number to check
     * @return true if certificate number is already in use, false otherwise
     */
    boolean isCertificateNumberTaken(String certificateNumber);

    /**
     * Generate a unique certificate number.
     *
     * @param templateCode Optional template code to include in the number
     * @return A unique certificate number
     */
    String generateCertificateNumber(String templateCode);

    /**
     * Validate certificate data before creation or update.
     *
     * @param certificate The certificate to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateCertificate(Certificate certificate);

    /**
     * Verify a certificate by its hash.
     * Validates the certificate's integrity using the stored hash.
     *
     * @param certificateId The certificate ID
     * @return true if certificate is valid, false otherwise
     * @throws IllegalArgumentException if certificate not found
     */
    boolean verifyCertificate(UUID certificateId);

    /**
     * Verify a certificate by its hash value.
     * Public verification endpoint that doesn't require authentication.
     *
     * @param hash The certificate hash to verify
     * @return Optional containing the certificate if hash is valid
     */
    Optional<Certificate> verifyCertificateByHash(String hash);

    /**
     * Get certificate count for a customer.
     *
     * @param customerId The customer ID
     * @return The number of certificates for the customer
     */
    long countByCustomerId(Long customerId);

    /**
     * Get certificate count by status.
     *
     * @param status The certificate status
     * @return The number of certificates with the specified status
     */
    long countByStatus(Certificate.CertificateStatus status);

    /**
     * Get certificate download URL (signed URL).
     * Returns a time-limited signed URL for downloading the certificate PDF.
     *
     * @param certificateId The certificate ID
     * @param expirationMinutes Optional expiration time in minutes (default: 60)
     * @return The signed download URL
     * @throws IllegalArgumentException if certificate not found or storage path is missing
     */
    String getCertificateDownloadUrl(UUID certificateId, Integer expirationMinutes);

    /**
     * Get QR code verification URL for a certificate.
     * Returns a URL that can be encoded in a QR code for certificate verification.
     *
     * @param certificateId The certificate ID
     * @return The verification URL for QR code
     * @throws IllegalArgumentException if certificate not found or hash is missing
     */
    String getQrCodeVerificationUrl(UUID certificateId);

    /**
     * Issue a preview certificate.
     * Promotes a PENDING certificate to ISSUED status and reuses the existing PDF.
     *
     * @param certificateId The certificate ID
     * @return The issued certificate
     * @throws IllegalArgumentException if certificate not found, not in PENDING status, or has no preview PDF
     */
    Certificate issueCertificate(UUID certificateId);

    /**
     * Find preview certificates that need cleanup.
     * Returns certificates with PENDING status and preview PDF older than specified minutes.
     *
     * @param minutesOld Number of minutes since preview generation
     * @return List of certificates eligible for cleanup
     */
    List<Certificate> findPreviewCertificatesForCleanup(int minutesOld);

    /**
     * Cleanup old preview PDFs.
     * Deletes preview PDFs from storage for certificates that have been in PENDING status
     * for longer than the specified time.
     *
     * @param minutesOld Number of minutes since preview generation
     * @return Number of PDFs cleaned up
     */
    int cleanupOldPreviewPdfs(int minutesOld);
}
