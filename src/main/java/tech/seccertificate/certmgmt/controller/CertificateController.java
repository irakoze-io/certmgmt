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
import tech.seccertificate.certmgmt.dto.certificate.CertificateResponse;
import tech.seccertificate.certmgmt.dto.certificate.GenerateCertificateRequest;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.service.CertificateService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for certificate management operations.
 * Handles certificate generation (sync/async), CRUD operations, and verification.
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/certificates - Generate a certificate (sync or async)</li>
 *   <li>GET /api/certificates/{id} - Get certificate by ID</li>
 *   <li>GET /api/certificates - Get all certificates (with optional filters)</li>
 *   <li>GET /api/certificates/number/{certificateNumber} - Get by certificate number</li>
 *   <li>PUT /api/certificates/{id} - Update certificate</li>
 *   <li>DELETE /api/certificates/{id} - Delete certificate</li>
 *   <li>POST /api/certificates/{id}/revoke - Revoke a certificate</li>
 *   <li>GET /api/certificates/{id}/download-url - Get signed download URL</li>
 *   <li>GET /api/certificates/verify/{hash} - Public verification endpoint</li>
 * </ul>
 * 
 * <p>Note: All operations require tenant context to be set (via X-Tenant-Id header),
 * except the public verification endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final ObjectMapper objectMapper;

    /**
     * Generate a certificate (synchronously or asynchronously).
     * 
     * @param request The certificate generation request
     * @return Created certificate response with 201 status
     */
    @PostMapping
    public ResponseEntity<CertificateResponse> generateCertificate(
            @Valid @RequestBody GenerateCertificateRequest request) {
        log.info("Generating certificate for template version: {}", request.getTemplateVersionId());
        
        var certificate = mapToEntity(request);
        Certificate createdCertificate;
        
        if (Boolean.TRUE.equals(request.getSynchronous())) {
            log.debug("Generating certificate synchronously");
            createdCertificate = certificateService.generateCertificate(certificate);
        } else {
            log.debug("Generating certificate asynchronously");
            createdCertificate = certificateService.generateCertificateAsync(certificate);
        }
        
        var response = mapToDTO(createdCertificate);
        var location = URI.create("/api/certificates/" + createdCertificate.getId());
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    /**
     * Get certificate by ID.
     * 
     * @param id The certificate ID
     * @return Certificate response with 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<CertificateResponse> getCertificate(@PathVariable @NotNull UUID id) {
        log.debug("Getting certificate with ID: {}", id);
        
        return certificateService.findById(id)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get certificate by certificate number.
     * 
     * @param certificateNumber The certificate number
     * @return Certificate response with 200 status, or 404 if not found
     */
    @GetMapping("/number/{certificateNumber}")
    public ResponseEntity<CertificateResponse> getCertificateByNumber(
            @PathVariable @NotNull String certificateNumber) {
        log.debug("Getting certificate with number: {}", certificateNumber);
        
        return certificateService.findByCertificateNumber(certificateNumber)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all certificates with optional filters.
     * 
     * @param customerId Optional customer ID filter
     * @param status Optional status filter
     * @param templateVersionId Optional template version ID filter
     * @return List of certificate responses with 200 status
     */
    @GetMapping
    public ResponseEntity<List<CertificateResponse>> getAllCertificates(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Certificate.CertificateStatus status,
            @RequestParam(required = false) UUID templateVersionId) {
        log.debug("Getting certificates with filters - customerId: {}, status: {}, templateVersionId: {}",
                customerId, status, templateVersionId);
        
        List<Certificate> certificates;
        
        if (customerId != null && status != null) {
            certificates = certificateService.findByCustomerIdAndStatus(customerId, status);
        } else if (customerId != null) {
            certificates = certificateService.findByCustomerId(customerId);
        } else if (status != null) {
            certificates = certificateService.findByStatus(status);
        } else if (templateVersionId != null) {
            certificates = certificateService.findByTemplateVersionId(templateVersionId);
        } else {
            // If no filters, return empty list (or implement findAll if service has it)
            certificates = List.of();
        }
        
        var certificateDTOs = certificates.stream()
                .map(this::mapToDTO)
                .toList();
        
        return ResponseEntity.ok(certificateDTOs);
    }

    /**
     * Update certificate.
     * 
     * @param id The certificate ID
     * @param certificateResponse The updated certificate data
     * @return Updated certificate response with 200 status, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<CertificateResponse> updateCertificate(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody CertificateResponse certificateResponse) {
        log.info("Updating certificate with ID: {}", id);
        
        certificateResponse.setId(id);
        var certificate = mapToEntity(certificateResponse);
        var updatedCertificate = certificateService.updateCertificate(certificate);
        var response = mapToDTO(updatedCertificate);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete certificate by ID.
     * 
     * @param id The certificate ID
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable @NotNull UUID id) {
        log.info("Deleting certificate with ID: {}", id);
        
        if (certificateService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        certificateService.deleteCertificate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revoke a certificate.
     * Sets status to REVOKED.
     * 
     * @param id The certificate ID
     * @return Revoked certificate response with 200 status, or 404 if not found
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<CertificateResponse> revokeCertificate(@PathVariable @NotNull UUID id) {
        log.info("Revoking certificate with ID: {}", id);
        
        var revokedCertificate = certificateService.revokeCertificate(id);
        var response = mapToDTO(revokedCertificate);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get signed download URL for certificate PDF.
     * 
     * @param id The certificate ID
     * @param expirationMinutes Optional expiration time in minutes (default: 60)
     * @return Download URL response with 200 status, or 404 if not found
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable @NotNull UUID id,
            @RequestParam(required = false, defaultValue = "60") Integer expirationMinutes) {
        log.debug("Getting download URL for certificate ID: {} with expiration: {} minutes", id, expirationMinutes);
        
        if (certificateService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        var downloadUrl = certificateService.getCertificateDownloadUrl(id, expirationMinutes);
        var response = Map.of("downloadUrl", downloadUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Public verification endpoint for certificate hash.
     * This endpoint doesn't require authentication and can be used for public verification.
     * 
     * @param hash The certificate hash to verify
     * @return Certificate response with 200 status if valid, or 404 if not found/invalid
     */
    @GetMapping("/verify/{hash}")
    public ResponseEntity<CertificateResponse> verifyCertificate(@PathVariable @NotNull String hash) {
        log.debug("Verifying certificate with hash: {}", hash);
        
        return certificateService.verifyCertificateByHash(hash)
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Map GenerateCertificateRequest to Certificate entity.
     */
    private Certificate mapToEntity(GenerateCertificateRequest request) {
        String recipientDataJson = null;
        if (request.getRecipientData() != null) {
            try {
                recipientDataJson = objectMapper.writeValueAsString(request.getRecipientData());
            } catch (Exception e) {
                log.error("Failed to serialize recipientData to JSON", e);
                throw new IllegalArgumentException("Invalid recipientData format", e);
            }
        }
        
        String metadataJson = null;
        if (request.getMetadata() != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            } catch (Exception e) {
                log.error("Failed to serialize metadata to JSON", e);
                throw new IllegalArgumentException("Invalid metadata format", e);
            }
        }
        
        return Certificate.builder()
                .templateVersionId(request.getTemplateVersionId())
                .certificateNumber(request.getCertificateNumber())
                .recipientData(recipientDataJson)
                .metadata(metadataJson)
                .issuedAt(request.getIssuedAt())
                .expiresAt(request.getExpiresAt())
                .issuedBy(request.getIssuedBy())
                .build();
    }

    /**
     * Map CertificateResponse to Certificate entity.
     */
    private Certificate mapToEntity(CertificateResponse dto) {
        String recipientDataJson = null;
        if (dto.getRecipientData() != null) {
            try {
                recipientDataJson = objectMapper.writeValueAsString(dto.getRecipientData());
            } catch (Exception e) {
                log.error("Failed to serialize recipientData to JSON", e);
                throw new IllegalArgumentException("Invalid recipientData format", e);
            }
        }
        
        String metadataJson = null;
        if (dto.getMetadata() != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(dto.getMetadata());
            } catch (Exception e) {
                log.error("Failed to serialize metadata to JSON", e);
                throw new IllegalArgumentException("Invalid metadata format", e);
            }
        }
        
        return Certificate.builder()
                .id(dto.getId())
                .customerId(dto.getCustomerId())
                .templateVersionId(dto.getTemplateVersionId())
                .certificateNumber(dto.getCertificateNumber())
                .recipientData(recipientDataJson)
                .metadata(metadataJson)
                .storagePath(dto.getStoragePath())
                .signedHash(dto.getSignedHash())
                .status(dto.getStatus())
                .issuedAt(dto.getIssuedAt())
                .expiresAt(dto.getExpiresAt())
                .issuedBy(dto.getIssuedBy())
                .build();
    }

    /**
     * Map Certificate entity to CertificateResponse.
     */
    private CertificateResponse mapToDTO(Certificate certificate) {
        Map<String, Object> recipientData = null;
        if (certificate.getRecipientData() != null && !certificate.getRecipientData().isEmpty()) {
            try {
                recipientData = objectMapper.readValue(certificate.getRecipientData(), new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("Failed to parse recipientData JSON for certificate {}: {}", certificate.getId(), e.getMessage());
                recipientData = Map.of();
            }
        }
        
        Map<String, Object> metadata = null;
        if (certificate.getMetadata() != null && !certificate.getMetadata().isEmpty()) {
            try {
                metadata = objectMapper.readValue(certificate.getMetadata(), new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON for certificate {}: {}", certificate.getId(), e.getMessage());
                metadata = Map.of();
            }
        }
        
        return CertificateResponse.builder()
                .id(certificate.getId())
                .customerId(certificate.getCustomerId())
                .templateVersionId(certificate.getTemplateVersionId())
                .certificateNumber(certificate.getCertificateNumber())
                .recipientData(recipientData)
                .metadata(metadata)
                .storagePath(certificate.getStoragePath())
                .signedHash(certificate.getSignedHash())
                .status(certificate.getStatus())
                .issuedAt(certificate.getIssuedAt())
                .expiresAt(certificate.getExpiresAt())
                .issuedBy(certificate.getIssuedBy())
                .createdAt(certificate.getCreatedAt())
                .updatedAt(certificate.getUpdatedAt())
                .build();
    }
}
