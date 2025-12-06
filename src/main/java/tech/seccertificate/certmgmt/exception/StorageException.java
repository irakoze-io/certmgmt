package tech.seccertificate.certmgmt.exception;

/**
 * Exception thrown when storage operations fail.
 * This covers MinIO/S3 operations like upload, download, URL generation, etc.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an exception for upload failure.
     * 
     * @param objectPath The object path that failed to upload
     * @param cause The underlying cause
     * @return StorageException with descriptive message
     */
    public static StorageException uploadFailed(String objectPath, Throwable cause) {
        return new StorageException("Failed to upload file to storage: " + objectPath, cause);
    }

    /**
     * Create an exception for download failure.
     * 
     * @param objectPath The object path that failed to download
     * @param cause The underlying cause
     * @return StorageException with descriptive message
     */
    public static StorageException downloadFailed(String objectPath, Throwable cause) {
        return new StorageException("Failed to download file from storage: " + objectPath, cause);
    }

    /**
     * Create an exception for signed URL generation failure.
     * 
     * @param objectPath The object path for which URL generation failed
     * @param cause The underlying cause
     * @return StorageException with descriptive message
     */
    public static StorageException signedUrlFailed(String objectPath, Throwable cause) {
        return new StorageException("Failed to generate signed URL for: " + objectPath, cause);
    }

    /**
     * Create an exception for bucket creation failure.
     * 
     * @param bucketName The bucket name that failed to create
     * @param cause The underlying cause
     * @return StorageException with descriptive message
     */
    public static StorageException bucketCreationFailed(String bucketName, Throwable cause) {
        return new StorageException("Failed to create bucket: " + bucketName, cause);
    }

    /**
     * Create an exception for delete failure.
     * 
     * @param objectPath The object path that failed to delete
     * @param cause The underlying cause
     * @return StorageException with descriptive message
     */
    public static StorageException deleteFailed(String objectPath, Throwable cause) {
        return new StorageException("Failed to delete file from storage: " + objectPath, cause);
    }

    /**
     * Create an exception for file existence check failure.
     * 
     * @param objectPath The object path that failed to check
     * @param cause The underlying cause
     * @return StorageException with descriptive message
     */
    public static StorageException existsCheckFailed(String objectPath, Throwable cause) {
        return new StorageException("Failed to check if file exists: " + objectPath, cause);
    }
}
