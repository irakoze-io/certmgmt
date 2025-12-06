package tech.seccertificate.certmgmt.config;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for MinIO/S3 storage settings.
 * 
 * <p>Configuration properties are loaded from application.properties
 * with the prefix "storage.minio".
 * 
 * <p>Example configuration:
 * <pre>
 * storage.minio.endpoint=http://localhost:9000
 * storage.minio.access-key=minioadmin
 * storage.minio.secret-key=minioadmin
 * storage.minio.bucket-name=certificates
 * storage.minio.signed-url-expiration-minutes=60
 * </pre>
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "storage.minio")
public class StorageConfig {

    /**
     * MinIO server endpoint URL.
     * Example: http://localhost:9000
     */
    private String endpoint = "http://localhost:9000";

    /**
     * MinIO access key (username).
     */
    private String accessKey = "minioadmin";

    /**
     * MinIO secret key (password).
     */
    private String secretKey = "minioadmin";

    /**
     * Default bucket name for storing certificates.
     */
    private String bucketName = "certificates";

    /**
     * Default expiration time for signed URLs in minutes.
     */
    private int signedUrlExpirationMinutes = 60;

    /**
     * Whether to use secure (HTTPS) connection.
     */
    private boolean secure = false;

    /**
     * Connection timeout in milliseconds.
     */
    private int connectTimeout = 10000;

    /**
     * Read timeout in milliseconds.
     */
    private int readTimeout = 30000;

    /**
     * Write timeout in milliseconds.
     */
    private int writeTimeout = 60000;

    /**
     * Creates a MinioClient bean configured with the properties.
     * 
     * @return Configured MinioClient instance
     */
    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO client with endpoint: {}", endpoint);
        
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
