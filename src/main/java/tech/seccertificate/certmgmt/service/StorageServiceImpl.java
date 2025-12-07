package tech.seccertificate.certmgmt.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.config.StorageConfig;
import tech.seccertificate.certmgmt.exception.StorageException;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of StorageService using MinIO client.
 * Provides operations for uploading, downloading, and managing files in MinIO/S3 storage.
 *
 * <p>This service handles:
 * <ul>
 *   <li>PDF certificate uploads</li>
 *   <li>File downloads via InputStream</li>
 *   <li>Signed URL generation for temporary access</li>
 *   <li>Bucket management</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final StorageConfig storageConfig;

    /**
     * Initialize storage service and ensure default bucket exists.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing StorageService with bucket: {}", storageConfig.getBucketName());
        try {
            ensureBucketExists(storageConfig.getBucketName());
            log.info("StorageService initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize StorageService: {}", e.getMessage());
            // Don't fail startup - bucket can be created later
        }
    }

    @Override
    public void uploadFile(String bucketName, String objectPath, InputStream inputStream,
                           String contentType, long contentLength) {
        log.debug("Uploading file to bucket: {}, path: {}, contentType: {}, size: {} bytes",
                bucketName, objectPath, contentType, contentLength);

        try {
            ensureBucketExists(bucketName);

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(inputStream, contentLength, -1)
                    .contentType(contentType)
                    .build());

            log.info("File uploaded successfully to {}/{}", bucketName, objectPath);
        } catch (Exception e) {
            log.error("Failed to upload file to {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.uploadFailed(objectPath, e);
        }
    }

    @Override
    public void uploadFile(String bucketName, String objectPath, byte[] data, String contentType) {
        log.debug("Uploading file (byte array) to bucket: {}, path: {}, size: {} bytes",
                bucketName, objectPath, data.length);

        try (var inputStream = new ByteArrayInputStream(data)) {
            uploadFile(bucketName, objectPath, inputStream, contentType, data.length);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file to {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.uploadFailed(objectPath, e);
        }
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectPath) {
        log.debug("Downloading file from bucket: {}, path: {}", bucketName, objectPath);

        try {
            var response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build());

            log.info("File download initiated from {}/{}", bucketName, objectPath);
            return response;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                log.warn("File not found: {}/{}", bucketName, objectPath);
                throw new StorageException("File not found: " + objectPath);
            }
            log.error("Failed to download file from {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.downloadFailed(objectPath, e);
        } catch (Exception e) {
            log.error("Failed to download file from {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.downloadFailed(objectPath, e);
        }
    }

    @Override
    public String generateSignedUrl(String bucketName, String objectPath, int expirationMinutes) {
        log.debug("Generating signed URL for bucket: {}, path: {}, expiration: {} minutes",
                bucketName, objectPath, expirationMinutes);

        // Use default expiration if not specified or invalid
        int effectiveExpiration = expirationMinutes > 0
                ? expirationMinutes
                : storageConfig.getSignedUrlExpirationMinutes();

        // MinIO has a maximum presigned URL expiration of 7 days
        if (effectiveExpiration > 7 * 24 * 60) {
            log.warn("Expiration time {} minutes exceeds 7 days limit, capping to 7 days", effectiveExpiration);
            effectiveExpiration = 7 * 24 * 60;
        }

        try {
            var url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectPath)
                    .expiry(effectiveExpiration, TimeUnit.MINUTES)
                    .build());

            log.info("Signed URL generated for {}/{} with expiration {} minutes",
                    bucketName, objectPath, effectiveExpiration);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate signed URL for {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.signedUrlFailed(objectPath, e);
        }
    }

    @Override
    public boolean fileExists(String bucketName, String objectPath) {
        log.debug("Checking if file exists in bucket: {}, path: {}", bucketName, objectPath);

        try {
            var obj = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build());

            log.debug("File exists: {}/{} - Last Modified {}", bucketName, objectPath, obj.lastModified());
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                log.debug("File does not exist: {}/{}", bucketName, objectPath);
                return false;
            }
            log.error("Failed to check file existence for file {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.existsCheckFailed(objectPath, e);
        } catch (Exception e) {
            log.error("Failed to check file existence for {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.existsCheckFailed(objectPath, e);
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectPath) {
        log.debug("Deleting file from bucket: {}, path: {}", bucketName, objectPath);

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build());

            log.info("File deleted successfully from {}/{}", bucketName, objectPath);
        } catch (Exception e) {
            log.error("Failed to delete file from {}/{}: {}", bucketName, objectPath, e.getMessage());
            throw StorageException.deleteFailed(objectPath, e);
        }
    }

    @Override
    public void ensureBucketExists(String bucketName) {
        log.debug("Ensuring bucket exists: {}", bucketName);

        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());

            if (!exists) {
                log.info("Creating bucket: {}", bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
                log.info("Bucket created successfully: {}", bucketName);
            } else {
                log.debug("Bucket already exists: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket exists {}: {}", bucketName, e.getMessage());
            throw StorageException.bucketCreationFailed(bucketName, e);
        }
    }

    @Override
    public String getDefaultBucketName() {
        return storageConfig.getBucketName();
    }
}
