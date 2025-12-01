package tech.seccertificate.certmgmt.service;

import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.model.Certificate;
import tech.seccertificate.certmgmt.model.TemplateVersion;
import tech.seccertificate.certmgmt.repository.CertificateRepository;
import tech.seccertificate.certmgmt.repository.TemplateVersionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing certificate generation and verification.
 */
@Service
public class CertificateService {
    
    private final CertificateRepository certificateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    
    public CertificateService(CertificateRepository certificateRepository,
                            TemplateVersionRepository templateVersionRepository) {
        this.certificateRepository = certificateRepository;
        this.templateVersionRepository = templateVersionRepository;
    }
    
    /**
     * Generate a certificate using a template version.
     */
    public Certificate generateCertificate(Long customerId, Long templateId, 
                                          Long templateVersionId, String certificateData) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (templateVersionId == null) {
            throw new IllegalArgumentException("Template version ID cannot be null");
        }
        
        // Verify template version exists and belongs to customer
        TemplateVersion templateVersion = templateVersionRepository.findById(templateVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Template version not found"));
        
        if (!templateVersion.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Template version does not belong to customer");
        }
        
        Certificate certificate = new Certificate(customerId, templateId, templateVersionId, certificateData);
        certificate.setStatus(Certificate.CertificateStatus.PENDING);
        
        // Generate hash for the certificate
        String hash = generateHash(certificateData);
        certificate.setHash(hash);
        
        // Generate signature (simplified for now)
        certificate.setSignature(generateSignature(hash));
        
        return certificateRepository.save(certificate);
    }
    
    /**
     * Get certificate by ID.
     */
    public Optional<Certificate> getCertificateById(Long id) {
        return certificateRepository.findById(id);
    }
    
    /**
     * Get all certificates for a customer.
     */
    public List<Certificate> getCertificatesByCustomerId(Long customerId) {
        return certificateRepository.findByCustomerId(customerId);
    }
    
    /**
     * Update certificate status.
     */
    public Certificate updateCertificateStatus(Long certificateId, Certificate.CertificateStatus status) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        certificate.setStatus(status);
        return certificateRepository.save(certificate);
    }
    
    /**
     * Set PDF URL for a certificate.
     */
    public Certificate setPdfUrl(Long certificateId, String pdfUrl) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        certificate.setPdfUrl(pdfUrl);
        certificate.setStatus(Certificate.CertificateStatus.COMPLETED);
        return certificateRepository.save(certificate);
    }
    
    /**
     * Verify certificate integrity.
     */
    public boolean verifyCertificate(Long certificateId, String providedHash) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
        
        if (certificate.getHash() == null || providedHash == null) {
            return false;
        }
        
        return certificate.getHash().equals(providedHash);
    }
    
    /**
     * Generate SHA-256 hash for certificate data.
     */
    private String generateHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate hash", e);
        }
    }
    
    /**
     * Generate signature for certificate (simplified implementation).
     */
    private String generateSignature(String hash) {
        // In production, this would use proper digital signature algorithms
        return "sig_" + hash.substring(0, 16);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
