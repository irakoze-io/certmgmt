package tech.seccertificate.certmgmt.service;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tech.seccertificate.certmgmt.dto.message.CertificateGenerationMessage;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.CertificateHash;
import tech.seccertificate.certmgmt.repository.CertificateHashRepository;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateGenerationWorker {

    private static final String CERTIFICATE_GENERATION_QUEUE = "certificate.generation.queue";

    private final CertificateService certificateService;
    private final PdfGenerationService pdfGenerationService;
    private final StorageService storageService;
    private final TenantService tenantService;
    private final TemplateService templateService;
    private final CertificateHashRepository certificateHashRepository;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = CERTIFICATE_GENERATION_QUEUE)
    public void processCertificateGeneration(
            CertificateGenerationMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        var certificateId = message.getCertificateId();
        var tenantSchema = message.getTenantSchema();
        var isPreview = message.isPreview();

        log.info("Processing certificate generation: certificateId={}, tenantSchema={}, isPreview={}",
                certificateId, tenantSchema, isPreview);
        // Here Debug #11: Setting tenant context for the current thread before the transaction starts
        tenantService.setTenantContext(tenantSchema);

        try {
            // Using TransactionTemplate - transaction starts AFTER the setTenantContext call above
            transactionTemplate.executeWithoutResult(_ -> {
                try {
                    processCertificate(certificateId, tenantSchema, isPreview, channel, deliveryTag);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            log.error("Transaction failed for certificate {}: {}", certificateId, e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
            handleFailure(certificateId, tenantSchema, e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), channel, deliveryTag);
        } finally {
            tenantService.clearTenantContext();
        }
    }

    private void processCertificate(
            java.util.UUID certificateId,
            String tenantSchema,
            boolean isPreview,
            Channel channel,
            long deliveryTag) throws Exception {

        try {
            var certificate = certificateService.findById(certificateId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Certificate not found: " + certificateId));

            if (certificate.getStatus() == Certificate.CertificateStatus.ISSUED) {
                log.warn("Certificate {} is already ISSUED, skipping", certificateId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (certificate.getStatus() == Certificate.CertificateStatus.FAILED) {
                log.info("Retrying failed certificate {}: resetting to PROCESSING", certificateId);
            } else if (certificate.getStatus() != Certificate.CertificateStatus.PENDING &&
                    certificate.getStatus() != Certificate.CertificateStatus.PROCESSING) {
                log.warn("Certificate {} is in invalid status for processing (current: {}), skipping",
                        certificateId, certificate.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (certificate.getStatus() != Certificate.CertificateStatus.PROCESSING) {
                certificateService.markAsProcessing(certificateId);
            }

            var templateVersion = templateService.findVersionById(certificate.getTemplateVersionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Template version not found: " + certificate.getTemplateVersionId()));

            // Optimized two-pass generation:
            // Pass 1: Render HTML once, generate PDF WITHOUT footer → calculate hash → save to DB
            // Pass 2: Append footer to cached HTML, generate final PDF → store this PDF
            // Optimization: Reuse HTML rendering to avoid expensive template processing twice

            // Step 1: Render HTML without footer (this is the expensive part - do it once)
            log.debug("Rendering HTML template for certificate: {}", certificateId);
            var htmlWithoutFooter = pdfGenerationService.renderHtml(templateVersion, certificate, false);

            // Step 2: Generate PDF from rendered HTML (for hash calculation)
            log.debug("Generating PDF without footer for hash calculation: {}", certificateId);
            var pdfWithoutFooter = pdfGenerationService.convertHtmlToPdf(htmlWithoutFooter, templateVersion);
            var hashValue = generateHashForPdf(pdfWithoutFooter);

            // Step 3: Save hash to database (first DB write)
            certificate.setSignedHash(hashValue);
            var updatedCertificate = certificateService.updateCertificate(certificate);
            var certificateHash = CertificateHash.builder()
                    .certificate(updatedCertificate)
                    .hashAlgorithm("SHA-256")
                    .hashValue(hashValue)
                    .build();
            certificateHashRepository.save(certificateHash);

            // Step 4: Append verification footer to cached HTML (fast string operation)
            log.debug("Appending verification footer to rendered HTML: {}", certificateId);
            var htmlWithFooter = pdfGenerationService.appendVerificationFooterToHtml(
                    htmlWithoutFooter, updatedCertificate);

            // Step 5: Generate final PDF from HTML with footer (reusing rendered content)
            log.debug("Generating final PDF with verification footer: {}", certificateId);
            var finalPdf = pdfGenerationService.convertHtmlToPdf(htmlWithFooter, templateVersion);

            // Step 6: Upload final PDF to storage
            var storagePath = buildStoragePath(tenantSchema, certificateId);
            var bucketName = storageService.getDefaultBucketName();

            storageService.ensureBucketExists(bucketName);
            storageService.uploadFile(
                    bucketName,
                    storagePath,
                    finalPdf.toByteArray(),
                    "application/pdf"
            );

            // Step 7: Update certificate with all final state (single DB write)
            certificate.setStoragePath(storagePath);
            if (isPreview) {
                certificate.setStatus(Certificate.CertificateStatus.PENDING);
                certificate.setPreviewGeneratedAt(LocalDateTime.now());
            } else {
                certificate.setStatus(Certificate.CertificateStatus.ISSUED);
                if (certificate.getIssuedBy() == null) {
                    certificate.setIssuedBy(null); // Will be set by service if needed
                }
                if (certificate.getIssuedAt() == null) {
                    certificate.setIssuedAt(LocalDateTime.now());
                }
            }
            certificateService.updateCertificate(certificate);

            log.info("Certificate {} generated successfully: certificateId={}",
                    isPreview ? "preview" : "", certificateId);

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Certificate generation failed for certificate {}: {}", certificateId, e.getMessage());
            // Re-throw to trigger transaction rollback, error handled in outer catch
            throw e;
        }
    }

    private String buildStoragePath(String tenantSchema, java.util.UUID certificateId) {
        var now = LocalDateTime.now();
        return String.format("%s/certificates/%d/%02d/%s.pdf",
                tenantSchema,
                now.getYear(),
                now.getMonthValue(),
                certificateId);
    }

    private String generateHashForPdf(ByteArrayOutputStream pdfBytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashBytes = digest.digest(pdfBytes.toByteArray());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void handleFailure(java.util.UUID certificateId, String tenantSchema, String errorMessage,
                               com.rabbitmq.client.Channel channel, long deliveryTag) {
        try {
            // Tenant context should already be set, but ensure it's set for safety
            if (!tenantSchema.equals(tenantService.getCurrentTenantSchema())) {
                tenantService.setTenantContext(tenantSchema);
            }
            certificateService.markAsFailed(certificateId, errorMessage);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to mark certificate {} as failed: {}", certificateId, e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception nackException) {
                log.error("Failed to nack message for certificate {}", certificateId, nackException);
            }
        }
    }
}
