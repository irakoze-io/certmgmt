package tech.seccertificate.umusare.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenRevocationServiceImpl.
 */
@DisplayName("TokenRevocationService")
class TokenRevocationServiceImplTest {
    
    private TokenRevocationService revocationService;
    
    @BeforeEach
    void setUp() {
        revocationService = new TokenRevocationServiceImpl();
    }
    
    @Nested
    @DisplayName("Token Revocation")
    class TokenRevocation {
        
        @Test
        @DisplayName("should return false for non-revoked token")
        void shouldReturnFalseForNonRevokedToken() {
            String tokenId = UUID.randomUUID().toString();
            
            assertFalse(revocationService.isRevoked(tokenId));
        }
        
        @Test
        @DisplayName("should return true for revoked token")
        void shouldReturnTrueForRevokedToken() {
            String tokenId = UUID.randomUUID().toString();
            
            revocationService.revokeToken(tokenId, 900);
            
            assertTrue(revocationService.isRevoked(tokenId));
        }
        
        @Test
        @DisplayName("should handle null token ID gracefully")
        void shouldHandleNullTokenId() {
            assertFalse(revocationService.isRevoked(null));
            assertDoesNotThrow(() -> revocationService.revokeToken(null, 900));
        }
    }
    
    @Nested
    @DisplayName("Token Registration")
    class TokenRegistration {
        
        @Test
        @DisplayName("should register token for user and device")
        void shouldRegisterToken() {
            String tokenId = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();
            String deviceId = "device-123";
            
            assertDoesNotThrow(() -> 
                revocationService.registerToken(tokenId, userId, deviceId, 900));
        }
        
        @Test
        @DisplayName("should handle null device ID")
        void shouldHandleNullDeviceId() {
            String tokenId = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();
            
            assertDoesNotThrow(() -> 
                revocationService.registerToken(tokenId, userId, null, 900));
        }
    }
    
    @Nested
    @DisplayName("Device Revocation")
    class DeviceRevocation {
        
        @Test
        @DisplayName("should revoke all tokens for device")
        void shouldRevokeAllTokensForDevice() {
            UUID userId = UUID.randomUUID();
            String deviceId = "device-123";
            String tokenId1 = UUID.randomUUID().toString();
            String tokenId2 = UUID.randomUUID().toString();
            
            revocationService.registerToken(tokenId1, userId, deviceId, 900);
            revocationService.registerToken(tokenId2, userId, deviceId, 900);
            
            revocationService.revokeAllTokensForDevice(userId, deviceId);
            
            assertTrue(revocationService.isRevoked(tokenId1));
            assertTrue(revocationService.isRevoked(tokenId2));
        }
        
        @Test
        @DisplayName("should not revoke tokens from other devices")
        void shouldNotRevokeTokensFromOtherDevices() {
            UUID userId = UUID.randomUUID();
            String deviceId1 = "device-1";
            String deviceId2 = "device-2";
            String tokenId1 = UUID.randomUUID().toString();
            String tokenId2 = UUID.randomUUID().toString();
            
            revocationService.registerToken(tokenId1, userId, deviceId1, 900);
            revocationService.registerToken(tokenId2, userId, deviceId2, 900);
            
            revocationService.revokeAllTokensForDevice(userId, deviceId1);
            
            assertTrue(revocationService.isRevoked(tokenId1));
            assertFalse(revocationService.isRevoked(tokenId2));
        }
    }
    
    @Nested
    @DisplayName("User Revocation")
    class UserRevocation {
        
        @Test
        @DisplayName("should revoke all tokens for user")
        void shouldRevokeAllTokensForUser() {
            UUID userId = UUID.randomUUID();
            String tokenId1 = UUID.randomUUID().toString();
            String tokenId2 = UUID.randomUUID().toString();
            
            revocationService.registerToken(tokenId1, userId, "device-1", 900);
            revocationService.registerToken(tokenId2, userId, "device-2", 900);
            
            revocationService.revokeAllTokensForUser(userId);
            
            assertTrue(revocationService.isRevoked(tokenId1));
            assertTrue(revocationService.isRevoked(tokenId2));
        }
        
        @Test
        @DisplayName("should not revoke tokens from other users")
        void shouldNotRevokeTokensFromOtherUsers() {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            String tokenId1 = UUID.randomUUID().toString();
            String tokenId2 = UUID.randomUUID().toString();
            
            revocationService.registerToken(tokenId1, userId1, "device-1", 900);
            revocationService.registerToken(tokenId2, userId2, "device-1", 900);
            
            revocationService.revokeAllTokensForUser(userId1);
            
            assertTrue(revocationService.isRevoked(tokenId1));
            assertFalse(revocationService.isRevoked(tokenId2));
        }
    }
}
