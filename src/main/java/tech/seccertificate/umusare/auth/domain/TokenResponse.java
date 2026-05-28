package tech.seccertificate.umusare.auth.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Response containing the generated access token and metadata.
 */
@Data
@Builder
public class TokenResponse {
    
    /**
     * The JWT access token.
     */
    private String accessToken;
    
    /**
     * Token type, always "Bearer".
     */
    @Builder.Default
    private String tokenType = "Bearer";
    
    /**
     * Token expiration time in seconds from issuance.
     */
    private long expiresIn;
    
    /**
     * The unique token identifier (jti) for revocation tracking.
     */
    private String tokenId;
}
