package tech.seccertificate.umusare.auth.service;

import tech.seccertificate.umusare.auth.domain.TokenResponse;

import java.util.UUID;

/**
 * Service for user authentication and token generation.
 * 
 * <p>Orchestrates the full authentication flow including:
 * <ul>
 *   <li>Password verification (bcrypt)</li>
 *   <li>Role and permission resolution from database (INV-I14)</li>
 *   <li>Entity ID resolution via PassengerQuery/DriverQuery/CoordinatorQuery</li>
 *   <li>JWT token generation with RS256 signing</li>
 *   <li>Token registration for revocation tracking</li>
 * </ul>
 * 
 * <p>Security: All authentication errors (bcrypt failures, invalid credentials)
 * are handled internally and mapped to appropriate error codes.
 * Internal details are never exposed to clients per security requirements.
 */
public interface AuthenticationService {
    
    /**
     * Authenticates a user and generates an access token.
     *
     * @param email the user's email address
     * @param password the user's password
     * @param deviceId the device identifier from X-Device-Id header
     * @return token response with access token and metadata
     * @throws tech.seccertificate.umusare.auth.exception.AuthenticationException if authentication fails
     */
    TokenResponse authenticate(String email, String password, String deviceId);
    
    /**
     * Authenticates a user by phone number and generates an access token.
     *
     * @param phoneNumber the user's phone number
     * @param password the user's password
     * @param deviceId the device identifier from X-Device-Id header
     * @return token response with access token and metadata
     * @throws tech.seccertificate.umusare.auth.exception.AuthenticationException if authentication fails
     */
    TokenResponse authenticateByPhone(String phoneNumber, String password, String deviceId);
    
    /**
     * Refreshes an access token for a user.
     * Validates the current token and issues a new one with updated claims.
     *
     * @param userId the user ID
     * @param deviceId the device identifier
     * @return new token response
     * @throws tech.seccertificate.umusare.auth.exception.TokenException if refresh fails
     */
    TokenResponse refreshToken(UUID userId, String deviceId);
    
    /**
     * Logs out a user from a specific device.
     * Revokes all tokens for the device.
     *
     * @param userId the user ID
     * @param deviceId the device identifier
     */
    void logout(UUID userId, String deviceId);
    
    /**
     * Logs out a user from all devices.
     * Revokes all tokens for the user.
     *
     * @param userId the user ID
     */
    void logoutAllDevices(UUID userId);
    
    /**
     * Validates an access token and checks revocation status.
     *
     * @param token the access token
     * @return true if the token is valid and not revoked
     */
    boolean validateToken(String token);
}
