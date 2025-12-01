package tech.seccertificate.certmgmt.dto.certificate;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for generating a new certificate.
 * Used for certificate generation API endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerateCertificateRequest {

    /**
     * Template version ID to use for generation (required).
     */
    @NotNull(message = "Template version ID is required")
    private UUID templateVersionId;

    /**
     * Certificate number (optional, will be auto-generated if not provided).
     * Must be unique and between 1 and 100 characters.
     */
    @Size(min = 1, max = 100, message = "Certificate number must be between 1 and 100 characters")
    private String certificateNumber;

    /**
     * Recipient data as key-value pairs (required).
     * Contains dynamic fields based on template field schema.
     * Example: {"name": "John Doe", "email": "john@example.com", "course": "Java Fundamentals"}
     */
    @NotNull(message = "Recipient data is required")
    @NotEmpty(message = "Recipient data cannot be empty")
    private Map<String, Object> recipientData;

    /**
     * Certificate metadata as key-value pairs (optional).
     */
    private Map<String, Object> metadata;

    /**
     * Issue date (optional, defaults to current time).
     */
    private LocalDateTime issuedAt;

    /**
     * Expiration date (optional, no expiration if not provided).
     */
    private LocalDateTime expiresAt;

    /**
     * User ID who is issuing this certificate (optional, defaults to authenticated user).
     */
    private UUID issuedBy;

    /**
     * Whether to generate PDF synchronously (default: false, async via RabbitMQ).
     */
    @Builder.Default
    private Boolean synchronous = false;
}
