package tech.seccertificate.certmgmt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.dto.message.CertificateGenerationMessage;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageQueueServiceImpl implements MessageQueueService {

    private static final String CERTIFICATE_EXCHANGE = "certificate.exchange";
    private static final String ROUTING_KEY_GENERATE = "certificate.generate";

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void sendCertificateGenerationMessage(UUID certificateId, String tenantSchema, boolean isPreview) {
        var message = new CertificateGenerationMessage(certificateId, tenantSchema, isPreview);

        try {
            rabbitTemplate.convertAndSend(CERTIFICATE_EXCHANGE, ROUTING_KEY_GENERATE, message);
            log.info("Certificate generation message sent to queue: certificateId={}, tenantSchema={}, isPreview={}",
                    certificateId, tenantSchema, isPreview);
        } catch (Exception e) {
            log.error("Failed to send certificate generation message to queue: certificateId={}, tenantSchema={}, isPreview={}",
                    certificateId, tenantSchema, isPreview, e);
            throw new RuntimeException("Failed to queue certificate for async processing", e);
        }
    }
}
