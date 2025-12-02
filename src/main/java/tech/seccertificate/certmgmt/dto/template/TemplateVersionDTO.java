package tech.seccertificate.certmgmt.dto.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for TemplateVersion entity.
 * Used for API responses to expose template version information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateVersionDTO {

    /**
     * Template version unique identifier.
     */
    private UUID id;

    /**
     * Template ID this version belongs to.
     */
    private Long templateId;

    /**
     * Version number.
     */
    private Integer version;

    /**
     * HTML content of the template.
     */
    private String htmlContent;

    /**
     * Field schema as JSON (defines dynamic fields).
     */
    private Map<String, Object> fieldSchema;

    /**
     * CSS styles for the template.
     */
    private String cssStyles;

    /**
     * Template version settings as key-value pairs.
     */
    private Map<String, Object> settings;

    /**
     * Template version status.
     */
    private TemplateVersion.TemplateVersionStatus status;

    /**
     * User ID who created this version.
     */
    private UUID createdBy;

    /**
     * Date when version was created.
     */
    private LocalDateTime createdAt;
}
