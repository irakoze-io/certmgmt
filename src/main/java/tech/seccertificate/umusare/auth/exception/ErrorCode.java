package tech.seccertificate.umusare.auth.exception;

/**
 * Error codes for the Umusare authentication module.
 * 
 * <p>Per security requirements, JWT signing errors, bcrypt failures,
 * and other sensitive operations are mapped to SERVER_001 to avoid
 * information leakage.
 */
public enum ErrorCode {
    
    /**
     * Generic server error for security-sensitive operations.
     * Used for JWT signing errors, bcrypt failures, key loading errors.
     * Internal details are never exposed to clients.
     */
    SERVER_001("SERVER_001", "Internal Server Error"),
    
    /**
     * Token has expired.
     */
    TOKEN_EXPIRED("AUTH_001", "Token has expired"),
    
    /**
     * Token signature is invalid.
     */
    INVALID_SIGNATURE("AUTH_002", "Invalid token signature"),
    
    /**
     * Token format is malformed.
     */
    MALFORMED_TOKEN("AUTH_003", "Malformed token"),
    
    /**
     * Token has been revoked.
     */
    TOKEN_REVOKED("AUTH_004", "Token has been revoked"),
    
    /**
     * Required claim is missing from token.
     */
    MISSING_CLAIM("AUTH_005", "Missing required claim");
    
    private final String code;
    private final String description;
    
    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return code + ": " + description;
    }
}
