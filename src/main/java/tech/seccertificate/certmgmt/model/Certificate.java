package tech.seccertificate.certmgmt.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Certificate entity representing a generated certificate.
 */
@Table("certificates")
public class Certificate {
    
    @Id
    private Long id;
    private Long customerId;
    private Long templateId;
    private Long templateVersionId;
    private String certificateData;
    private String pdfUrl;
    private String hash;
    private String signature;
    private CertificateStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public Certificate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = CertificateStatus.PENDING;
    }
    
    public Certificate(Long customerId, Long templateId, Long templateVersionId, String certificateData) {
        this();
        this.customerId = customerId;
        this.templateId = templateId;
        this.templateVersionId = templateVersionId;
        this.certificateData = certificateData;
    }
    
    public enum CertificateStatus {
        PENDING, GENERATING, COMPLETED, FAILED
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
    
    public Long getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }
    
    public Long getTemplateVersionId() {
        return templateVersionId;
    }
    
    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
    }
    
    public String getCertificateData() {
        return certificateData;
    }
    
    public void setCertificateData(String certificateData) {
        this.certificateData = certificateData;
    }
    
    public String getPdfUrl() {
        return pdfUrl;
    }
    
    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(String signature) {
        this.signature = signature;
    }
    
    public CertificateStatus getStatus() {
        return status;
    }
    
    public void setStatus(CertificateStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
