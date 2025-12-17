package tech.seccertificate.certmgmt.exception;

import tech.seccertificate.certmgmt.entity.Certificate;

import java.util.UUID;

/**
 * Exception thrown when a certificate cannot be found.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
public class CertificateNotFoundException extends ApplicationObjectNotFoundException {
    public CertificateNotFoundException(Certificate certificate) {
        super(certificate);
    }

    public CertificateNotFoundException(UUID certId) {
        super(Certificate.builder().id(certId).build());
    }

    public CertificateNotFoundException(String message) {
        super(message);
    }
}