package tech.seccertificate.certmgmt.dto.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for TemplateVersion entity.
 * Used for API responses to expose template version information.
 */
@Schema(description = "Template version response containing template version information")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateVersionResponse {

    @Schema(description = "Template version unique identifier", example = "660e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Template ID this version belongs to", example = "1")
    private Long templateId;

    @Schema(description = "Version number", example = "1")
    private Integer version;

    @Schema(description = "HTML content of the template", 
            example = "<html><body><h1>Certificate</h1><p>This certifies that {{name}}...</p></body></html>")
    private String htmlContent;

    @Schema(description = "Field schema as JSON (defines dynamic fields)", 
            example = "{\"name\": {\"type\": \"string\", \"required\": true}, \"email\": {\"type\": \"string\", \"required\": true}}")
    private Map<String, Object> fieldSchema;

    @Schema(description = "CSS styles for the template", 
            example = "body { font-family: Arial, sans-serif; } h1 { color: #333; }")
    private String cssStyles;

    @Schema(description = "Template version settings as key-value pairs", 
            example = "{\"pageSize\": \"A4\", \"orientation\": \"portrait\"}")
    private Map<String, Object> settings;

    @Schema(description = "Template version status", example = "PUBLISHED", 
            allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"})
    private TemplateVersion.TemplateVersionStatus status;

    @Schema(description = "User ID who created this version", example = "770e8400-e29b-41d4-a716-446655440000")
    private UUID createdBy;

    @Schema(description = "Full name of user who created this version", example = "John Doe")
    private String createdByName;

    @Schema(description = "Date when version was created", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;
}
