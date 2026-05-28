package tech.seccertificate.umusare.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.seccertificate.umusare.auth.config.TokenConfig;
import tech.seccertificate.umusare.auth.domain.Permission;
import tech.seccertificate.umusare.auth.domain.TokenClaims;
import tech.seccertificate.umusare.auth.domain.TokenResponse;
import tech.seccertificate.umusare.auth.exception.ErrorCode;
import tech.seccertificate.umusare.auth.exception.TokenException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RS256 JWT token service implementation for the Umusare platform.
 * 
 * <p>Implements JWT generation and validation using RSA-SHA256 signatures
 * per §9.4 specifications.
 * 
 * <p>JWT claim construction:
 * <ul>
 *   <li>{@code sub} — users.id</li>
 *   <li>{@code role} — singular string (primary role per governance §4.1 C7)</li>
 *   <li>{@code permissions} — resolved from DB join: user_roles → roles → permissions (INV-I14)</li>
 *   <li>{@code entityId} — resolved via PassengerQuery/DriverQuery/CoordinatorQuery</li>
 *   <li>{@code venueId} — from coordinators.venue_id when role includes COORDINATOR</li>
 *   <li>{@code locale} — from users preference column or default "en"</li>
 *   <li>{@code deviceId} — from X-Device-Id header (for revocation checks)</li>
 *   <li>{@code jti} — new UUID per access token (for Redis revocation tracking)</li>
 *   <li>{@code exp} — now + 900s (INV-I11)</li>
 *   <li>{@code iat}, {@code iss}, {@code aud} per Appendix B</li>
 * </ul>
 * 
 * <p>Security: bcrypt failures, JWT signing errors are caught and mapped to SERVER_001.
 * Internal details are never propagated to clients.
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {
    
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_ENTITY_ID = "entityId";
    private static final String CLAIM_VENUE_ID = "venueId";
    private static final String CLAIM_LOCALE = "locale";
    private static final String CLAIM_DEVICE_ID = "deviceId";
    
    private final TokenConfig tokenConfig;
    
    public TokenServiceImpl(TokenConfig tokenConfig) {
        this.tokenConfig = tokenConfig;
        log.info("TokenService initialized with RS256 signing, expiration: {}s, issuer: {}", 
                tokenConfig.getExpirationSeconds(), tokenConfig.getIssuer());
    }
    
    @Override
    public TokenResponse generateToken(TokenClaims claims) {
        try {
            var now = Instant.now();
            var expiration = now.plus(tokenConfig.getExpirationSeconds(), ChronoUnit.SECONDS);
            var tokenId = UUID.randomUUID().toString();
            
            Map<String, Object> jwtClaims = buildClaims(claims);
            
            String token = Jwts.builder()
                    .claims(jwtClaims)
                    .id(tokenId)
                    .subject(claims.getSubject().toString())
                    .issuer(tokenConfig.getIssuer())
                    .audience().add(tokenConfig.getAudience()).and()
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiration))
                    .signWith(tokenConfig.getPrivateKey(), Jwts.SIG.RS256)
                    .compact();
            
            log.debug("Generated token for subject: {}, role: {}, jti: {}", 
                    claims.getSubject(), claims.getRole(), tokenId);
            
            return TokenResponse.builder()
                    .accessToken(token)
                    .tokenType("Bearer")
                    .expiresIn(tokenConfig.getExpirationSeconds())
                    .tokenId(tokenId)
                    .build();
                    
        } catch (Exception e) {
            log.error("JWT signing error for subject: {}", claims.getSubject(), e);
            throw new TokenException(ErrorCode.SERVER_001, "Token generation failed", e);
        }
    }
    
    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        
        try {
            Claims claims = parseClaimsJws(token);
            
            // Verify issuer
            if (!tokenConfig.getIssuer().equals(claims.getIssuer())) {
                log.debug("Token validation failed: invalid issuer");
                return false;
            }
            
            // Verify audience
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(tokenConfig.getAudience())) {
                log.debug("Token validation failed: invalid audience");
                return false;
            }
            
            // Verify expiration
            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) {
                log.debug("Token validation failed: token expired");
                return false;
            }
            
            return true;
            
        } catch (ExpiredJwtException e) {
            log.debug("Token validation failed: expired - {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.debug("Token validation failed: invalid signature - {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.debug("Token validation failed: malformed - {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public Map<String, Object> extractClaims(String token) {
        try {
            Claims claims = parseClaimsJws(token);
            return new HashMap<>(claims);
        } catch (ExpiredJwtException e) {
            throw new TokenException(ErrorCode.TOKEN_EXPIRED, "Token has expired", e);
        } catch (SignatureException e) {
            throw new TokenException(ErrorCode.INVALID_SIGNATURE, "Invalid token signature", e);
        } catch (MalformedJwtException e) {
            throw new TokenException(ErrorCode.MALFORMED_TOKEN, "Malformed token", e);
        } catch (JwtException e) {
            log.error("Failed to extract claims from token", e);
            throw new TokenException(ErrorCode.SERVER_001, "Failed to extract claims", e);
        }
    }
    
    @Override
    public UUID extractSubject(String token) {
        try {
            Claims claims = parseClaimsJws(token);
            String subject = claims.getSubject();
            if (subject == null) {
                throw new TokenException(ErrorCode.MISSING_CLAIM, "Subject claim is missing");
            }
            return UUID.fromString(subject);
        } catch (TokenException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new TokenException(ErrorCode.MALFORMED_TOKEN, "Invalid subject format", e);
        } catch (JwtException e) {
            log.error("Failed to extract subject from token", e);
            throw new TokenException(ErrorCode.SERVER_001, "Failed to extract subject", e);
        }
    }
    
    @Override
    public String extractTokenId(String token) {
        try {
            Claims claims = parseClaimsJws(token);
            String jti = claims.getId();
            if (jti == null) {
                throw new TokenException(ErrorCode.MISSING_CLAIM, "Token ID (jti) claim is missing");
            }
            return jti;
        } catch (TokenException e) {
            throw e;
        } catch (JwtException e) {
            log.error("Failed to extract token ID from token", e);
            throw new TokenException(ErrorCode.SERVER_001, "Failed to extract token ID", e);
        }
    }
    
    @Override
    public String extractDeviceId(String token) {
        try {
            Claims claims = parseClaimsJws(token);
            return claims.get(CLAIM_DEVICE_ID, String.class);
        } catch (JwtException e) {
            log.error("Failed to extract device ID from token", e);
            throw new TokenException(ErrorCode.SERVER_001, "Failed to extract device ID", e);
        }
    }
    
    /**
     * Builds the custom claims map from TokenClaims.
     */
    private Map<String, Object> buildClaims(TokenClaims claims) {
        Map<String, Object> jwtClaims = new HashMap<>();
        
        // Role - singular string (primary role per governance §4.1 C7)
        if (claims.getRole() != null) {
            jwtClaims.put(CLAIM_ROLE, claims.getRole().name());
        }
        
        // Permissions - resolved from DB join: user_roles → roles → permissions (INV-I14)
        if (claims.getPermissions() != null && !claims.getPermissions().isEmpty()) {
            Set<String> permissionStrings = claims.getPermissions().stream()
                    .map(Permission::name)
                    .collect(Collectors.toSet());
            jwtClaims.put(CLAIM_PERMISSIONS, permissionStrings);
        }
        
        // EntityId - resolved via PassengerQuery/DriverQuery/CoordinatorQuery (null until profile exists)
        if (claims.getEntityId() != null) {
            jwtClaims.put(CLAIM_ENTITY_ID, claims.getEntityId().toString());
        }
        
        // VenueId - from coordinators.venue_id when role includes COORDINATOR
        if (claims.getVenueId() != null) {
            jwtClaims.put(CLAIM_VENUE_ID, claims.getVenueId().toString());
        }
        
        // Locale - from users preference column or default "en"
        jwtClaims.put(CLAIM_LOCALE, claims.getLocale() != null ? claims.getLocale() : "en");
        
        // DeviceId - from X-Device-Id header (embedded in JWT for revocation checks only)
        if (claims.getDeviceId() != null) {
            jwtClaims.put(CLAIM_DEVICE_ID, claims.getDeviceId());
        }
        
        return jwtClaims;
    }
    
    /**
     * Parses and verifies the JWT token using the public key.
     */
    private Claims parseClaimsJws(String token) {
        return Jwts.parser()
                .verifyWith(tokenConfig.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
