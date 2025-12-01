package tech.seccertificate.certmgmt.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * TemplateVersion entity representing an immutable version of a template.
 */
@Table("template_versions")
public class TemplateVersion {
    
    @Id
    private Long id;
    private Long templateId;
    private Long customerId;
    private Integer versionNumber;
    private String jsonSchema;
    private String templateContent;
    private boolean immutable;
    private LocalDateTime createdAt;
    
    public TemplateVersion() {
        this.createdAt = LocalDateTime.now();
        this.immutable = true; // Versions are immutable by default
    }
    
    public TemplateVersion(Long templateId, Long customerId, Integer versionNumber, String jsonSchema) {
        this();
        this.templateId = templateId;
        this.customerId = customerId;
        this.versionNumber = versionNumber;
        this.jsonSchema = jsonSchema;
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }
    
    public Long getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
    
    public Integer getVersionNumber() {
        return versionNumber;
    }
    
    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }
    
    public String getJsonSchema() {
        return jsonSchema;
    }
    
    public void setJsonSchema(String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }
    
    public String getTemplateContent() {
        return templateContent;
    }
    
    public void setTemplateContent(String templateContent) {
        this.templateContent = templateContent;
    }
    
    public boolean isImmutable() {
        return immutable;
    }
    
    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
