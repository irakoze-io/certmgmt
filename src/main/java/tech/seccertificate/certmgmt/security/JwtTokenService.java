package tech.seccertificate.certmgmt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for generating and validating JWT tokens.
 *
 * <p>This service provides a simple JWT-based authentication mechanism
 * suitable for prototypes and internal APIs. Tokens are signed using
 * HMAC-SHA256 with a secret key.
 *
 * <p>Token claims include:
 * <ul>
 *   <li>sub: User email</li>
 *   <li>userId: User UUID</li>
 *   <li>customerId: Customer ID</li>
 *   <li>tenantSchema: Tenant schema name</li>
 *   <li>role: User role (ADMIN, EDITOR, VIEWER, API_CLIENT)</li>
 *   <li>authorities: Spring Security authorities (ROLE_* format)</li>
 * </ul>
 */
@Slf4j
@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long tokenValidityInSeconds;

    public JwtTokenService(
            @Value("${jwt.secret:eQbbr3+SBvrUDiYRCwjX5e1WC+Zowfmt2CHZCdTgpi0=}") String secret,
            @Value("${jwt.expiration-seconds:3600}") long tokenValidityInSeconds) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenValidityInSeconds = tokenValidityInSeconds;
        log.info("JwtTokenService initialized with token validity: {} seconds", tokenValidityInSeconds);
    }

    /**
     * Generates a JWT token for the given user.
     *
     * @param user The user entity
     * @param tenantSchema The tenant schema name
     * @return JWT token string
     */
    public String generateToken(User user, String tenantSchema) {
        var now = Instant.now();
        var expiration = now.plus(tokenValidityInSeconds, ChronoUnit.SECONDS);

        Map<String, Object> claims = getStringObjectMap(user, tenantSchema);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    @NotNull
    private static Map<String, Object> getStringObjectMap(User user, String tenantSchema) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("customerId", user.getCustomerId());
        claims.put("tenant", tenantSchema);
        claims.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
        claims.put("lastName", user.getLastName() != null ? user.getLastName() : "");
        claims.put("authorities", "ROLE_" + user.getRole().name());

        return claims;
    }

    /**
     * Extracts the username (email) from the token.
     *
     * @param token JWT token
     * @return Username (email)
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from the token.
     *
     * @param token JWT token
     * @param claimsResolver Function to extract the claim
     * @return Claim value
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extracts all claims from the token.
     *
     * @param token JWT token
     * @return Claims object
     */
    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates if the token is valid (not expired and signature is valid).
     *
     * @param token JWT token
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the expiration date from the token.
     *
     * @param token JWT token
     * @return Expiration date
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * Checks if the token is expired.
     *
     * @param token JWT token
     * @return true if token is expired
     */
    public Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
}
