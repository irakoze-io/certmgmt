package tech.seccertificate.certmgmt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantSchemaValidator;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.repository.TemplateRepository;
import tech.seccertificate.certmgmt.repository.TemplateVersionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of TemplateService.
 * Handles template CRUD operations, template versioning, and version validation.
 *
 * <p>All operations require tenant context to be set (via TenantRequestInterceptor
 * or programmatically using TenantService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TenantSchemaValidator tenantSchemaValidator;

    @Override
    @Transactional
    public Template createTemplate(Template template) {
        log.info("Creating template: {}", template.getName());

        tenantSchemaValidator.validateTenantSchema("createTemplate");
        validateTemplate(template);

        if (isCodeTaken(template.getCode())) {
            throw new IllegalArgumentException("Template code is already in use: " + template.getCode());
        }

        // Set defaults
        if (template.getCurrentVersion() == null) {
            template.setCurrentVersion(1);
        }
        if (template.getMetadata() == null || template.getMetadata().isEmpty()) {
            template.setMetadata("{}");
        }

        try {
            var savedTemplate = templateRepository.save(template);
            log.info("Template created successfully with ID: {}", savedTemplate.getId());
            return savedTemplate;
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to create template due to data integrity violation", e);
            throw new IllegalArgumentException("Template data violates constraints: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> findById(Long templateId) {
        tenantSchemaValidator.validateTenantSchema("findById");
        return templateRepository.findById(templateId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Template> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }
        tenantSchemaValidator.validateTenantSchema("findByCode");
        return templateRepository.findByCode(code.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> findByCustomerId(Long customerId) {
        tenantSchemaValidator.validateTenantSchema("findByCustomerId");
        return templateRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Template> findAll() {
        tenantSchemaValidator.validateTenantSchema("findAll");
        return templateRepository.findAll();
    }

    @Override
    @Transactional
    public Template updateTemplate(Template template) {
        if (template == null || template.getId() == null) {
            throw new IllegalArgumentException("Template and template ID must not be null");
        }

        tenantSchemaValidator.validateTenantSchema("updateTemplate");

        var existingTemplate = templateRepository.findById(template.getId())
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + template.getId()));

        validateTemplate(template);

        // Check if code is being changed and if new code is taken
        if (template.getCode() != null && !template.getCode().equals(existingTemplate.getCode())) {
            if (isCodeTaken(template.getCode())) {
                throw new IllegalArgumentException("Template code is already in use: " + template.getCode());
            }
            existingTemplate.setCode(template.getCode());
        }

        // Update allowed fields
        if (template.getName() != null) {
            existingTemplate.setName(template.getName());
        }
        if (template.getDescription() != null) {
            existingTemplate.setDescription(template.getDescription());
        }
        if (template.getMetadata() != null) {
            existingTemplate.setMetadata(template.getMetadata());
        }

        /*
         * Note: currentVersion should be updated via version management methods
         * (createVersion, publishVersion, archiveVersion, setVersionAsDraft)
         * TODO: implement a method to update currentVersion directly
         */
        var updatedTemplate = templateRepository.save(existingTemplate);
        log.info("Template updated successfully: {}", updatedTemplate.getId());
        return updatedTemplate;
    }

    @Override
    @Transactional
    public void deleteTemplate(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("Template ID must not be null");
        }

        tenantSchemaValidator.validateTenantSchema("deleteTemplate");

        var template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        // Delete all versions first (cascade should handle this, but explicit for clarity)
        var versions = templateVersionRepository.findByTemplate_Id(templateId);
        if (!versions.isEmpty()) {
            templateVersionRepository.deleteAll(versions);
            log.debug("Deleted {} versions for template {}", versions.size(), templateId);
        }

        templateRepository.delete(template);
        log.info("Template deleted successfully: {}", templateId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCodeTaken(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        tenantSchemaValidator.validateTenantSchema("isCodeTaken");
        return templateRepository.existsByCode(code.trim());
    }

    @Override
    public void validateTemplate(Template template) {
        if (template == null) {
            throw new IllegalArgumentException("Template cannot be null");
        }

        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }

        if (template.getName().length() > 255) {
            throw new IllegalArgumentException("Template name must not exceed 255 characters");
        }

        if (template.getCode() == null || template.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Template code is required");
        }

        // Validate code format: alphanumeric, underscore, hyphen, max 100 chars
        var code = template.getCode().trim();
        if (!code.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Template code must contain only alphanumeric characters, underscores, and hyphens");
        }

        if (code.length() > 100) {
            throw new IllegalArgumentException("Template code must not exceed 100 characters");
        }

        if (template.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        if (template.getCurrentVersion() != null && template.getCurrentVersion() < 1) {
            throw new IllegalArgumentException("Current version must be at least 1");
        }
    }

    // Template Version Management
    @Override
    @Transactional
    public TemplateVersion createTemplateVersion(Long templateId, TemplateVersion templateVersion) {
        if (templateId == null) {
            throw new IllegalArgumentException("Template ID must not be null");
        }
        if (templateVersion == null) {
            throw new IllegalArgumentException("Template version cannot be null");
        }

        tenantSchemaValidator.validateTenantSchema("createTemplateVersion");

        var template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        validateTemplateVersion(templateVersion, templateId);

        // Set template relationship
        templateVersion.setTemplate(template);

        // Auto-increment version if not provided
        if (templateVersion.getVersion() == null) {
            var nextVersion = getNextVersionNumber(templateId);
            templateVersion.setVersion(nextVersion);
        } else {
            // Check if version already exists
            if (isVersionExists(templateId, templateVersion.getVersion())) {
                throw new IllegalArgumentException(
                        String.format("Version %d already exists for template %d",
                                templateVersion.getVersion(), templateId)
                );
            }
        }

        // Set defaults
        if (templateVersion.getStatus() == null) {
            templateVersion.setStatus(TemplateVersion.TemplateVersionStatus.DRAFT);
        }
        if (templateVersion.getSettings() == null || templateVersion.getSettings().isEmpty()) {
            templateVersion.setSettings("{}");
        }

        var savedVersion = templateVersionRepository.save(templateVersion);

        // Update template's current version if this is the first version or if explicitly set
        if (template.getCurrentVersion() == null || template.getCurrentVersion() < savedVersion.getVersion()) {
            template.setCurrentVersion(savedVersion.getVersion());
            templateRepository.save(template);
        }

        log.info("Template version created successfully: {} for template {}",
                savedVersion.getVersion(), templateId);
        return savedVersion;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateVersion> findVersionById(UUID versionId) {
        tenantSchemaValidator.validateTenantSchema("findVersionById");
        return templateVersionRepository.findById(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateVersion> findVersionByTemplateIdAndVersion(Long templateId, Integer version) {
        tenantSchemaValidator.validateTenantSchema("findVersionByTemplateIdAndVersion");
        return templateVersionRepository.findByTemplate_IdAndVersion(templateId, version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateVersion> findVersionsByTemplateId(Long templateId) {
        tenantSchemaValidator.validateTenantSchema("findVersionsByTemplateId");
        return templateVersionRepository.findByTemplate_IdOrderByVersionDesc(templateId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateVersion> findLatestPublishedVersion(Long templateId) {
        tenantSchemaValidator.validateTenantSchema("findLatestPublishedVersion");
        return templateVersionRepository.findFirstByTemplate_IdAndStatusOrderByVersionDesc(
                templateId, TemplateVersion.TemplateVersionStatus.PUBLISHED);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemplateVersion> findCurrentVersion(Long templateId) {
        tenantSchemaValidator.validateTenantSchema("findCurrentVersion");

        var template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (template.getCurrentVersion() == null) {
            return Optional.empty();
        }

        return templateVersionRepository.findByTemplate_IdAndVersion(
                templateId, template.getCurrentVersion());
    }

    @Override
    @Transactional
    public TemplateVersion updateTemplateVersion(TemplateVersion templateVersion) {
        if (templateVersion == null || templateVersion.getId() == null) {
            throw new IllegalArgumentException("Template version and version ID must not be null");
        }

        tenantSchemaValidator.validateTenantSchema("updateTemplateVersion");

        var existingVersion = templateVersionRepository.findById(templateVersion.getId())
                .orElseThrow(() -> new IllegalArgumentException("Template version not found: " + templateVersion.getId()));

        var templateId = existingVersion.getTemplate().getId();
        validateTemplateVersion(templateVersion, templateId);

        // Version number cannot be changed
        if (templateVersion.getVersion() != null &&
                !templateVersion.getVersion().equals(existingVersion.getVersion())) {
            throw new IllegalArgumentException("Version number cannot be changed");
        }

        // Update allowed fields
        if (templateVersion.getHtmlContent() != null) {
            existingVersion.setHtmlContent(templateVersion.getHtmlContent());
        }
        if (templateVersion.getFieldSchema() != null) {
            existingVersion.setFieldSchema(templateVersion.getFieldSchema());
        }
        if (templateVersion.getCssStyles() != null) {
            existingVersion.setCssStyles(templateVersion.getCssStyles());
        }
        if (templateVersion.getSettings() != null) {
            existingVersion.setSettings(templateVersion.getSettings());
        }
        // Status should be updated via specific methods (publish, archive, setDraft)

        var updatedVersion = templateVersionRepository.save(existingVersion);
        log.info("Template version updated successfully: {}", updatedVersion.getId());
        return updatedVersion;
    }

    @Override
    @Transactional
    public TemplateVersion publishVersion(UUID versionId) {
        tenantSchemaValidator.validateTenantSchema("publishVersion");

        var version = templateVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Template version not found: " + versionId));

        version.setStatus(TemplateVersion.TemplateVersionStatus.PUBLISHED);

        // Update template's current version
        var template = version.getTemplate();
        template.setCurrentVersion(version.getVersion());
        templateRepository.save(template);

        var publishedVersion = templateVersionRepository.save(version);
        log.info("Template version published: {} for template {}",
                version.getVersion(), template.getId());
        return publishedVersion;
    }

    @Override
    @Transactional
    public TemplateVersion archiveVersion(UUID versionId) {
        tenantSchemaValidator.validateTenantSchema("archiveVersion");

        var version = templateVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Template version not found: " + versionId));

        version.setStatus(TemplateVersion.TemplateVersionStatus.ARCHIVED);

        var archivedVersion = templateVersionRepository.save(version);
        log.info("Template version archived: {} for template {}",
                version.getVersion(), version.getTemplate().getId());
        return archivedVersion;
    }

    @Override
    @Transactional
    public TemplateVersion setVersionAsDraft(UUID versionId) {
        tenantSchemaValidator.validateTenantSchema("setVersionAsDraft");

        var version = templateVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Template version not found: " + versionId));

        version.setStatus(TemplateVersion.TemplateVersionStatus.DRAFT);

        var draftVersion = templateVersionRepository.save(version);
        log.info("Template version set as draft: {} for template {}",
                version.getVersion(), version.getTemplate().getId());
        return draftVersion;
    }

    @Override
    public void validateTemplateVersion(TemplateVersion templateVersion, Long templateId) {
        if (templateVersion == null) {
            throw new IllegalArgumentException("Template version cannot be null");
        }

        if (templateId == null) {
            throw new IllegalArgumentException("Template ID cannot be null");
        }

        if (templateVersion.getHtmlContent() == null || templateVersion.getHtmlContent().trim().isEmpty()) {
            throw new IllegalArgumentException("HTML content is required");
        }

        if (templateVersion.getFieldSchema() == null || templateVersion.getFieldSchema().trim().isEmpty()) {
            throw new IllegalArgumentException("Field schema is required");
        }

        // Validate field schema is valid JSON (basic check)
        var fieldSchema = templateVersion.getFieldSchema().trim();
        if (!fieldSchema.startsWith("{") && !fieldSchema.startsWith("[")) {
            throw new IllegalArgumentException("Field schema must be valid JSON");
        }

        if (templateVersion.getVersion() != null && templateVersion.getVersion() < 1) {
            throw new IllegalArgumentException("Version number must be at least 1");
        }

        if (templateVersion.getCreatedBy() == null) {
            throw new IllegalArgumentException("Created by (user ID) is required");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVersionExists(Long templateId, Integer version) {
        if (templateId == null || version == null) {
            return false;
        }
        tenantSchemaValidator.validateTenantSchema("isVersionExists");
        return templateVersionRepository.existsByTemplate_IdAndVersion(templateId, version);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getNextVersionNumber(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("Template ID must not be null");
        }

        tenantSchemaValidator.validateTenantSchema("getNextVersionNumber");

        var versionCount = templateVersionRepository.countByTemplate_Id(templateId);

        if (versionCount == 0) {
            return 1;
        }

        // Find the highest version number
        var versions = templateVersionRepository.findByTemplate_IdOrderByVersionDesc(templateId);
        if (versions.isEmpty()) {
            return 1;
        }

        var maxVersion = versions.getFirst().getVersion();
        return maxVersion != null ? maxVersion + 1 : 1;
    }
}
