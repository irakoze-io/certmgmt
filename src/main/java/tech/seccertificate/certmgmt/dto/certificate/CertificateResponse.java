package tech.seccertificate.certmgmt.dto.certificate;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.entity.Certificate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for Certificate entity.
 * Used for API responses to expose certificate information.
 */
@Schema(description = "Certificate response containing certificate information")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateResponse {

    @Schema(description = "Certificate unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Customer ID that owns this certificate", example = "1")
    private Long customerId;

    @Schema(description = "Template version ID used to generate this certificate", example = "660e8400-e29b-41d4-a716-446655440000")
    private UUID templateVersionId;

    @Schema(description = "Certificate number (unique identifier)", example = "CERT-2024-001")
    private String certificateNumber;

    @Schema(description = "Recipient data as key-value pairs (name, email, etc.)", 
            example = "{\"name\": \"John Doe\", \"email\": \"john@example.com\", \"course\": \"Java Fundamentals\"}")
    private Map<String, Object> recipientData;

    @Schema(description = "Certificate metadata as key-value pairs", 
            example = "{\"issuer\": \"Acme Corp\", \"category\": \"Professional Development\"}")
    private Map<String, Object> metadata;

    @Schema(description = "Storage path where PDF is stored (S3/MinIO)", example = "certificates/2024/01/cert-001.pdf")
    private String storagePath;

    @Schema(description = "Signed hash for certificate verification", example = "a1b2c3d4e5f6...")
    private String signedHash;

    @Schema(description = "Certificate status", example = "ISSUED", 
            allowableValues = {"PENDING", "PROCESSING", "ISSUED", "REVOKED", "FAILED"})
    private Certificate.CertificateStatus status;

    @Schema(description = "Date when certificate was issued", example = "2024-01-15T10:30:00")
    private LocalDateTime issuedAt;

    @Schema(description = "Date when certificate expires", example = "2025-01-15T10:30:00")
    private LocalDateTime expiresAt;

    @Schema(description = "User ID who issued this certificate", example = "770e8400-e29b-41d4-a716-446655440000")
    private UUID issuedBy;

    @Schema(description = "Full name of user who issued this certificate", example = "John Doe")
    private String issuedByName;

    @Schema(description = "Date when certificate was created", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Date when certificate was last updated", example = "2024-01-15T10:30:00")
    private LocalDateTime updatedAt;

    @Schema(description = "Download URL (signed URL for secure access). Only populated when requested", 
            example = "https://storage.example.com/certificates/cert-001.pdf?signature=...")
    private String downloadUrl;

    @Schema(description = "QR code URL for verification. Only populated when requested", 
            example = "https://api.example.com/verify/a1b2c3d4e5f6...")
    private String qrCodeUrl;
}
