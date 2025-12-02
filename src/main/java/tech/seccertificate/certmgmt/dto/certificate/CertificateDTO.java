package tech.seccertificate.certmgmt.dto.certificate;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.entity.Certificate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for Certificate entity.
 * Used for API responses to expose certificate information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateDTO {

    /**
     * Certificate unique identifier.
     */
    private UUID id;

    /**
     * Customer ID that owns this certificate.
     */
    private Long customerId;

    /**
     * Template version ID used to generate this certificate.
     */
    private UUID templateVersionId;

    /**
     * Certificate number (unique identifier).
     */
    private String certificateNumber;

    /**
     * Recipient data as key-value pairs (name, email, etc.).
     */
    private Map<String, Object> recipientData;

    /**
     * Certificate metadata as key-value pairs.
     */
    private Map<String, Object> metadata;

    /**
     * Storage path where PDF is stored (S3/MinIO).
     */
    private String storagePath;

    /**
     * Signed hash for certificate verification.
     */
    private String signedHash;

    /**
     * Certificate status.
     */
    private Certificate.CertificateStatus status;

    /**
     * Date when certificate was issued.
     */
    private LocalDateTime issuedAt;

    /**
     * Date when certificate expires.
     */
    private LocalDateTime expiresAt;

    /**
     * User ID who issued this certificate.
     */
    private UUID issuedBy;

    /**
     * Date when certificate was created.
     */
    private LocalDateTime createdAt;

    /**
     * Date when certificate was last updated.
     */
    private LocalDateTime updatedAt;

    /**
     * Download URL (signed URL for secure access).
     * Only populated when requested.
     */
    private String downloadUrl;

    /**
     * QR code URL for verification.
     * Only populated when requested.
     */
    private String qrCodeUrl;
}
