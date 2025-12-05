package tech.seccertificate.certmgmt.dto.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Message DTO for RabbitMQ certificate generation queue.
 * Contains all information needed for async PDF generation.
 */
@Value
@Builder
public class CertificateGenerationMessage {

    @NotNull
    @JsonProperty("certificateId")
    UUID certificateId;

    @NotBlank
    @JsonProperty("tenantSchema")
    String tenantSchema;

    @JsonCreator
    public CertificateGenerationMessage(
            @JsonProperty("certificateId") UUID certificateId,
            @JsonProperty("tenantSchema") String tenantSchema) {
        this.certificateId = certificateId;
        this.tenantSchema = tenantSchema;
    }
}
