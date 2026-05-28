package tech.seccertificate.umusare.auth.service;

import tech.seccertificate.umusare.auth.domain.TokenClaims;
import tech.seccertificate.umusare.auth.domain.TokenResponse;

import java.util.Map;
import java.util.UUID;

/**
 * Service for generating and validating RS256-signed JWT access tokens.
 * 
 * <p>This service handles:
 * <ul>
 *   <li>JWT generation with RS256 (RSA-SHA256) signatures per §9.4</li>
 *   <li>Full claim construction including role, permissions, entityId, etc.</li>
 *   <li>Token validation and claim extraction</li>
 *   <li>Token ID (jti) generation for revocation tracking</li>
 * </ul>
 * 
 * <p>Security: All signing errors are caught internally and mapped to SERVER_001.
 * Internal details are never propagated to clients.
 * 
 * @see tech.seccertificate.umusare.auth.domain.TokenClaims
 * @see tech.seccertificate.umusare.auth.domain.TokenResponse
 */
public interface TokenService {
    
    /**
     * Generates a new JWT access token with the specified claims.
     * 
     * <p>The generated token includes:
     * <ul>
     *   <li>{@code sub} — subject (user ID)</li>
     *   <li>{@code role} — primary role (singular string)</li>
     *   <li>{@code permissions} — array of permission strings</li>
     *   <li>{@code entityId} — passenger/driver/coordinator profile ID (nullable)</li>
     *   <li>{@code venueId} — coordinator's venue ID (nullable)</li>
     *   <li>{@code locale} — user's locale preference</li>
     *   <li>{@code deviceId} — device identifier for revocation checks</li>
     *   <li>{@code jti} — unique token ID for revocation tracking</li>
     *   <li>{@code exp} — expiration time (now + 900s per INV-I11)</li>
     *   <li>{@code iat} — issued at timestamp</li>
     *   <li>{@code iss} — issuer</li>
     *   <li>{@code aud} — audience</li>
     * </ul>
     *
     * @param claims the claims to encode in the token
     * @return token response containing the access token and metadata
     * @throws tech.seccertificate.umusare.auth.exception.TokenException if signing fails (mapped to SERVER_001)
     */
    TokenResponse generateToken(TokenClaims claims);
    
    /**
     * Validates a JWT token and returns whether it is valid.
     * 
     * <p>Validation checks:
     * <ul>
     *   <li>Signature verification using the public key</li>
     *   <li>Expiration time check</li>
     *   <li>Issuer and audience verification</li>
     *   <li>Required claims presence</li>
     * </ul>
     *
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    boolean validateToken(String token);
    
    /**
     * Extracts all claims from a validated JWT token.
     *
     * @param token the JWT token to parse
     * @return map of claim names to values
     * @throws tech.seccertificate.umusare.auth.exception.TokenException if parsing fails
     */
    Map<String, Object> extractClaims(String token);
    
    /**
     * Extracts the subject (user ID) from a JWT token.
     *
     * @param token the JWT token
     * @return the user ID from the subject claim
     * @throws tech.seccertificate.umusare.auth.exception.TokenException if extraction fails
     */
    UUID extractSubject(String token);
    
    /**
     * Extracts the token ID (jti) from a JWT token for revocation checking.
     *
     * @param token the JWT token
     * @return the unique token identifier
     * @throws tech.seccertificate.umusare.auth.exception.TokenException if extraction fails
     */
    String extractTokenId(String token);
    
    /**
     * Extracts the device ID from a JWT token for revocation checking.
     *
     * @param token the JWT token
     * @return the device identifier, or null if not present
     * @throws tech.seccertificate.umusare.auth.exception.TokenException if extraction fails
     */
    String extractDeviceId(String token);
}
