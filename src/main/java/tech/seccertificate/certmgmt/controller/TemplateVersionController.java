package tech.seccertificate.certmgmt.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.seccertificate.certmgmt.dto.template.TemplateVersionResponse;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.service.TemplateService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for template version management operations.
 * Handles template version CRUD and lifecycle operations within the tenant context.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/templates/{templateId}/versions - Create a new template version</li>
 *   <li>GET /api/templates/{templateId}/versions - Get all versions for a template</li>
 *   <li>GET /api/templates/{templateId}/versions/{versionId} - Get version by ID</li>
 *   <li>GET /api/templates/{templateId}/versions/current - Get current version</li>
 *   <li>GET /api/templates/{templateId}/versions/latest-published - Get latest published version</li>
 *   <li>PUT /api/templates/{templateId}/versions/{versionId} - Update template version</li>
 *   <li>POST /api/templates/{templateId}/versions/{versionId}/publish - Publish a version</li>
 *   <li>POST /api/templates/{templateId}/versions/{versionId}/archive - Archive a version</li>
 *   <li>POST /api/templates/{templateId}/versions/{versionId}/draft - Set version as draft</li>
 * </ul>
 * 
 * <p>Note: All operations require tenant context to be set (via X-Tenant-Id header).
 */
@Slf4j
@RestController
@RequestMapping("/api/templates/{templateId}/versions")
@RequiredArgsConstructor
public class TemplateVersionController {

    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    /**
     * Create a new template version.
     * 
     * @param templateId The template ID
     * @param versionResponse The template version data
     * @return Created template version response with 201 status
     */
    @PostMapping
    public ResponseEntity<TemplateVersionResponse> createTemplateVersion(
            @PathVariable @NotNull Long templateId,
            @Valid @RequestBody TemplateVersionResponse versionResponse) {
        log.info("Creating template version for template ID: {}", templateId);
        
        var templateVersion = mapToEntity(versionResponse);
        var createdVersion = templateService.createTemplateVersion(templateId, templateVersion);
        var response = mapToDTO(createdVersion);
        
        var location = URI.create("/api/templates/" + templateId + "/versions/" + createdVersion.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    /**
     * Get all versions for a template.
     * 
     * @param templateId The template ID
     * @return List of template version responses with 200 status
     */
    @GetMapping
    public ResponseEntity<List<TemplateVersionResponse>> getTemplateVersions(
            @PathVariable @NotNull Long templateId) {
        log.debug("Getting all versions for template ID: {}", templateId);
        
        var versions = templateService.findVersionsByTemplateId(templateId);
        var versionDTOs = versions.stream()
                .map(this::mapToDTO)
                .toList();
        
        return ResponseEntity.ok(versionDTOs);
    }

    /**
     * Get template version by ID.
     * 
     * @param templateId The template ID
     * @param versionId The version ID
     * @return Template version response with 200 status, or 404 if not found
     */
    @GetMapping("/{versionId}")
    public ResponseEntity<TemplateVersionResponse> getTemplateVersion(
            @PathVariable @NotNull Long templateId,
            @PathVariable @NotNull UUID versionId) {
        log.debug("Getting template version with ID: {} for template: {}", versionId, templateId);
        
        return templateService.findVersionById(versionId)
                .filter(version -> version.getTemplate().getId().equals(templateId))
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get current version of a template.
     * 
     * @param templateId The template ID
     * @return Template version response with 200 status, or 404 if not found
     */
    @GetMapping("/current")
    public ResponseEntity<TemplateVersionResponse> getCurrentVersion(
            @PathVariable @NotNull Long templateId) {
        log.debug("Getting current version for template ID: {}", templateId);
        
        return templateService.findCurrentVersion(templateId)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get latest published version of a template.
     * 
     * @param templateId The template ID
     * @return Template version response with 200 status, or 404 if not found
     */
    @GetMapping("/latest-published")
    public ResponseEntity<TemplateVersionResponse> getLatestPublishedVersion(
            @PathVariable @NotNull Long templateId) {
        log.debug("Getting latest published version for template ID: {}", templateId);
        
        return templateService.findLatestPublishedVersion(templateId)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update template version.
     * 
     * @param templateId The template ID
     * @param versionId The version ID
     * @param versionResponse The updated template version data
     * @return Updated template version response with 200 status, or 404 if not found
     */
    @PutMapping("/{versionId}")
    public ResponseEntity<TemplateVersionResponse> updateTemplateVersion(
            @PathVariable @NotNull Long templateId,
            @PathVariable @NotNull UUID versionId,
            @Valid @RequestBody TemplateVersionResponse versionResponse) {
        log.info("Updating template version with ID: {} for template: {}", versionId, templateId);

        versionResponse.setId(versionId);
        
        var templateVersion = mapToEntity(versionResponse);
        var updatedVersion = templateService.updateTemplateVersion(templateVersion);
        var response = mapToDTO(updatedVersion);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Publish a template version.
     * Sets the version status to PUBLISHED and updates the template's current version.
     * 
     * @param templateId The template ID
     * @param versionId The version ID
     * @return Published template version response with 200 status, or 404 if not found
     */
    @PostMapping("/{versionId}/publish")
    public ResponseEntity<TemplateVersionResponse> publishVersion(
            @PathVariable @NotNull Long templateId,
            @PathVariable @NotNull UUID versionId) {
        log.info("Publishing template version with ID: {} for template: {}", versionId, templateId);
        
        var publishedVersion = templateService.publishVersion(versionId);

        if (!publishedVersion.getTemplate().getId().equals(templateId)) {
            return ResponseEntity.notFound().build();
        }
        
        var response = mapToDTO(publishedVersion);
        return ResponseEntity.ok(response);
    }

    /**
     * Archive a template version.
     * Sets the version status to ARCHIVED.
     * 
     * @param templateId The template ID
     * @param versionId The version ID
     * @return Archived template version response with 200 status, or 404 if not found
     */
    @PostMapping("/{versionId}/archive")
    public ResponseEntity<TemplateVersionResponse> archiveVersion(
            @PathVariable @NotNull Long templateId,
            @PathVariable @NotNull UUID versionId) {
        log.info("Archiving template version with ID: {} for template: {}", versionId, templateId);
        
        var archivedVersion = templateService.archiveVersion(versionId);
        
        // Verify it belongs to the correct template
        if (!archivedVersion.getTemplate().getId().equals(templateId)) {
            return ResponseEntity.notFound().build();
        }
        
        var response = mapToDTO(archivedVersion);
        return ResponseEntity.ok(response);
    }

    /**
     * Set a template version as draft.
     * Sets the version status to DRAFT.
     * 
     * @param templateId The template ID
     * @param versionId The version ID
     * @return Draft template version response with 200 status, or 404 if not found
     */
    @PostMapping("/{versionId}/draft")
    public ResponseEntity<TemplateVersionResponse> setVersionAsDraft(
            @PathVariable @NotNull Long templateId,
            @PathVariable @NotNull UUID versionId) {
        log.info("Setting template version as draft with ID: {} for template: {}", versionId, templateId);
        
        var draftVersion = templateService.setVersionAsDraft(versionId);
        
        // Verify it belongs to the correct template
        if (!draftVersion.getTemplate().getId().equals(templateId)) {
            return ResponseEntity.notFound().build();
        }
        
        var response = mapToDTO(draftVersion);
        return ResponseEntity.ok(response);
    }

    /**
     * Map TemplateVersionResponse to TemplateVersion entity.
     */
    private TemplateVersion mapToEntity(TemplateVersionResponse dto) {
        String fieldSchemaJson = null;
        if (dto.getFieldSchema() != null) {
            try {
                fieldSchemaJson = objectMapper.writeValueAsString(dto.getFieldSchema());
            } catch (Exception e) {
                log.error("Failed to serialize fieldSchema to JSON", e);
                throw new IllegalArgumentException("Invalid fieldSchema format", e);
            }
        }
        
        String settingsJson = null;
        if (dto.getSettings() != null) {
            try {
                settingsJson = objectMapper.writeValueAsString(dto.getSettings());
            } catch (Exception e) {
                log.error("Failed to serialize settings to JSON", e);
                throw new IllegalArgumentException("Invalid settings format", e);
            }
        }
        
        return TemplateVersion.builder()
                .id(dto.getId())
                .version(dto.getVersion())
                .htmlContent(dto.getHtmlContent())
                .fieldSchema(fieldSchemaJson)
                .cssStyles(dto.getCssStyles())
                .settings(settingsJson)
                .status(dto.getStatus())
                .createdBy(dto.getCreatedBy())
                .build();
    }

    /**
     * Map TemplateVersion entity to TemplateVersionResponse.
     */
    private TemplateVersionResponse mapToDTO(TemplateVersion version) {
        Map<String, Object> fieldSchema = null;
        if (version.getFieldSchema() != null && !version.getFieldSchema().isEmpty()) {
            try {
                fieldSchema = objectMapper.readValue(version.getFieldSchema(), new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("Failed to parse fieldSchema JSON for version {}: {}", version.getId(), e.getMessage());
                fieldSchema = Map.of();
            }
        }
        
        Map<String, Object> settings = null;
        if (version.getSettings() != null && !version.getSettings().isEmpty()) {
            try {
                settings = objectMapper.readValue(version.getSettings(), new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("Failed to parse settings JSON for version {}: {}", version.getId(), e.getMessage());
                settings = Map.of();
            }
        }
        
        return TemplateVersionResponse.builder()
                .id(version.getId())
                .templateId(version.getTemplate().getId())
                .version(version.getVersion())
                .htmlContent(version.getHtmlContent())
                .fieldSchema(fieldSchema)
                .cssStyles(version.getCssStyles())
                .settings(settings)
                .status(version.getStatus())
                .createdBy(version.getCreatedBy())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
