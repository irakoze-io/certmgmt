package tech.seccertificate.certmgmt.exception;

/**
 * Exception thrown when a tenant cannot be found or resolved.
 */
public class TenantNotFoundException extends TenantException {

    public TenantNotFoundException(String message) {
        super(message);
    }

    public TenantNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
