package tech.seccertificate.certmgmt.service;

import java.io.InputStream;

/**
 * Service interface for object storage operations (MinIO/S3).
 * Handles PDF upload, download, and signed URL generation.
 */
public interface StorageService {

    /**
     * Upload a file to storage.
     *
     * @param bucketName The bucket name
     * @param objectPath The object path (key) in the bucket
     * @param inputStream The input stream containing file data
     * @param contentType The content type (e.g., "application/pdf")
     * @param contentLength The content length in bytes
     */
    void uploadFile(String bucketName, String objectPath, InputStream inputStream,
                    String contentType, long contentLength);

    /**
     * Upload a file to storage (convenience method with byte array).
     *
     * @param bucketName The bucket name
     * @param objectPath The object path (key) in the bucket
     * @param data The file data as byte array
     * @param contentType The content type (e.g., "application/pdf")
     */
    void uploadFile(String bucketName, String objectPath, byte[] data, String contentType);

    /**
     * Download a file from storage.
     *
     * @param bucketName The bucket name
     * @param objectPath The object path (key) in the bucket
     * @return InputStream containing the file data
     */
    InputStream downloadFile(String bucketName, String objectPath);

    /**
     * Generate a signed URL for temporary access to a file.
     *
     * @param bucketName The bucket name
     * @param objectPath The object path (key) in the bucket
     * @param expirationMinutes Expiration time in minutes (default: 60)
     * @return Signed URL string
     */
    String generateSignedUrl(String bucketName, String objectPath, int expirationMinutes);

    /**
     * Check if a file exists in storage.
     *
     * @param bucketName The bucket name
     * @param objectPath The object path (key) in the bucket
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String bucketName, String objectPath);

    /**
     * Delete a file from storage.
     *
     * @param bucketName The bucket name
     * @param objectPath The object path (key) in the bucket
     */
    void deleteFile(String bucketName, String objectPath);

    /**
     * Ensure a bucket exists, create it if it doesn't.
     *
     * @param bucketName The bucket name
     */
    void ensureBucketExists(String bucketName);

    /**
     * Get the default bucket name configured for this storage service.
     *
     * @return Default bucket name
     */
    String getDefaultBucketName();
}
