package tech.seccertificate.certmgmt.service;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.RabbitMQConfig;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.dto.message.CertificateGenerationMessage;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.CertificateHash;
import tech.seccertificate.certmgmt.exception.PdfGenerationException;
import tech.seccertificate.certmgmt.repository.CertificateHashRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateGenerationWorker {

    private static final String CERTIFICATE_GENERATION_QUEUE = "certificate.generation.queue";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String X_RETRY_COUNT_HEADER = "x-retry-count";
    private static final String X_DEATH_HEADER = "x-death";

    private final CertificateService certificateService;
    private final PdfGenerationService pdfGenerationService;
    private final StorageService storageService;
    private final TenantService tenantService;
    private final TemplateService templateService;
    private final CertificateHashRepository certificateHashRepository;

    @RabbitListener(queues = CERTIFICATE_GENERATION_QUEUE)
    @Transactional
    public void processCertificateGeneration(
            CertificateGenerationMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            Message amqpMessage) {
        
        processMessage(message, channel, deliveryTag, amqpMessage, false);
    }

    @RabbitListener(queues = RabbitMQConfig.CERTIFICATE_GENERATION_DLQ)
    @Transactional
    public void processDeadLetterMessage(
            CertificateGenerationMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            Message amqpMessage) {
        
        log.warn("Processing message from DLQ: certificateId={}, tenantSchema={}", 
                message.getCertificateId(), message.getTenantSchema());
        processMessage(message, channel, deliveryTag, amqpMessage, true);
    }

    private void processMessage(
            CertificateGenerationMessage message,
            Channel channel,
            long deliveryTag,
            Message amqpMessage,
            boolean isFromDLQ) {
        
        var certificateId = message.getCertificateId();
        var tenantSchema = message.getTenantSchema();
        var retryCount = getRetryCount(amqpMessage);
        
        if (isFromDLQ) {
            log.warn("Processing certificate generation from DLQ: certificateId={}, tenantSchema={}, retryCount={}", 
                    certificateId, tenantSchema, retryCount);
        } else {
            log.info("Processing certificate generation: certificateId={}, tenantSchema={}, retryCount={}", 
                    certificateId, tenantSchema, retryCount);
        }

        try {
            tenantService.setTenantContext(tenantSchema);
            
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
            
        } catch (PdfGenerationException e) {
            log.error("PDF generation failed for certificate {}: {}", certificateId, e.getMessage(), e);
            handleFailure(certificateId, tenantSchema, e, channel, deliveryTag, amqpMessage, 
                    isFromDLQ ? false : true);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for certificate {}: {}", certificateId, e.getMessage(), e);
            handleFailure(certificateId, tenantSchema, e, channel, deliveryTag, amqpMessage, false);
        } catch (Exception e) {
            log.error("Certificate generation failed for certificate {}: {}", certificateId, e.getMessage(), e);
            handleFailure(certificateId, tenantSchema, e, channel, deliveryTag, amqpMessage, 
                    isFromDLQ ? false : isTransientError(e));
        } finally {
            tenantService.clearTenantContext();
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

    private void handleFailure(java.util.UUID certificateId, String tenantSchema, Exception exception,
                               Channel channel, long deliveryTag, Message amqpMessage, boolean isTransient) {
        var retryCount = getRetryCount(amqpMessage);
        var errorMessage = exception.getMessage();
        
        log.warn("Handling failure for certificate {}: retryCount={}, isTransient={}, error={}", 
                certificateId, retryCount, isTransient, errorMessage);

        if (isTransient && retryCount < MAX_RETRY_ATTEMPTS) {
            log.info("Retrying certificate generation: certificateId={}, attempt={}/{}", 
                    certificateId, retryCount + 1, MAX_RETRY_ATTEMPTS);
            retryMessage(channel, deliveryTag, amqpMessage, retryCount);
        } else {
            log.error("Max retries exceeded or permanent error for certificate {}: marking as failed", certificateId);
            markCertificateAsFailed(certificateId, tenantSchema, errorMessage, channel, deliveryTag);
        }
    }

    private int getRetryCount(Message amqpMessage) {
        var headers = amqpMessage.getMessageProperties().getHeaders();
        
        if (headers != null && headers.containsKey(X_RETRY_COUNT_HEADER)) {
            var retryCount = headers.get(X_RETRY_COUNT_HEADER);
            if (retryCount instanceof Number) {
                return ((Number) retryCount).intValue();
            }
        }
        
        if (headers != null && headers.containsKey(X_DEATH_HEADER)) {
            @SuppressWarnings("unchecked")
            var deathList = (java.util.List<Map<String, Object>>) headers.get(X_DEATH_HEADER);
            if (deathList != null && !deathList.isEmpty()) {
                var count = deathList.get(0).get("count");
                if (count instanceof Number) {
                    return ((Number) count).intValue();
                }
            }
        }
        
        return 0;
    }

    private void retryMessage(Channel channel, long deliveryTag, Message amqpMessage, int currentRetryCount) {
        try {
            var headers = amqpMessage.getMessageProperties().getHeaders();
            if (headers != null) {
                headers.put(X_RETRY_COUNT_HEADER, currentRetryCount + 1);
            }
            
            channel.basicNack(deliveryTag, false, true);
            log.debug("Message nacked for retry: deliveryTag={}, retryCount={}", deliveryTag, currentRetryCount + 1);
        } catch (IOException e) {
            log.error("Failed to nack message for retry: deliveryTag={}", deliveryTag, e);
        }
    }

    private void markCertificateAsFailed(java.util.UUID certificateId, String tenantSchema, 
                                        String errorMessage, Channel channel, long deliveryTag) {
        try {
            tenantService.setTenantContext(tenantSchema);
            certificateService.markAsFailed(certificateId, errorMessage);
            channel.basicAck(deliveryTag, false);
            log.info("Certificate {} marked as failed and message acknowledged", certificateId);
        } catch (Exception e) {
            log.error("Failed to mark certificate {} as failed: {}", certificateId, e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException nackException) {
                log.error("Failed to nack message for certificate {}", certificateId, nackException);
            }
        }
    }

    private boolean isTransientError(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return false;
        }
        
        if (exception instanceof PdfGenerationException) {
            var cause = exception.getCause();
            if (cause instanceof java.io.IOException || cause instanceof java.net.SocketException) {
                return true;
            }
            return false;
        }
        
        if (exception instanceof java.io.IOException || 
            exception instanceof java.net.SocketException ||
            exception instanceof org.springframework.dao.DataAccessException) {
            return true;
        }
        
        return false;
    }
}
