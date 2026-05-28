package tech.seccertificate.umusare.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tech.seccertificate.umusare.auth.config.TokenConfig;
import tech.seccertificate.umusare.auth.domain.Permission;
import tech.seccertificate.umusare.auth.domain.Role;
import tech.seccertificate.umusare.auth.domain.TokenClaims;
import tech.seccertificate.umusare.auth.domain.TokenResponse;
import tech.seccertificate.umusare.auth.entity.*;
import tech.seccertificate.umusare.auth.exception.AuthenticationException;
import tech.seccertificate.umusare.auth.repository.CoordinatorRepository;
import tech.seccertificate.umusare.auth.repository.DriverRepository;
import tech.seccertificate.umusare.auth.repository.PassengerRepository;
import tech.seccertificate.umusare.auth.repository.UmusareUserRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService")
class AuthenticationServiceImplTest {
    
    @Mock
    private UmusareUserRepository userRepository;
    
    @Mock
    private PassengerRepository passengerRepository;
    
    @Mock
    private DriverRepository driverRepository;
    
    @Mock
    private CoordinatorRepository coordinatorRepository;
    
    @Mock
    private TokenService tokenService;
    
    @Mock
    private TokenRevocationService revocationService;
    
    @Mock
    private TokenConfig tokenConfig;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    private AuthenticationService authService;
    
    @BeforeEach
    void setUp() {
        authService = new AuthenticationServiceImpl(
                userRepository,
                passengerRepository,
                driverRepository,
                coordinatorRepository,
                tokenService,
                revocationService,
                tokenConfig,
                passwordEncoder
        );
    }
    
    @Nested
    @DisplayName("Authentication by Email")
    class AuthenticationByEmail {
        
        @Test
        @DisplayName("should authenticate valid user and return token")
        void shouldAuthenticateValidUser() {
            String email = "user@example.com";
            String password = "password123";
            String deviceId = "device-123";
            
            UmusareUser user = createTestUser(Role.PASSENGER);
            TokenResponse expectedResponse = createTokenResponse();
            
            when(userRepository.findByEmailWithRolesAndPermissions(email))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(password, user.getPasswordHash()))
                    .thenReturn(true);
            when(tokenService.generateToken(any(TokenClaims.class)))
                    .thenReturn(expectedResponse);
            when(tokenConfig.getExpirationSeconds()).thenReturn(900L);
            
            TokenResponse response = authService.authenticate(email, password, deviceId);
            
            assertNotNull(response);
            assertEquals(expectedResponse.getAccessToken(), response.getAccessToken());
            verify(revocationService).registerToken(anyString(), any(UUID.class), anyString(), anyLong());
        }
        
        @Test
        @DisplayName("should throw exception for invalid email")
        void shouldThrowForInvalidEmail() {
            String email = "nonexistent@example.com";
            
            when(userRepository.findByEmailWithRolesAndPermissions(email))
                    .thenReturn(Optional.empty());
            
            assertThrows(AuthenticationException.class, 
                    () -> authService.authenticate(email, "password", "device"));
        }
        
        @Test
        @DisplayName("should throw exception for invalid password")
        void shouldThrowForInvalidPassword() {
            String email = "user@example.com";
            UmusareUser user = createTestUser(Role.PASSENGER);
            
            when(userRepository.findByEmailWithRolesAndPermissions(email))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString()))
                    .thenReturn(false);
            
            assertThrows(AuthenticationException.class, 
                    () -> authService.authenticate(email, "wrong", "device"));
        }
        
        @Test
        @DisplayName("should throw exception for disabled account")
        void shouldThrowForDisabledAccount() {
            String email = "user@example.com";
            UmusareUser user = createTestUser(Role.PASSENGER);
            user.setActive(false);
            
            when(userRepository.findByEmailWithRolesAndPermissions(email))
                    .thenReturn(Optional.of(user));
            
            AuthenticationException exception = assertThrows(AuthenticationException.class, 
                    () -> authService.authenticate(email, "password", "device"));
            
            assertEquals(AuthenticationException.AuthErrorCode.ACCOUNT_DISABLED, 
                    exception.getErrorCode());
        }
    }
    
    @Nested
    @DisplayName("Entity ID Resolution")
    class EntityIdResolution {
        
        @Test
        @DisplayName("should resolve passenger entityId")
        void shouldResolvePassengerEntityId() {
            String email = "user@example.com";
            UUID passengerId = UUID.randomUUID();
            UmusareUser user = createTestUser(Role.PASSENGER);
            Passenger passenger = Passenger.builder()
                    .id(passengerId)
                    .userId(user.getId())
                    .build();
            
            when(userRepository.findByEmailWithRolesAndPermissions(email))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString()))
                    .thenReturn(true);
            when(passengerRepository.findByUserId(user.getId()))
                    .thenReturn(Optional.of(passenger));
            when(tokenService.generateToken(any(TokenClaims.class)))
                    .thenAnswer(inv -> {
                        TokenClaims claims = inv.getArgument(0);
                        assertEquals(passengerId, claims.getEntityId());
                        return createTokenResponse();
                    });
            when(tokenConfig.getExpirationSeconds()).thenReturn(900L);
            
            authService.authenticate(email, "password", "device");
            
            verify(passengerRepository).findByUserId(user.getId());
        }
        
        @Test
        @DisplayName("should resolve coordinator venueId")
        void shouldResolveCoordinatorVenueId() {
            String email = "coordinator@example.com";
            UUID venueId = UUID.randomUUID();
            UUID coordinatorId = UUID.randomUUID();
            UmusareUser user = createTestUser(Role.COORDINATOR);
            Coordinator coordinator = Coordinator.builder()
                    .id(coordinatorId)
                    .userId(user.getId())
                    .venueId(venueId)
                    .build();
            
            when(userRepository.findByEmailWithRolesAndPermissions(email))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString()))
                    .thenReturn(true);
            when(coordinatorRepository.findByUserId(user.getId()))
                    .thenReturn(Optional.of(coordinator));
            when(tokenService.generateToken(any(TokenClaims.class)))
                    .thenAnswer(inv -> {
                        TokenClaims claims = inv.getArgument(0);
                        assertEquals(coordinatorId, claims.getEntityId());
                        assertEquals(venueId, claims.getVenueId());
                        return createTokenResponse();
                    });
            when(tokenConfig.getExpirationSeconds()).thenReturn(900L);
            
            authService.authenticate(email, "password", "device");
            
            verify(coordinatorRepository, times(2)).findByUserId(user.getId());
        }
        
        @Test
        @DisplayName("should return null entityId when profile does not exist")
        void shouldReturnNullEntityIdWhenProfileNotExists() {
            String email = "user@example.com";
            UmusareUser user = createTestUser(Role.PASSENGER);
            
            when(userRepository.findByEmailWithRolesAndPermissions(email))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString()))
                    .thenReturn(true);
            when(passengerRepository.findByUserId(user.getId()))
                    .thenReturn(Optional.empty());
            when(tokenService.generateToken(any(TokenClaims.class)))
                    .thenAnswer(inv -> {
                        TokenClaims claims = inv.getArgument(0);
                        assertNull(claims.getEntityId());
                        return createTokenResponse();
                    });
            when(tokenConfig.getExpirationSeconds()).thenReturn(900L);
            
            authService.authenticate(email, "password", "device");
        }
    }
    
    @Nested
    @DisplayName("Logout")
    class Logout {
        
        @Test
        @DisplayName("should revoke tokens for device on logout")
        void shouldRevokeTokensForDevice() {
            UUID userId = UUID.randomUUID();
            String deviceId = "device-123";
            
            authService.logout(userId, deviceId);
            
            verify(revocationService).revokeAllTokensForDevice(userId, deviceId);
        }
        
        @Test
        @DisplayName("should revoke all tokens on logout from all devices")
        void shouldRevokeAllTokens() {
            UUID userId = UUID.randomUUID();
            
            authService.logoutAllDevices(userId);
            
            verify(revocationService).revokeAllTokensForUser(userId);
        }
    }
    
    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {
        
        @Test
        @DisplayName("should validate non-revoked token")
        void shouldValidateNonRevokedToken() {
            String token = "valid.token.here";
            String tokenId = UUID.randomUUID().toString();
            
            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.extractTokenId(token)).thenReturn(tokenId);
            when(revocationService.isRevoked(tokenId)).thenReturn(false);
            
            assertTrue(authService.validateToken(token));
        }
        
        @Test
        @DisplayName("should reject revoked token")
        void shouldRejectRevokedToken() {
            String token = "revoked.token.here";
            String tokenId = UUID.randomUUID().toString();
            
            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.extractTokenId(token)).thenReturn(tokenId);
            when(revocationService.isRevoked(tokenId)).thenReturn(true);
            
            assertFalse(authService.validateToken(token));
        }
        
        @Test
        @DisplayName("should reject invalid token signature")
        void shouldRejectInvalidSignature() {
            String token = "invalid.token.here";
            
            when(tokenService.validateToken(token)).thenReturn(false);
            
            assertFalse(authService.validateToken(token));
        }
    }
    
    private UmusareUser createTestUser(Role role) {
        UmusarePermission permission = UmusarePermission.builder()
                .id(UUID.randomUUID())
                .name(Permission.REQUEST_RIDE)
                .build();
        
        UmusareRole umusareRole = UmusareRole.builder()
                .id(UUID.randomUUID())
                .name(role)
                .permissions(Set.of(permission))
                .build();
        
        return UmusareUser.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .firstName("Test")
                .lastName("User")
                .locale("en")
                .active(true)
                .roles(Set.of(umusareRole))
                .build();
    }
    
    private TokenResponse createTokenResponse() {
        return TokenResponse.builder()
                .accessToken("eyJ...")
                .tokenType("Bearer")
                .expiresIn(900)
                .tokenId(UUID.randomUUID().toString())
                .build();
    }
}
