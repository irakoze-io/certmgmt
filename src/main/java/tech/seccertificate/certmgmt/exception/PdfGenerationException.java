package tech.seccertificate.certmgmt.exception;

/**
 * Exception thrown when PDF generation fails.
 */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message) {
        super(message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
