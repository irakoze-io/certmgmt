package tech.seccertificate.certmgmt.dto.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for Template entity.
 * Used for API responses to expose template information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateDTO {

    /**
     * Template unique identifier.
     */
    private Long id;

    /**
     * Customer ID that owns this template.
     */
    private Long customerId;

    /**
     * Template name.
     */
    private String name;

    /**
     * Template code (unique identifier within customer).
     */
    private String code;

    /**
     * Template description.
     */
    private String description;

    /**
     * Current version number.
     */
    private Integer currentVersion;

    /**
     * Template metadata as key-value pairs.
     */
    private Map<String, Object> metadata;

    /**
     * Date when template was created.
     */
    private LocalDateTime createdAt;

    /**
     * Date when template was last updated.
     */
    private LocalDateTime updatedAt;

    /**
     * List of template versions (optional, populated when requested).
     */
    private List<TemplateVersionDTO> versions;
}
