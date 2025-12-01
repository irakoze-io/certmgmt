package tech.seccertificate.certmgmt.exception;

/**
 * Exception thrown when tenant-related operations fail.
 */
public class TenantException extends RuntimeException {

    public TenantException(String message) {
        super(message);
    }

    public TenantException(String message, Throwable cause) {
        super(message, cause);
    }
}
