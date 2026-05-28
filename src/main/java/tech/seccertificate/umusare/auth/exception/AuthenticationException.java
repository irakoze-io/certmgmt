package tech.seccertificate.umusare.auth.exception;

/**
 * Exception thrown when authentication fails.
 * 
 * <p>Used for invalid credentials, disabled accounts, or other
 * authentication-related failures. Internal details are never
 * exposed to clients per security requirements.
 */
public class AuthenticationException extends RuntimeException {
    
    private final AuthErrorCode errorCode;
    
    public AuthenticationException(AuthErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }
    
    public AuthenticationException(AuthErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public AuthenticationException(AuthErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public AuthErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Returns a sanitized message safe for client exposure.
     * Internal details are never exposed per security requirements.
     */
    public String getSanitizedMessage() {
        return errorCode.getClientMessage();
    }
    
    public enum AuthErrorCode {
        INVALID_CREDENTIALS("AUTH_010", "Invalid credentials", "Invalid email or password"),
        ACCOUNT_DISABLED("AUTH_011", "Account is disabled", "Your account has been disabled"),
        ACCOUNT_LOCKED("AUTH_012", "Account is locked", "Your account has been locked due to too many failed attempts"),
        EMAIL_NOT_VERIFIED("AUTH_013", "Email not verified", "Please verify your email address"),
        PHONE_NOT_VERIFIED("AUTH_014", "Phone not verified", "Please verify your phone number"),
        USER_NOT_FOUND("AUTH_015", "User not found", "Invalid email or password"),
        PASSWORD_EXPIRED("AUTH_016", "Password expired", "Please reset your password");
        
        private final String code;
        private final String description;
        private final String clientMessage;
        
        AuthErrorCode(String code, String description, String clientMessage) {
            this.code = code;
            this.description = description;
            this.clientMessage = clientMessage;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getClientMessage() {
            return clientMessage;
        }
    }
}
