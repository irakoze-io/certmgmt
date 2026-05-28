package tech.seccertificate.umusare.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.umusare.auth.config.TokenConfig;
import tech.seccertificate.umusare.auth.domain.Permission;
import tech.seccertificate.umusare.auth.domain.Role;
import tech.seccertificate.umusare.auth.domain.TokenClaims;
import tech.seccertificate.umusare.auth.domain.TokenResponse;
import tech.seccertificate.umusare.auth.entity.Coordinator;
import tech.seccertificate.umusare.auth.entity.Driver;
import tech.seccertificate.umusare.auth.entity.Passenger;
import tech.seccertificate.umusare.auth.entity.UmusarePermission;
import tech.seccertificate.umusare.auth.entity.UmusareRole;
import tech.seccertificate.umusare.auth.entity.UmusareUser;
import tech.seccertificate.umusare.auth.exception.AuthenticationException;
import tech.seccertificate.umusare.auth.exception.AuthenticationException.AuthErrorCode;
import tech.seccertificate.umusare.auth.exception.ErrorCode;
import tech.seccertificate.umusare.auth.exception.TokenException;
import tech.seccertificate.umusare.auth.repository.CoordinatorRepository;
import tech.seccertificate.umusare.auth.repository.DriverRepository;
import tech.seccertificate.umusare.auth.repository.PassengerRepository;
import tech.seccertificate.umusare.auth.repository.UmusareUserRepository;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of AuthenticationService.
 * 
 * <p>Orchestrates the full authentication flow per §9.4:
 * <ul>
 *   <li>Password verification using bcrypt</li>
 *   <li>Role and permission resolution from DB join (INV-I14)</li>
 *   <li>Primary role determination by precedence (C7)</li>
 *   <li>Entity ID resolution via profile queries</li>
 *   <li>JWT token generation with RS256 signing</li>
 *   <li>Token registration for revocation tracking</li>
 * </ul>
 * 
 * <p>Security: bcrypt failures and other sensitive errors are caught
 * and mapped to SERVER_001, never propagated with internal details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {
    
    private final UmusareUserRepository userRepository;
    private final PassengerRepository passengerRepository;
    private final DriverRepository driverRepository;
    private final CoordinatorRepository coordinatorRepository;
    private final TokenService tokenService;
    private final TokenRevocationService revocationService;
    private final TokenConfig tokenConfig;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    @Transactional(readOnly = true)
    public TokenResponse authenticate(String email, String password, String deviceId) {
        log.debug("Authenticating user by email: {}", email);
        
        UmusareUser user = userRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode.USER_NOT_FOUND));
        
        return authenticateUser(user, password, deviceId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public TokenResponse authenticateByPhone(String phoneNumber, String password, String deviceId) {
        log.debug("Authenticating user by phone: {}", phoneNumber);
        
        UmusareUser user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode.USER_NOT_FOUND));
        
        user = userRepository.findByIdWithRolesAndPermissions(user.getId())
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode.USER_NOT_FOUND));
        
        return authenticateUser(user, password, deviceId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public TokenResponse refreshToken(UUID userId, String deviceId) {
        log.debug("Refreshing token for user: {}", userId);
        
        UmusareUser user = userRepository.findByIdWithRolesAndPermissions(userId)
                .orElseThrow(() -> new TokenException(ErrorCode.SERVER_001, "User not found"));
        
        validateUserStatus(user);
        
        TokenClaims claims = buildTokenClaims(user, deviceId);
        TokenResponse response = tokenService.generateToken(claims);
        
        revocationService.registerToken(
                response.getTokenId(),
                userId,
                deviceId,
                tokenConfig.getExpirationSeconds()
        );
        
        log.info("Token refreshed for user: {}", userId);
        return response;
    }
    
    @Override
    public void logout(UUID userId, String deviceId) {
        log.info("Logging out user: {} from device: {}", userId, deviceId);
        revocationService.revokeAllTokensForDevice(userId, deviceId);
    }
    
    @Override
    public void logoutAllDevices(UUID userId) {
        log.info("Logging out user: {} from all devices", userId);
        revocationService.revokeAllTokensForUser(userId);
    }
    
    @Override
    public boolean validateToken(String token) {
        if (!tokenService.validateToken(token)) {
            return false;
        }
        
        try {
            String tokenId = tokenService.extractTokenId(token);
            return !revocationService.isRevoked(tokenId);
        } catch (TokenException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    private TokenResponse authenticateUser(UmusareUser user, String password, String deviceId) {
        validateUserStatus(user);
        verifyPassword(user, password);
        
        TokenClaims claims = buildTokenClaims(user, deviceId);
        TokenResponse response = tokenService.generateToken(claims);
        
        revocationService.registerToken(
                response.getTokenId(),
                user.getId(),
                deviceId,
                tokenConfig.getExpirationSeconds()
        );
        
        log.info("User authenticated: {}, role: {}", user.getId(), claims.getRole());
        return response;
    }
    
    private void validateUserStatus(UmusareUser user) {
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new AuthenticationException(AuthErrorCode.ACCOUNT_DISABLED);
        }
    }
    
    private void verifyPassword(UmusareUser user, String password) {
        try {
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new AuthenticationException(AuthErrorCode.INVALID_CREDENTIALS);
            }
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Password verification failed for user: {}", user.getId(), e);
            throw new TokenException(ErrorCode.SERVER_001, "Authentication failed", e);
        }
    }
    
    /**
     * Builds TokenClaims with full claim resolution per §9.4.
     */
    private TokenClaims buildTokenClaims(UmusareUser user, String deviceId) {
        Set<Role> userRoles = user.getRoles().stream()
                .map(UmusareRole::getName)
                .collect(Collectors.toSet());
        
        Role primaryRole = Role.getPrimaryRole(userRoles);
        
        Set<Permission> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(UmusarePermission::getName)
                .collect(Collectors.toSet());
        
        UUID entityId = resolveEntityId(user.getId(), primaryRole);
        UUID venueId = resolveVenueId(user.getId(), userRoles);
        
        return TokenClaims.builder()
                .subject(user.getId())
                .role(primaryRole)
                .permissions(permissions)
                .entityId(entityId)
                .venueId(venueId)
                .locale(user.getLocale() != null ? user.getLocale() : "en")
                .deviceId(deviceId)
                .build();
    }
    
    /**
     * Resolves entityId via PassengerQuery/DriverQuery/CoordinatorQuery.
     * Returns null until profile exists per §9.4.
     */
    private UUID resolveEntityId(UUID userId, Role primaryRole) {
        if (primaryRole == null) {
            return null;
        }
        
        return switch (primaryRole) {
            case PASSENGER -> passengerRepository.findByUserId(userId)
                    .map(Passenger::getId)
                    .orElse(null);
            case DRIVER -> driverRepository.findByUserId(userId)
                    .map(Driver::getId)
                    .orElse(null);
            case COORDINATOR -> coordinatorRepository.findByUserId(userId)
                    .map(Coordinator::getId)
                    .orElse(null);
            case ADMIN -> null;
        };
    }
    
    /**
     * Resolves venueId from coordinators.venue_id when role includes COORDINATOR.
     */
    private UUID resolveVenueId(UUID userId, Set<Role> roles) {
        if (!roles.contains(Role.COORDINATOR)) {
            return null;
        }
        
        return coordinatorRepository.findByUserId(userId)
                .map(Coordinator::getVenueId)
                .orElse(null);
    }
}
