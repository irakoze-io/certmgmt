package tech.seccertificate.certmgmt.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.seccertificate.certmgmt.dto.Response;
import tech.seccertificate.certmgmt.dto.certificate.CertificateResponse;
import tech.seccertificate.certmgmt.dto.certificate.GenerateCertificateRequest;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.exception.ApplicationObjectNotFoundException;
import tech.seccertificate.certmgmt.service.CertificateService;
import tech.seccertificate.certmgmt.service.QrCodeService;

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
@Tag(name = "Certificates", description = "Certificate generation, management, and verification operations")
public class CertificateController {

    private final CertificateService certificateService;
    private final QrCodeService qrCodeService;
    private final ObjectMapper objectMapper;

    /**
     * Generate a certificate (synchronously or asynchronously).
     *
     * @param request The certificate generation request
     * @return Created certificate response with 201 status
     */
    @PostMapping
    public ResponseEntity<Response<CertificateResponse>> generateCertificate(
            @Valid @RequestBody GenerateCertificateRequest request) {
        boolean isPreview = Boolean.TRUE.equals(request.getPreview());
        log.info("Generating certificate {} for template version: {}",
                isPreview ? "preview" : "", request.getTemplateVersionId());

        var certificate = mapToEntity(request);
        Certificate createdCertificate;

        if (Boolean.TRUE.equals(request.getSynchronous())) {
            log.debug("Generating certificate synchronously");
            createdCertificate = certificateService.generateCertificate(certificate, isPreview);
        } else {
            log.debug("Generating certificate asynchronously");
            createdCertificate = certificateService.generateCertificateAsync(certificate, isPreview);
        }

        var response = mapToDTO(createdCertificate);
        var location = URI.create("/api/certificates/" + createdCertificate.getId());
        var message = isPreview ?
                "Certificate preview generated successfully" :
                "Certificate generated successfully";
        var unifiedResponse = Response.success(message, response);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(unifiedResponse);
    }

    /**
     * Get certificate by ID.
     * 
     * @param id The certificate ID
     * @return Certificate response with 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Response<CertificateResponse>> getCertificate(@PathVariable @NotNull UUID id) {
        log.debug("Getting certificate with ID: {}", id);
        
        var certificate = certificateService.findById(id)
                .orElseThrow(() -> new ApplicationObjectNotFoundException("Certificate with ID " + id + " not found"));
        
        var response = mapToDTO(certificate);
        var unifiedResponse = Response.success(
                "Certificate retrieved successfully",
                response
        );

        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Get certificate by certificate number.
     * 
     * @param certificateNumber The certificate number
     * @return Certificate response with 200 status, or 404 if not found
     */
    @GetMapping("/number/{certificateNumber}")
    public ResponseEntity<Response<CertificateResponse>> getCertificateByNumber(
            @PathVariable @NotNull String certificateNumber) {
        log.debug("Getting certificate with number: {}", certificateNumber);
        
        var certificate = certificateService.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> new ApplicationObjectNotFoundException("Certificate with number " + certificateNumber + " not found"));
        
        var response = mapToDTO(certificate);
        var unifiedResponse = Response.success(
                "Certificate retrieved successfully",
                response
        );
        
        return ResponseEntity.ok(unifiedResponse);
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
    public ResponseEntity<Response<List<CertificateResponse>>> getAllCertificates(
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
        
        var unifiedResponse = Response.success(
                "Certificates retrieved successfully",
                certificateDTOs
        );
        
        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Update certificate.
     * 
     * @param id The certificate ID
     * @param certificateResponse The updated certificate data
     * @return Updated certificate response with 200 status, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Response<CertificateResponse>> updateCertificate(
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody CertificateResponse certificateResponse) {
        log.info("Updating certificate with ID: {}", id);
        
        certificateResponse.setId(id);
        var certificate = mapToEntity(certificateResponse);
        var updatedCertificate = certificateService.updateCertificate(certificate);
        var response = mapToDTO(updatedCertificate);
        var unifiedResponse = Response.success(
                "Certificate updated successfully",
                response
        );
        
        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Delete certificate by ID.
     * 
     * @param id The certificate ID
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Response<Void>> deleteCertificate(@PathVariable @NotNull UUID id) {
        log.info("Deleting certificate with ID: {}", id);
        
        if (certificateService.findById(id).isEmpty()) {
            throw new ApplicationObjectNotFoundException("Certificate with ID " + id + " not found");
        }
        
        certificateService.deleteCertificate(id);
        var unifiedResponse = Response.<Void>success(
                "Certificate deleted successfully",
                null
        );
        
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(unifiedResponse);
    }

    /**
     * Revoke a certificate.
     * Sets status to REVOKED.
     *
     * @param id The certificate ID
     * @return Revoked certificate response with 200 status, or 404 if not found
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Response<CertificateResponse>> revokeCertificate(@PathVariable @NotNull UUID id) {
        log.info("Revoking certificate with ID: {}", id);

        var revokedCertificate = certificateService.revokeCertificate(id);
        var response = mapToDTO(revokedCertificate);
        var unifiedResponse = Response.success(
                "Certificate revoked successfully",
                response
        );

        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Issue a preview certificate.
     * Promotes a PENDING certificate to ISSUED status and reuses the existing preview PDF.
     *
     * @param id The certificate ID
     * @return Issued certificate response with 200 status, or 404 if not found
     */
    @PostMapping("/{id}/issue")
    public ResponseEntity<Response<CertificateResponse>> issueCertificate(@PathVariable @NotNull UUID id) {
        log.info("Issuing preview certificate with ID: {}", id);

        var issuedCertificate = certificateService.issueCertificate(id);
        var response = mapToDTO(issuedCertificate);
        var unifiedResponse = Response.success(
                "Certificate issued successfully",
                response
        );

        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Get signed download URL for certificate PDF.
     * 
     * @param id The certificate ID
     * @param expirationMinutes Optional expiration time in minutes (default: 60)
     * @return Download URL response with 200 status, or 404 if not found
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<Response<Map<String, String>>> getDownloadUrl(
            @PathVariable @NotNull UUID id,
            @RequestParam(required = false, defaultValue = "60") Integer expirationMinutes) {
        log.debug("Getting download URL for certificate ID: {} with expiration: {} minutes", id, expirationMinutes);
        
        if (certificateService.findById(id).isEmpty()) {
            throw new ApplicationObjectNotFoundException("Certificate with ID " + id + " not found");
        }
        
        var downloadUrl = certificateService.getCertificateDownloadUrl(id, expirationMinutes);
        var response = Map.of("downloadUrl", downloadUrl);
        var unifiedResponse = Response.success(
                "Download URL generated successfully",
                response
        );
        
        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Public verification endpoint for certificate hash.
     * This endpoint doesn't require authentication and can be used for public verification.
     *
     * @param hash The certificate hash to verify
     * @return Certificate response with 200 status if valid, or 404 if not found/invalid
     */
    @Operation(
            summary = "Verify certificate by hash",
            description = "Public endpoint for verifying certificate authenticity using the certificate hash. " +
                    "No authentication required.",
            security = @SecurityRequirement(name = "")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Certificate verified successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Certificate not found or invalid hash",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)
                    )
            )
    })
    @GetMapping("/verify/{hash}")
    public ResponseEntity<Response<CertificateResponse>> verifyCertificate(@PathVariable @NotNull String hash) {
        log.debug("Verifying certificate with hash: {}", hash);
        
        var certificate = certificateService.verifyCertificateByHash(hash)
                .orElseThrow(() -> new ApplicationObjectNotFoundException("Certificate with hash " + hash + " not found or invalid"));
        
        var response = mapToDTO(certificate);
        var unifiedResponse = Response.success(
                "Certificate verified successfully",
                response
        );
        
        return ResponseEntity.ok(unifiedResponse);
    }

    /**
     * Get QR code image for certificate verification.
     * Returns a PNG image containing QR code with verification URL.
     * 
     * @param id The certificate ID
     * @return QR code PNG image
     */
    @GetMapping("/{id}/qr-code")
    public ResponseEntity<byte[]> getQrCodeImage(@PathVariable @NotNull UUID id) {
        log.debug("Generating QR code for certificate: {}", id);
        
        var certificate = certificateService.findById(id)
                .orElseThrow(() -> new ApplicationObjectNotFoundException("Certificate with ID " + id + " not found"));
        
        if (certificate.getStatus() != Certificate.CertificateStatus.ISSUED) {
            throw new ApplicationObjectNotFoundException("Certificate is not issued yet");
        }
        
        String verificationUrl = certificateService.getQrCodeVerificationUrl(id);
        var qrCodeImage = qrCodeService.generateQrCode(verificationUrl);
        
        try {
            var baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(qrCodeImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                    .contentLength(imageBytes.length)
                    .body(imageBytes);
        } catch (java.io.IOException e) {
            log.error("Failed to convert QR code to bytes for certificate {}", id, e);
            throw new RuntimeException("Failed to generate QR code image", e);
        }
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

        String issuedByName = null;
        if (certificate.getIssuedByUser() != null) {
            var firstName = certificate.getIssuedByUser().getFirstName() != null ?
                certificate.getIssuedByUser().getFirstName() : "";
            var lastName = certificate.getIssuedByUser().getLastName() != null ?
                certificate.getIssuedByUser().getLastName() : "";
            issuedByName = (firstName + " " + lastName).trim();
        }

        var response = CertificateResponse.builder()
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
                .issuedByName(issuedByName)
                .createdAt(certificate.getCreatedAt())
                .updatedAt(certificate.getUpdatedAt())
                .build();

        // Add QR code URL if certificate is issued and has a hash
        if (certificate.getStatus() == Certificate.CertificateStatus.ISSUED && 
            certificate.getSignedHash() != null && !certificate.getSignedHash().isEmpty()) {
            try {
                String qrCodeUrl = certificateService.getQrCodeVerificationUrl(certificate.getId());
                response.setQrCodeUrl(qrCodeUrl);
            } catch (Exception e) {
                log.warn("Failed to generate QR code URL for certificate {}: {}", 
                        certificate.getId(), e.getMessage());
            }
        }

        return response;
    }
}
