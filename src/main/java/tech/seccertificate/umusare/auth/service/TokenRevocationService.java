package tech.seccertificate.umusare.auth.service;

import java.util.UUID;

/**
 * Service for managing JWT token revocation using Redis.
 * 
 * <p>Tokens are tracked by their unique identifier (jti) per §9.4.
 * Revoked tokens are stored in Redis until their natural expiration
 * to prevent replay attacks.
 * 
 * <p>Supports revocation of:
 * <ul>
 *   <li>Individual tokens by jti</li>
 *   <li>All tokens for a specific device</li>
 *   <li>All tokens for a specific user</li>
 * </ul>
 */
public interface TokenRevocationService {
    
    /**
     * Checks if a token has been revoked.
     *
     * @param tokenId the token's unique identifier (jti)
     * @return true if the token has been revoked
     */
    boolean isRevoked(String tokenId);
    
    /**
     * Revokes a specific token.
     *
     * @param tokenId the token's unique identifier (jti)
     * @param expirationSeconds time until the token would naturally expire
     */
    void revokeToken(String tokenId, long expirationSeconds);
    
    /**
     * Revokes all tokens for a specific device.
     * Used when a user logs out from a specific device.
     *
     * @param userId the user ID
     * @param deviceId the device identifier
     */
    void revokeAllTokensForDevice(UUID userId, String deviceId);
    
    /**
     * Revokes all tokens for a user across all devices.
     * Used when a user changes their password or for security lockout.
     *
     * @param userId the user ID
     */
    void revokeAllTokensForUser(UUID userId);
    
    /**
     * Registers a token for potential future revocation.
     * Called when a new token is issued.
     *
     * @param tokenId the token's unique identifier (jti)
     * @param userId the user ID
     * @param deviceId the device identifier
     * @param expirationSeconds time until the token naturally expires
     */
    void registerToken(String tokenId, UUID userId, String deviceId, long expirationSeconds);
}
