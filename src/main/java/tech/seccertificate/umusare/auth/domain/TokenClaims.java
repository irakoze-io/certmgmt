package tech.seccertificate.umusare.auth.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

/**
 * Represents the claims to be encoded in a JWT access token.
 * 
 * <p>JWT claim construction per §9.4:
 * <ul>
 *   <li>{@code sub} — users.id</li>
 *   <li>{@code role} — singular string (primary role)</li>
 *   <li>{@code permissions} — resolved from DB join: user_roles → roles → permissions</li>
 *   <li>{@code entityId} — resolved via PassengerQuery/DriverQuery/CoordinatorQuery</li>
 *   <li>{@code venueId} — from coordinators.venue_id when role includes COORDINATOR</li>
 *   <li>{@code locale} — from users preference column or default "en"</li>
 *   <li>{@code deviceId} — from X-Device-Id header (for revocation checks)</li>
 *   <li>{@code jti} — new UUID per access token (for Redis revocation tracking)</li>
 *   <li>{@code exp} — now + 900s per INV-I11</li>
 *   <li>{@code iat}, {@code iss}, {@code aud} per Appendix B</li>
 * </ul>
 */
@Data
@Builder
public class TokenClaims {
    
    /**
     * Subject claim - the user's unique identifier (users.id).
     */
    private UUID subject;
    
    /**
     * Primary role - singular string determined by precedence.
     * PASSENGER < DRIVER < COORDINATOR < ADMIN per governance §4.1 (C7).
     */
    private Role role;
    
    /**
     * Resolved permissions from DB join: user_roles → roles → permissions (INV-I14).
     */
    private Set<Permission> permissions;
    
    /**
     * Entity ID resolved via PassengerQuery/DriverQuery/CoordinatorQuery.
     * Null until the profile exists.
     */
    private UUID entityId;
    
    /**
     * Venue ID from coordinators.venue_id when role includes COORDINATOR.
     * Null for non-coordinators.
     */
    private UUID venueId;
    
    /**
     * User's locale preference from users table, defaults to "en".
     */
    @Builder.Default
    private String locale = "en";
    
    /**
     * Device ID from X-Device-Id header, embedded for revocation checks only.
     */
    private String deviceId;
}
