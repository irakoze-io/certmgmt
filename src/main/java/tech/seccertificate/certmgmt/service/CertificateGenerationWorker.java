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

        log.info("Processing certificate generation: certificateId={}, tenantSchema={}",
                certificateId, tenantSchema);
        // Here Debug #11: Setting tenant context for the current thread before the transaction starts
        tenantService.setTenantContext(tenantSchema);

        try {
            // Using TransactionTemplate - transaction starts AFTER the setTenantContext call above
            transactionTemplate.executeWithoutResult(_ -> {
                try {
                    processCertificate(certificateId, tenantSchema, channel, deliveryTag);
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

            var pdfBytes = pdfGenerationService.generatePdf(templateVersion, certificate);

            var storagePath = buildStoragePath(tenantSchema, certificateId);
            var bucketName = storageService.getDefaultBucketName();

            storageService.ensureBucketExists(bucketName);
            storageService.uploadFile(
                    bucketName,
                    storagePath,
                    pdfBytes.toByteArray(),
                    "application/pdf"
            );

            var hashValue = generateHashForPdf(pdfBytes);

            certificate.setStoragePath(storagePath);
            certificate.setSignedHash(hashValue);
            var updatedCertificate = certificateService.updateCertificate(certificate);
            var certificateHash = CertificateHash.builder()
                    .certificate(updatedCertificate)
                    .hashAlgorithm("SHA-256")
                    .hashValue(hashValue)
                    .build();

            certificateHashRepository.save(certificateHash);

            certificateService.markAsIssued(certificateId, null);

            log.info("Certificate generation completed successfully: certificateId={}", certificateId);
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
