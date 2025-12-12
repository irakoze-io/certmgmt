package tech.seccertificate.certmgmt.service;

import java.util.UUID;

/**
 * Service interface for message queue operations (RabbitMQ).
 * Handles sending certificate generation messages for async processing.
 */
public interface MessageQueueService {

    /**
     * Send a certificate generation message to the queue.
     *
     * @param certificateId The certificate ID to process
     * @param tenantSchema The tenant schema name (for context)
     * @param isPreview Whether this is a preview generation
     */
    void sendCertificateGenerationMessage(UUID certificateId, String tenantSchema, boolean isPreview);
}
