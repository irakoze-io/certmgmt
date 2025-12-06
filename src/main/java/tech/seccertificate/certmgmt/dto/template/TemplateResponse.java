package tech.seccertificate.certmgmt.dto.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for Template entity.
 * Used for API responses to expose template information.
 */
@Schema(description = "Template response containing template information")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateResponse {

    @Schema(description = "Template unique identifier", example = "1")
    private Long id;

    @Schema(description = "Customer ID that owns this template", example = "1")
    private Long customerId;

    @Schema(description = "Template name", example = "Java Certification Template")
    private String name;

    @Schema(description = "Template code (unique identifier within customer)", example = "JAVA_CERT_001")
    private String code;

    @Schema(description = "Template description", example = "Template for Java programming certifications")
    private String description;

    @Schema(description = "Current version number", example = "2")
    private Integer currentVersion;

    @Schema(description = "Template metadata as key-value pairs", 
            example = "{\"category\": \"certification\", \"language\": \"en\"}")
    private Map<String, Object> metadata;

    @Schema(description = "Date when template was created", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Date when template was last updated", example = "2024-01-20T14:45:00")
    private LocalDateTime updatedAt;

    @Schema(description = "List of template versions (optional, populated when requested)")
    private List<TemplateVersionResponse> versions;
}
