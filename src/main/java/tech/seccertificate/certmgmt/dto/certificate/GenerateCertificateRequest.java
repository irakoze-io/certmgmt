package tech.seccertificate.certmgmt.dto.certificate;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for generating a new certificate.
 * Used for certificate generation API endpoint.
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to generate a new certificate")
public class GenerateCertificateRequest {

    @NotNull(message = "Template version ID is required")
    @Schema(description = "Template version ID to use for generation", 
            example = "660e8400-e29b-41d4-a716-446655440000", required = true)
    private UUID templateVersionId;

    @Size(min = 1, max = 100, message = "Certificate number must be between 1 and 100 characters")
    @Schema(description = "Certificate number (optional, will be auto-generated if not provided). Must be unique", 
            example = "CERT-2024-001", minLength = 1, maxLength = 100)
    private String certificateNumber;

    @NotNull(message = "Recipient data is required")
    @NotEmpty(message = "Recipient data cannot be empty")
    @Schema(description = "Recipient data as key-value pairs. Contains dynamic fields based on template field schema", 
            example = "{\"name\": \"John Doe\", \"email\": \"john@example.com\", \"course\": \"Java Fundamentals\"}", 
            required = true)
    private Map<String, Object> recipientData;

    @Schema(description = "Certificate metadata as key-value pairs", 
            example = "{\"issuer\": \"Acme Corp\", \"category\": \"Professional Development\"}")
    private Map<String, Object> metadata;

    @Schema(description = "Issue date (optional, defaults to current time)", example = "2024-01-15T10:30:00")
    private LocalDateTime issuedAt;

    @Schema(description = "Expiration date (optional, no expiration if not provided)", example = "2025-01-15T10:30:00")
    private LocalDateTime expiresAt;

    @Schema(description = "User ID who is issuing this certificate (optional, defaults to authenticated user)", 
            example = "770e8400-e29b-41d4-a716-446655440000")
    private UUID issuedBy;

    @Builder.Default
    @Schema(description = "Whether to generate PDF synchronously (default: false, async via RabbitMQ)", 
            example = "false", defaultValue = "false")
    private Boolean synchronous = false;
}
