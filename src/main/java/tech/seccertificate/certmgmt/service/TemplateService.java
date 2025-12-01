package tech.seccertificate.certmgmt.service;

import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.model.Template;
import tech.seccertificate.certmgmt.model.TemplateVersion;
import tech.seccertificate.certmgmt.repository.TemplateRepository;
import tech.seccertificate.certmgmt.repository.TemplateVersionRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing templates and template versioning.
 */
@Service
public class TemplateService {
    
    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    
    public TemplateService(TemplateRepository templateRepository, 
                          TemplateVersionRepository templateVersionRepository) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
    }
    
    /**
     * Create a new template for a customer.
     */
    public Template createTemplate(Long customerId, String name, String description) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        
        Template template = new Template(customerId, name, description);
        return templateRepository.save(template);
    }
    
    /**
     * Get template by ID.
     */
    public Optional<Template> getTemplateById(Long id) {
        return templateRepository.findById(id);
    }
    
    /**
     * Get all templates for a customer.
     */
    public List<Template> getTemplatesByCustomerId(Long customerId) {
        return templateRepository.findByCustomerId(customerId);
    }
    
    /**
     * Create a new version of a template.
     * Template versions are immutable once created.
     */
    public TemplateVersion createTemplateVersion(Long templateId, String jsonSchema, String templateContent) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        
        if (jsonSchema == null || jsonSchema.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON schema cannot be empty");
        }
        
        // Get the next version number
        List<TemplateVersion> existingVersions = templateVersionRepository
                .findByTemplateIdOrderByVersionNumberDesc(templateId);
        
        int nextVersionNumber = existingVersions.isEmpty() ? 1 : 
                existingVersions.get(0).getVersionNumber() + 1;
        
        TemplateVersion version = new TemplateVersion(
                templateId, 
                template.getCustomerId(), 
                nextVersionNumber, 
                jsonSchema
        );
        version.setTemplateContent(templateContent);
        version.setImmutable(true);
        
        return templateVersionRepository.save(version);
    }
    
    /**
     * Get a specific version of a template.
     */
    public Optional<TemplateVersion> getTemplateVersion(Long templateId, Integer versionNumber) {
        return templateVersionRepository.findByTemplateIdAndVersionNumber(templateId, versionNumber);
    }
    
    /**
     * Get all versions of a template.
     */
    public List<TemplateVersion> getTemplateVersions(Long templateId) {
        return templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
    }
    
    /**
     * Get the latest version of a template.
     */
    public Optional<TemplateVersion> getLatestTemplateVersion(Long templateId) {
        List<TemplateVersion> versions = templateVersionRepository
                .findByTemplateIdOrderByVersionNumberDesc(templateId);
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(0));
    }
    
    /**
     * Verify that a template version is immutable (cannot be modified).
     */
    public boolean isTemplateVersionImmutable(Long versionId) {
        return templateVersionRepository.findById(versionId)
                .map(TemplateVersion::isImmutable)
                .orElse(false);
    }
    
    /**
     * Deactivate a template.
     */
    public void deactivateTemplate(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        template.setActive(false);
        templateRepository.save(template);
    }
}
