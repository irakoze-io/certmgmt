package tech.seccertificate.certmgmt.service;

import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for template management operations.
 * Handles template CRUD operations, template versioning, and version validation.
 *
 */
public interface TemplateService {

    /**
     * Create a new template.
     *
     * @param template The template to create
     * @return The created template with generated ID
     * @throws IllegalArgumentException if template data is invalid
     */
    Template createTemplate(Template template);

    /**
     * Find a template by ID.
     *
     * @param templateId The template ID
     * @return Optional containing the template if found
     */
    Optional<Template> findById(UUID templateId);

    /**
     * Find a template by code.
     *
     * @param code The template code
     * @return Optional containing the template if found
     */
    Optional<Template> findByCode(String code);

    /**
     * Find all templates for a customer.
     *
     * @param customerId The customer ID
     * @return List of templates for the customer
     */
    List<Template> findByCustomerId(Long customerId);

    /**
     * Get all templates.
     *
     * @return List of all templates
     */
    List<Template> findAll();

    /**
     * Update template information.
     *
     * @param template The template with updated information
     * @return The updated template
     * @throws IllegalArgumentException if template is null or ID is missing
     */
    Template updateTemplate(Template template);

    /**
     * Delete a template by ID.
     * This will also delete all associated template versions.
     *
     * @param templateId The template ID
     * @throws IllegalArgumentException if template not found
     */
    void deleteTemplate(UUID templateId);

    /**
     * Check if a template code is already in use.
     *
     * @param code The template code to check
     * @return true if code is already in use, false otherwise
     */
    boolean isCodeTaken(String code);

    /**
     * Validate template data before creation or update.
     *
     * @param template The template to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateTemplate(Template template);

    // Template Version Management

    /**
     * Create a new template version.
     * Automatically increments the version number and updates the template's current version.
     *
     * @param templateId The template ID
     * @param templateVersion The template version to create
     * @return The created template version
     * @throws IllegalArgumentException if template not found or version data is invalid
     */
    TemplateVersion createTemplateVersion(UUID templateId, TemplateVersion templateVersion);

    /**
     * Find a template version by ID.
     *
     * @param versionId The template version ID
     * @return Optional containing the template version if found
     */
    Optional<TemplateVersion> findVersionById(UUID versionId);

    /**
     * Find a template version by template ID and version number.
     *
     * @param templateId The template ID
     * @param version The version number
     * @return Optional containing the template version if found
     */
    Optional<TemplateVersion> findVersionByTemplateIdAndVersion(UUID templateId, Integer version);

    /**
     * Find all versions for a template.
     *
     * @param templateId The template ID
     * @return List of template versions ordered by version number descending
     */
    List<TemplateVersion> findVersionsByTemplateId(UUID templateId);

    /**
     * Find the latest published version of a template.
     *
     * @param templateId The template ID
     * @return Optional containing the latest published version if found
     */
    Optional<TemplateVersion> findLatestPublishedVersion(UUID templateId);

    /**
     * Find the current version of a template (based on template's currentVersion field).
     *
     * @param templateId The template ID
     * @return Optional containing the current version if found
     */
    Optional<TemplateVersion> findCurrentVersion(UUID templateId);

    /**
     * Update template version information.
     *
     * @param templateVersion The template version with updated information
     * @return The updated template version
     * @throws IllegalArgumentException if template version is null or ID is missing
     */
    TemplateVersion updateTemplateVersion(TemplateVersion templateVersion);

    /**
     * Publish a template version.
     * Sets the version status to PUBLISHED and updates the template's current version.
     *
     * @param versionId The template version ID
     * @return The published template version
     * @throws IllegalArgumentException if template version not found
     */
    TemplateVersion publishVersion(UUID versionId);

    /**
     * Archive a template version.
     * Sets the version status to ARCHIVED.
     *
     * @param versionId The template version ID
     * @return The archived template version
     * @throws IllegalArgumentException if template version not found
     */
    TemplateVersion archiveVersion(UUID versionId);

    /**
     * Set a template version as draft.
     * Sets the version status to DRAFT.
     *
     * @param versionId The template version ID
     * @return The draft template version
     * @throws IllegalArgumentException if template version not found
     */
    TemplateVersion setVersionAsDraft(UUID versionId);

    /**
     * Validate template version data before creation or update.
     *
     * @param templateVersion The template version to validate
     * @param templateId The template ID this version belongs to
     * @throws IllegalArgumentException if validation fails
     */
    void validateTemplateVersion(TemplateVersion templateVersion, UUID templateId);

    /**
     * Check if a version number already exists for a template.
     *
     * @param templateId The template ID
     * @param version The version number to check
     * @return true if version already exists, false otherwise
     */
    boolean isVersionExists(UUID templateId, Integer version);

    /**
     * Get the next version number for a template.
     *
     * @param templateId The template ID
     * @return The next version number (1 if no versions exist)
     */
    Integer getNextVersionNumber(UUID templateId);
}
