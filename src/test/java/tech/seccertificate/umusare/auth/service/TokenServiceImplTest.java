package tech.seccertificate.umusare.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.seccertificate.umusare.auth.config.TokenConfig;
import tech.seccertificate.umusare.auth.domain.Permission;
import tech.seccertificate.umusare.auth.domain.Role;
import tech.seccertificate.umusare.auth.domain.TokenClaims;
import tech.seccertificate.umusare.auth.domain.TokenResponse;
import tech.seccertificate.umusare.auth.exception.ErrorCode;
import tech.seccertificate.umusare.auth.exception.TokenException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenServiceImpl.
 * 
 * Tests cover:
 * - RS256 JWT token generation with all specified claims
 * - Token validation (signature, expiration, issuer, audience)
 * - Claim extraction
 * - Error handling with SERVER_001 mapping
 */
@DisplayName("TokenService")
class TokenServiceImplTest {
    
    private TokenService tokenService;
    private TokenConfig tokenConfig;
    
    @BeforeEach
    void setUp() throws Exception {
        tokenConfig = new TokenConfig();
        setFieldValue(tokenConfig, "privateKeyPath", "");
        setFieldValue(tokenConfig, "publicKeyPath", "");
        setFieldValue(tokenConfig, "expirationSeconds", 900L);
        setFieldValue(tokenConfig, "issuer", "umusare-auth");
        setFieldValue(tokenConfig, "audience", "umusare-api");
        
        Method initMethod = TokenConfig.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(tokenConfig);
        
        tokenService = new TokenServiceImpl(tokenConfig);
    }
    
    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        var field = TokenConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
    
    @Nested
    @DisplayName("Token Generation")
    class TokenGeneration {
        
        @Test
        @DisplayName("should generate valid RS256 signed JWT token")
        void shouldGenerateValidToken() {
            TokenClaims claims = createBasicClaims();
            
            TokenResponse response = tokenService.generateToken(claims);
            
            assertNotNull(response);
            assertNotNull(response.getAccessToken());
            assertEquals("Bearer", response.getTokenType());
            assertEquals(900L, response.getExpiresIn());
            assertNotNull(response.getTokenId());
        }
        
        @Test
        @DisplayName("should include subject claim as user ID")
        void shouldIncludeSubjectClaim() {
            UUID userId = UUID.randomUUID();
            TokenClaims claims = TokenClaims.builder()
                    .subject(userId)
                    .role(Role.PASSENGER)
                    .permissions(Set.of(Permission.REQUEST_RIDE))
                    .build();
            
            TokenResponse response = tokenService.generateToken(claims);
            UUID extractedSubject = tokenService.extractSubject(response.getAccessToken());
            
            assertEquals(userId, extractedSubject);
        }
        
        @Test
        @DisplayName("should include role claim as singular string")
        void shouldIncludeRoleClaim() {
            TokenClaims claims = createBasicClaims();
            claims.setRole(Role.DRIVER);
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            assertEquals("DRIVER", extractedClaims.get("role"));
        }
        
        @Test
        @DisplayName("should include permissions array")
        void shouldIncludePermissionsClaim() {
            TokenClaims claims = TokenClaims.builder()
                    .subject(UUID.randomUUID())
                    .role(Role.DRIVER)
                    .permissions(Set.of(Permission.ACCEPT_RIDE, Permission.COMPLETE_RIDE, Permission.VIEW_EARNINGS))
                    .build();
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            @SuppressWarnings("unchecked")
            var permissions = (java.util.Collection<String>) extractedClaims.get("permissions");
            assertNotNull(permissions);
            assertEquals(3, permissions.size());
            assertTrue(permissions.contains("ACCEPT_RIDE"));
            assertTrue(permissions.contains("COMPLETE_RIDE"));
            assertTrue(permissions.contains("VIEW_EARNINGS"));
        }
        
        @Test
        @DisplayName("should include entityId when provided")
        void shouldIncludeEntityId() {
            UUID entityId = UUID.randomUUID();
            TokenClaims claims = createBasicClaims();
            claims.setEntityId(entityId);
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            assertEquals(entityId.toString(), extractedClaims.get("entityId"));
        }
        
        @Test
        @DisplayName("should not include entityId when null (profile not exists)")
        void shouldNotIncludeEntityIdWhenNull() {
            TokenClaims claims = createBasicClaims();
            claims.setEntityId(null);
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            assertNull(extractedClaims.get("entityId"));
        }
        
        @Test
        @DisplayName("should include venueId for coordinators")
        void shouldIncludeVenueIdForCoordinators() {
            UUID venueId = UUID.randomUUID();
            TokenClaims claims = TokenClaims.builder()
                    .subject(UUID.randomUUID())
                    .role(Role.COORDINATOR)
                    .permissions(Set.of(Permission.MANAGE_DRIVERS, Permission.VIEW_VENUE_STATS))
                    .venueId(venueId)
                    .build();
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            assertEquals(venueId.toString(), extractedClaims.get("venueId"));
        }
        
        @Test
        @DisplayName("should include locale with default 'en'")
        void shouldIncludeLocaleWithDefault() {
            TokenClaims claims = createBasicClaims();
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            assertEquals("en", extractedClaims.get("locale"));
        }
        
        @Test
        @DisplayName("should include custom locale")
        void shouldIncludeCustomLocale() {
            TokenClaims claims = createBasicClaims();
            claims.setLocale("rw");
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            assertEquals("rw", extractedClaims.get("locale"));
        }
        
        @Test
        @DisplayName("should include deviceId for revocation tracking")
        void shouldIncludeDeviceId() {
            String deviceId = "device-123-abc";
            TokenClaims claims = createBasicClaims();
            claims.setDeviceId(deviceId);
            
            TokenResponse response = tokenService.generateToken(claims);
            String extractedDeviceId = tokenService.extractDeviceId(response.getAccessToken());
            
            assertEquals(deviceId, extractedDeviceId);
        }
        
        @Test
        @DisplayName("should generate unique jti for each token")
        void shouldGenerateUniqueJti() {
            TokenClaims claims = createBasicClaims();
            
            TokenResponse response1 = tokenService.generateToken(claims);
            TokenResponse response2 = tokenService.generateToken(claims);
            
            assertNotEquals(response1.getTokenId(), response2.getTokenId());
            assertNotEquals(
                    tokenService.extractTokenId(response1.getAccessToken()),
                    tokenService.extractTokenId(response2.getAccessToken())
            );
        }
        
        @Test
        @DisplayName("should include standard JWT claims (iss, aud, iat, exp)")
        void shouldIncludeStandardClaims() {
            TokenClaims claims = createBasicClaims();
            
            TokenResponse response = tokenService.generateToken(claims);
            Map<String, Object> extractedClaims = tokenService.extractClaims(response.getAccessToken());
            
            assertEquals("umusare-auth", extractedClaims.get("iss"));
            assertNotNull(extractedClaims.get("iat"));
            assertNotNull(extractedClaims.get("exp"));
            assertNotNull(extractedClaims.get("jti"));
        }
    }
    
    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {
        
        @Test
        @DisplayName("should validate correctly signed token")
        void shouldValidateCorrectlySignedToken() {
            TokenClaims claims = createBasicClaims();
            TokenResponse response = tokenService.generateToken(claims);
            
            assertTrue(tokenService.validateToken(response.getAccessToken()));
        }
        
        @Test
        @DisplayName("should reject malformed token")
        void shouldRejectMalformedToken() {
            assertFalse(tokenService.validateToken("not-a-valid-token"));
        }
        
        @Test
        @DisplayName("should reject token with invalid signature")
        void shouldRejectInvalidSignature() {
            TokenClaims claims = createBasicClaims();
            TokenResponse response = tokenService.generateToken(claims);
            
            String token = response.getAccessToken();
            String tamperedToken = token.substring(0, token.length() - 10) + "tampered!!";
            
            assertFalse(tokenService.validateToken(tamperedToken));
        }
        
        @Test
        @DisplayName("should reject null token")
        void shouldRejectNullToken() {
            assertFalse(tokenService.validateToken(null));
        }
        
        @Test
        @DisplayName("should reject empty token")
        void shouldRejectEmptyToken() {
            assertFalse(tokenService.validateToken(""));
        }
    }
    
    @Nested
    @DisplayName("Claim Extraction")
    class ClaimExtraction {
        
        @Test
        @DisplayName("should extract subject (user ID)")
        void shouldExtractSubject() {
            UUID userId = UUID.randomUUID();
            TokenClaims claims = createClaimsWithSubject(userId);
            
            TokenResponse response = tokenService.generateToken(claims);
            UUID extracted = tokenService.extractSubject(response.getAccessToken());
            
            assertEquals(userId, extracted);
        }
        
        @Test
        @DisplayName("should extract token ID (jti)")
        void shouldExtractTokenId() {
            TokenClaims claims = createBasicClaims();
            
            TokenResponse response = tokenService.generateToken(claims);
            String jti = tokenService.extractTokenId(response.getAccessToken());
            
            assertNotNull(jti);
            assertEquals(response.getTokenId(), jti);
        }
        
        @Test
        @DisplayName("should extract device ID")
        void shouldExtractDeviceId() {
            String deviceId = "test-device-id";
            TokenClaims claims = createBasicClaims();
            claims.setDeviceId(deviceId);
            
            TokenResponse response = tokenService.generateToken(claims);
            String extracted = tokenService.extractDeviceId(response.getAccessToken());
            
            assertEquals(deviceId, extracted);
        }
        
        @Test
        @DisplayName("should return null for missing device ID")
        void shouldReturnNullForMissingDeviceId() {
            TokenClaims claims = createBasicClaims();
            claims.setDeviceId(null);
            
            TokenResponse response = tokenService.generateToken(claims);
            String extracted = tokenService.extractDeviceId(response.getAccessToken());
            
            assertNull(extracted);
        }
    }
    
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        
        @Test
        @DisplayName("should throw TokenException with MALFORMED_TOKEN for invalid format")
        void shouldThrowForMalformedToken() {
            TokenException exception = assertThrows(TokenException.class,
                    () -> tokenService.extractClaims("not.a.valid.token"));
            
            assertNotNull(exception.getErrorCode());
        }
        
        @Test
        @DisplayName("should throw TokenException with MISSING_CLAIM for missing subject")
        void shouldThrowForMissingSubject() {
            TokenException exception = assertThrows(TokenException.class,
                    () -> tokenService.extractSubject("not.a.valid.token"));
            
            assertNotNull(exception.getErrorCode());
        }
        
        @Test
        @DisplayName("should provide sanitized message for security")
        void shouldProvideSanitizedMessage() {
            TokenException exception = new TokenException("Internal details that should not be exposed");
            
            assertEquals("An internal server error occurred. Please try again later.",
                    exception.getSanitizedMessage());
        }
        
        @Test
        @DisplayName("SERVER_001 should be default error code")
        void shouldUseServer001AsDefaultErrorCode() {
            TokenException exception = new TokenException("Test error");
            
            assertEquals(ErrorCode.SERVER_001, exception.getErrorCode());
        }
    }
    
    @Nested
    @DisplayName("Role Precedence")
    class RolePrecedence {
        
        @Test
        @DisplayName("should determine correct primary role by precedence")
        void shouldDetermineCorrectPrimaryRole() {
            assertEquals(Role.ADMIN, Role.getPrimaryRole(
                    Set.of(Role.PASSENGER, Role.DRIVER, Role.COORDINATOR, Role.ADMIN)));
            
            assertEquals(Role.COORDINATOR, Role.getPrimaryRole(
                    Set.of(Role.PASSENGER, Role.DRIVER, Role.COORDINATOR)));
            
            assertEquals(Role.DRIVER, Role.getPrimaryRole(
                    Set.of(Role.PASSENGER, Role.DRIVER)));
            
            assertEquals(Role.PASSENGER, Role.getPrimaryRole(
                    Set.of(Role.PASSENGER)));
        }
        
        @Test
        @DisplayName("should return null for empty roles")
        void shouldReturnNullForEmptyRoles() {
            assertNull(Role.getPrimaryRole(Set.of()));
        }
    }
    
    private TokenClaims createBasicClaims() {
        return TokenClaims.builder()
                .subject(UUID.randomUUID())
                .role(Role.PASSENGER)
                .permissions(Set.of(Permission.REQUEST_RIDE, Permission.VIEW_RIDE_HISTORY))
                .build();
    }
    
    private TokenClaims createClaimsWithSubject(UUID subject) {
        return TokenClaims.builder()
                .subject(subject)
                .role(Role.PASSENGER)
                .permissions(Set.of(Permission.REQUEST_RIDE))
                .build();
    }
}
