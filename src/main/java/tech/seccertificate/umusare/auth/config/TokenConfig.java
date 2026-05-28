package tech.seccertificate.umusare.auth.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import tech.seccertificate.umusare.auth.exception.ErrorCode;
import tech.seccertificate.umusare.auth.exception.TokenException;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Configuration for RS256 JWT token signing and validation.
 * 
 * <p>Manages RSA key pair for JWT signatures per §9.4 and Appendix B.
 * 
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code umusare.jwt.private-key-path} - Path to RSA private key (PEM format)</li>
 *   <li>{@code umusare.jwt.public-key-path} - Path to RSA public key (PEM format)</li>
 *   <li>{@code umusare.jwt.expiration-seconds} - Token expiration in seconds (default: 900 per INV-I11)</li>
 *   <li>{@code umusare.jwt.issuer} - JWT issuer claim (iss)</li>
 *   <li>{@code umusare.jwt.audience} - JWT audience claim (aud)</li>
 * </ul>
 * 
 * <p>If key paths are not provided, generates an ephemeral key pair for development.
 * Production deployments MUST provide actual key files.
 */
@Slf4j
@Getter
@Configuration
public class TokenConfig {
    
    private static final int RSA_KEY_SIZE = 2048;
    
    @Value("${umusare.jwt.private-key-path:}")
    private String privateKeyPath;
    
    @Value("${umusare.jwt.public-key-path:}")
    private String publicKeyPath;
    
    @Value("${umusare.jwt.expiration-seconds:900}")
    private long expirationSeconds;
    
    @Value("${umusare.jwt.issuer:umusare-auth}")
    private String issuer;
    
    @Value("${umusare.jwt.audience:umusare-api}")
    private String audience;
    
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    
    @PostConstruct
    public void init() {
        try {
            if (hasKeyPaths()) {
                loadKeysFromFiles();
            } else {
                generateEphemeralKeyPair();
            }
            log.info("TokenConfig initialized - issuer: {}, audience: {}, expiration: {}s", 
                    issuer, audience, expirationSeconds);
        } catch (Exception e) {
            log.error("Failed to initialize RSA keys", e);
            throw new TokenException(ErrorCode.SERVER_001, "Failed to initialize token configuration", e);
        }
    }
    
    private boolean hasKeyPaths() {
        return privateKeyPath != null && !privateKeyPath.isBlank() 
                && publicKeyPath != null && !publicKeyPath.isBlank();
    }
    
    /**
     * Loads RSA keys from PEM files.
     */
    private void loadKeysFromFiles() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        log.info("Loading RSA keys from files - private: {}, public: {}", privateKeyPath, publicKeyPath);
        
        String privateKeyPem = Files.readString(Path.of(privateKeyPath));
        String publicKeyPem = Files.readString(Path.of(publicKeyPath));
        
        this.privateKey = parsePrivateKey(privateKeyPem);
        this.publicKey = parsePublicKey(publicKeyPem);
        
        log.info("RSA keys loaded successfully from files");
    }
    
    /**
     * Generates an ephemeral RSA key pair for development/testing.
     * Production deployments should provide actual key files.
     */
    private void generateEphemeralKeyPair() throws NoSuchAlgorithmException {
        log.warn("No RSA key files configured - generating ephemeral key pair. " +
                "This is suitable for development only. Configure umusare.jwt.private-key-path " +
                "and umusare.jwt.public-key-path for production.");
        
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(RSA_KEY_SIZE);
        KeyPair keyPair = keyGen.generateKeyPair();
        
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        
        log.info("Ephemeral RSA key pair generated ({}bit)", RSA_KEY_SIZE);
    }
    
    /**
     * Parses a PKCS#8 formatted private key from PEM string.
     */
    private RSAPrivateKey parsePrivateKey(String pem) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String keyContent = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }
    
    /**
     * Parses an X.509 formatted public key from PEM string.
     */
    private RSAPublicKey parsePublicKey(String pem) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String keyContent = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
}
