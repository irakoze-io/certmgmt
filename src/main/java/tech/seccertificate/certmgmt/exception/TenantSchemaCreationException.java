package tech.seccertificate.certmgmt.exception;

/**
 * Exception thrown when tenant schema creation fails.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
public class TenantSchemaCreationException extends RuntimeException {

    public TenantSchemaCreationException(String message) {
        super(message);
    }

    public TenantSchemaCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
