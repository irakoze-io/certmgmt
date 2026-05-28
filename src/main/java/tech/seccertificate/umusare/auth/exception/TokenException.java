package tech.seccertificate.umusare.auth.exception;

/**
 * Exception thrown when token operations fail.
 * 
 * <p>Per security requirements, bcrypt failures and JWT signing errors
 * are caught in the service layer and mapped to SERVER_001 error code.
 * These errors are never propagated to clients with detailed information.
 * 
 * @see ErrorCode#SERVER_001
 */
public class TokenException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public TokenException(String message) {
        super(message);
        this.errorCode = ErrorCode.SERVER_001;
    }
    
    public TokenException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.SERVER_001;
    }
    
    public TokenException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public TokenException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Returns a sanitized message safe for client exposure.
     * Internal details are never exposed per security requirements.
     */
    public String getSanitizedMessage() {
        return "An internal server error occurred. Please try again later.";
    }
}
