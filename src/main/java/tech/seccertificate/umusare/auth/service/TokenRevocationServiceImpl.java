package tech.seccertificate.umusare.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of TokenRevocationService.
 * 
 * <p>This implementation uses ConcurrentHashMap for token revocation tracking.
 * For production deployments, replace with a Redis-backed implementation.
 * 
 * <p>Token tracking structure:
 * <ul>
 *   <li>revoked tokens: jti → expiration timestamp</li>
 *   <li>user tokens: userId → Set of (jti, deviceId) pairs</li>
 *   <li>device tokens: userId:deviceId → Set of jti</li>
 * </ul>
 * 
 * <p>Note: This in-memory implementation is suitable for development and testing.
 * For production, implement a Redis-backed version using Spring Data Redis
 * with appropriate TTL settings matching token expiration.
 */
@Slf4j
@Service
public class TokenRevocationServiceImpl implements TokenRevocationService {
    
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();
    private final Map<UUID, Set<TokenInfo>> userTokens = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> deviceTokens = new ConcurrentHashMap<>();
    
    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        
        Long expirationTime = revokedTokens.get(tokenId);
        if (expirationTime == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > expirationTime) {
            revokedTokens.remove(tokenId);
            return false;
        }
        
        return true;
    }
    
    @Override
    public void revokeToken(String tokenId, long expirationSeconds) {
        if (tokenId == null) {
            return;
        }
        
        long expirationTime = System.currentTimeMillis() + (expirationSeconds * 1000);
        revokedTokens.put(tokenId, expirationTime);
        log.debug("Revoked token: {}, expires at: {}", tokenId, expirationTime);
    }
    
    @Override
    public void revokeAllTokensForDevice(UUID userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return;
        }
        
        String deviceKey = buildDeviceKey(userId, deviceId);
        Set<String> tokens = deviceTokens.get(deviceKey);
        
        if (tokens != null) {
            for (String tokenId : tokens) {
                revokedTokens.put(tokenId, System.currentTimeMillis() + (900 * 1000));
            }
            deviceTokens.remove(deviceKey);
            log.info("Revoked {} tokens for user: {}, device: {}", tokens.size(), userId, deviceId);
        }
        
        Set<TokenInfo> userTokenSet = userTokens.get(userId);
        if (userTokenSet != null) {
            userTokenSet.removeIf(info -> deviceId.equals(info.deviceId));
        }
    }
    
    @Override
    public void revokeAllTokensForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        
        Set<TokenInfo> tokens = userTokens.get(userId);
        if (tokens != null) {
            for (TokenInfo info : tokens) {
                revokedTokens.put(info.tokenId, System.currentTimeMillis() + (900 * 1000));
                
                String deviceKey = buildDeviceKey(userId, info.deviceId);
                deviceTokens.remove(deviceKey);
            }
            log.info("Revoked {} tokens for user: {}", tokens.size(), userId);
            userTokens.remove(userId);
        }
    }
    
    @Override
    public void registerToken(String tokenId, UUID userId, String deviceId, long expirationSeconds) {
        if (tokenId == null || userId == null) {
            return;
        }
        
        TokenInfo tokenInfo = new TokenInfo(tokenId, deviceId);
        userTokens.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(tokenInfo);
        
        if (deviceId != null) {
            String deviceKey = buildDeviceKey(userId, deviceId);
            deviceTokens.computeIfAbsent(deviceKey, k -> ConcurrentHashMap.newKeySet()).add(tokenId);
        }
        
        log.debug("Registered token: {} for user: {}, device: {}", tokenId, userId, deviceId);
        
        scheduleCleanup(tokenId, userId, deviceId, expirationSeconds);
    }
    
    private String buildDeviceKey(UUID userId, String deviceId) {
        return userId.toString() + ":" + (deviceId != null ? deviceId : "unknown");
    }
    
    private void scheduleCleanup(String tokenId, UUID userId, String deviceId, long expirationSeconds) {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(expirationSeconds * 1000);
                
                revokedTokens.remove(tokenId);
                
                Set<TokenInfo> userTokenSet = userTokens.get(userId);
                if (userTokenSet != null) {
                    userTokenSet.removeIf(info -> tokenId.equals(info.tokenId));
                    if (userTokenSet.isEmpty()) {
                        userTokens.remove(userId);
                    }
                }
                
                if (deviceId != null) {
                    String deviceKey = buildDeviceKey(userId, deviceId);
                    Set<String> deviceTokenSet = deviceTokens.get(deviceKey);
                    if (deviceTokenSet != null) {
                        deviceTokenSet.remove(tokenId);
                        if (deviceTokenSet.isEmpty()) {
                            deviceTokens.remove(deviceKey);
                        }
                    }
                }
                
                log.debug("Cleaned up expired token: {}", tokenId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    private record TokenInfo(String tokenId, String deviceId) {}
}
