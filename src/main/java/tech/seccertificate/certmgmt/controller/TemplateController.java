package tech.seccertificate.certmgmt.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.seccertificate.certmgmt.dto.template.TemplateDTO;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.service.TemplateService;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST controller for template management operations.
 * Handles template CRUD operations within the tenant context.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/templates - Create a new template</li>
 *   <li>GET /api/templates/{id} - Get template by ID</li>
 *   <li>GET /api/templates - Get all templates</li>
 *   <li>GET /api/templates/code/{code} - Get template by code</li>
 *   <li>PUT /api/templates/{id} - Update template</li>
 *   <li>DELETE /api/templates/{id} - Delete template</li>
 * </ul>
 * 
 * <p>Note: All operations require tenant context to be set (via X-Tenant-Id header).
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    /**
     * Create a new template.
     * 
     * @param templateDTO The template data
     * @return Created template DTO with 201 status
     */
    @PostMapping
    public ResponseEntity<TemplateDTO> createTemplate(@Valid @RequestBody TemplateDTO templateDTO) {
        log.info("Creating template: {}", templateDTO.getName());
        
        var template = mapToEntity(templateDTO);
        var createdTemplate = templateService.createTemplate(template);
        var response = mapToDTO(createdTemplate);
        
        var location = URI.create("/api/templates/" + createdTemplate.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    /**
     * Get template by ID.
     * 
     * @param id The template ID
     * @return Template DTO with 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<TemplateDTO> getTemplate(@PathVariable @NotNull Long id) {
        log.debug("Getting template with ID: {}", id);
        
        return templateService.findById(id)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get template by code.
     * 
     * @param code The template code
     * @return Template DTO with 200 status, or 404 if not found
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<TemplateDTO> getTemplateByCode(@PathVariable @NotNull String code) {
        log.debug("Getting template with code: {}", code);
        
        return templateService.findByCode(code)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all templates in the current tenant context.
     * 
     * @return List of template DTOs with 200 status
     */
    @GetMapping
    public ResponseEntity<List<TemplateDTO>> getAllTemplates() {
        log.debug("Getting all templates");
        
        var templates = templateService.findAll();
        var templateDTOs = templates.stream()
                .map(this::mapToDTO)
                .toList();
        
        return ResponseEntity.ok(templateDTOs);
    }

    /**
     * Update template.
     * 
     * @param id The template ID
     * @param templateDTO The updated template data
     * @return Updated template DTO with 200 status, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<TemplateDTO> updateTemplate(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody TemplateDTO templateDTO) {
        log.info("Updating template with ID: {}", id);

        templateDTO.setId(id);
        
        var template = mapToEntity(templateDTO);
        var updatedTemplate = templateService.updateTemplate(template);
        var response = mapToDTO(updatedTemplate);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete template by ID.
     * This will also delete all associated template versions.
     * 
     * @param id The template ID
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable @NotNull Long id) {
        log.info("Deleting template with ID: {}", id);
        
        if (templateService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Map TemplateDTO to Template entity.
     */
    private Template mapToEntity(TemplateDTO dto) {
        String metadataJson = null;
        if (dto.getMetadata() != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(dto.getMetadata());
            } catch (Exception e) {
                log.error("Failed to serialize metadata to JSON", e);
                throw new IllegalArgumentException("Invalid metadata format", e);
            }
        }
        
        return Template.builder()
                .id(dto.getId())
                .customerId(dto.getCustomerId())
                .name(dto.getName())
                .code(dto.getCode())
                .description(dto.getDescription())
                .currentVersion(dto.getCurrentVersion())
                .metadata(metadataJson)
                .build();
    }

    /**
     * Map Template entity to TemplateDTO.
     */
    private TemplateDTO mapToDTO(Template template) {
        Map<String, Object> metadata = null;
        if (template.getMetadata() != null && !template.getMetadata().isEmpty()) {
            try {
                metadata = objectMapper.readValue(template.getMetadata(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON for template {}: {}", template.getId(), e.getMessage());
                metadata = Map.of();
            }
        }
        
        return TemplateDTO.builder()
                .id(template.getId())
                .customerId(template.getCustomerId())
                .name(template.getName())
                .code(template.getCode())
                .description(template.getDescription())
                .currentVersion(template.getCurrentVersion())
                .metadata(metadata)
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
