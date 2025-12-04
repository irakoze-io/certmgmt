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
import tech.seccertificate.certmgmt.dto.Response;
import tech.seccertificate.certmgmt.dto.template.TemplateResponse;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.exception.ApplicationObjectNotFoundException;
import tech.seccertificate.certmgmt.exception.TemplateNotFoundException;
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
     * @param templateResponse The template data
     * @return Created template response with 201 status
     */
    @PostMapping
    public ResponseEntity<Response<TemplateResponse>> createTemplate(@Valid @RequestBody TemplateResponse templateResponse) {
        log.info("Creating template: {}", templateResponse.getName());

        var template = mapToEntity(templateResponse);
        var createdTemplate = templateService.createTemplate(template);
        var response = mapToDTO(createdTemplate);
        var unifiedResponse = Response.success(
                "Template created successfully",
                response
        );

        var location = URI.create("/api/templates/" + createdTemplate.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(unifiedResponse);
    }

    /**
     * Get template by ID.
     *
     * @param id The template ID
     * @return Template response with 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Response<TemplateResponse>> getTemplate(@PathVariable @NotNull Long id) {
        log.debug("Getting template with ID: {}", id);

        var template = templateService.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(id));

        var response = mapToDTO(template);
        var unifiedResponse = Response.success(
                "Template retrieved successfully",
                response
        );

        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Get template by code.
     *
     * @param code The template code
     * @return Template response with 200 status, or 404 if not found
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Response<TemplateResponse>> getTemplateByCode(@PathVariable @NotNull String code) {
        log.debug("Getting template with code: {}", code);

        var template = templateService.findByCode(code)
                .orElseThrow(() -> new TemplateNotFoundException(code));

        var response = mapToDTO(template);
        var unifiedResponse = Response.success(
                "Template retrieved successfully",
                response
        );

        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Get all templates in the current tenant context.
     *
     * @return List of template responses with 200 status
     */
    @GetMapping
    public ResponseEntity<List<TemplateResponse>> getAllTemplates() {
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
     * @param templateResponse The updated template data
     * @return Updated template response with 200 status, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Response<TemplateResponse>> updateTemplate(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody TemplateResponse templateResponse) {
        log.info("Updating template with ID: {}", id);

        templateResponse.setId(id);

        var template = mapToEntity(templateResponse);
        var updatedTemplate = templateService.updateTemplate(template);
        var response = mapToDTO(updatedTemplate);
        var unifiedResponse = Response.success(
                "Template updated successfully",
                response
        );

        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Delete template by ID.
     * This will also delete all associated template versions.
     *
     * @param id The template ID
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Response<Void>> deleteTemplate(@PathVariable @NotNull Long id) {
        log.info("Deleting template with ID: {}", id);

        if (templateService.findById(id).isEmpty()) {
            throw new ApplicationObjectNotFoundException("Template with ID " + id + " not found");
        }

        templateService.deleteTemplate(id);
        var unifiedResponse = Response.<Void>success(
                "Template deleted successfully",
                null
        );

        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(unifiedResponse);
    }

    /**
     * Map TemplateResponse to Template entity.
     */
    private Template mapToEntity(TemplateResponse dto) {
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
     * Map Template entity to TemplateResponse.
     */
    private TemplateResponse mapToDTO(Template template) {
        Map<String, Object> metadata = null;
        if (template.getMetadata() != null && !template.getMetadata().isEmpty()) {
            try {
                metadata = objectMapper.readValue(template.getMetadata(), new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON for template {}: {}", template.getId(), e.getMessage());
                metadata = Map.of();
            }
        }

        return TemplateResponse.builder()
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
