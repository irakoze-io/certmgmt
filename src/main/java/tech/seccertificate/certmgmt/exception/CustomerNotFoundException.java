package tech.seccertificate.certmgmt.exception;

/**
 * Exception thrown when a customer cannot be found.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(String message) {
        super(message);
    }

    public CustomerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
