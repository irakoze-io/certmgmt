package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.seccertificate.certmgmt.exception.StorageException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for StorageService.
 * Tests MinIO/S3 storage operations including upload, download, signed URLs, and file management.
 */
@DisplayName("StorageService Integration Tests")
class StorageServiceIntegrationTest extends BaseIntegrationTest {

    private static final String TEST_BUCKET = "certificates-test";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String TEXT_CONTENT_TYPE = "text/plain";

    @BeforeEach
    void setUp() {
        initMockMvc();
        // Ensure test bucket exists
        storageService.ensureBucketExists(TEST_BUCKET);
    }

    @Test
    @DisplayName("Should upload and download file successfully")
    void shouldUploadAndDownloadFile() throws IOException {
        // Given
        String objectPath = "test/" + UUID.randomUUID() + ".txt";
        String content = "Hello, MinIO Storage!";
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        // When - Upload
        storageService.uploadFile(TEST_BUCKET, objectPath, data, TEXT_CONTENT_TYPE);

        // Then - File should exist
        assertTrue(storageService.fileExists(TEST_BUCKET, objectPath));

        // When - Download
        try (var inputStream = storageService.downloadFile(TEST_BUCKET, objectPath)) {
            byte[] downloadedData = inputStream.readAllBytes();
            String downloadedContent = new String(downloadedData, StandardCharsets.UTF_8);

            // Then
            assertEquals(content, downloadedContent);
        }

        // Cleanup
        storageService.deleteFile(TEST_BUCKET, objectPath);
    }

    @Test
    @DisplayName("Should upload file using InputStream")
    void shouldUploadFileUsingInputStream() throws IOException {
        // Given
        String objectPath = "test/" + UUID.randomUUID() + ".txt";
        String content = "Stream content test";
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        
        try (var inputStream = new ByteArrayInputStream(data)) {
            // When
            storageService.uploadFile(TEST_BUCKET, objectPath, inputStream, TEXT_CONTENT_TYPE, data.length);

            // Then
            assertTrue(storageService.fileExists(TEST_BUCKET, objectPath));
        }

        // Cleanup
        storageService.deleteFile(TEST_BUCKET, objectPath);
    }

    @Test
    @DisplayName("Should upload PDF file successfully")
    void shouldUploadPdfFile() {
        // Given - Simple PDF-like content (not a real PDF, but tests upload)
        String objectPath = "certificates/2024/01/" + UUID.randomUUID() + ".pdf";
        byte[] pdfContent = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);

        // When
        storageService.uploadFile(TEST_BUCKET, objectPath, pdfContent, PDF_CONTENT_TYPE);

        // Then
        assertTrue(storageService.fileExists(TEST_BUCKET, objectPath));

        // Cleanup
        storageService.deleteFile(TEST_BUCKET, objectPath);
    }

    @Test
    @DisplayName("Should generate signed URL for existing file")
    void shouldGenerateSignedUrlForExistingFile() {
        // Given
        String objectPath = "test/" + UUID.randomUUID() + ".txt";
        byte[] data = "URL test content".getBytes(StandardCharsets.UTF_8);
        storageService.uploadFile(TEST_BUCKET, objectPath, data, TEXT_CONTENT_TYPE);

        // When
        String signedUrl = storageService.generateSignedUrl(TEST_BUCKET, objectPath, 60);

        // Then
        assertNotNull(signedUrl);
        assertTrue(signedUrl.startsWith("http"));
        assertTrue(signedUrl.contains(objectPath.replace("/", "%2F")) || signedUrl.contains(objectPath));

        // Cleanup
        storageService.deleteFile(TEST_BUCKET, objectPath);
    }

    @Test
    @DisplayName("Should generate signed URL with custom expiration")
    void shouldGenerateSignedUrlWithCustomExpiration() {
        // Given
        String objectPath = "test/" + UUID.randomUUID() + ".txt";
        byte[] data = "Expiration test".getBytes(StandardCharsets.UTF_8);
        storageService.uploadFile(TEST_BUCKET, objectPath, data, TEXT_CONTENT_TYPE);

        // When - Generate URL with 5 minutes expiration
        String signedUrl = storageService.generateSignedUrl(TEST_BUCKET, objectPath, 5);

        // Then
        assertNotNull(signedUrl);
        assertTrue(signedUrl.startsWith("http"));

        // Cleanup
        storageService.deleteFile(TEST_BUCKET, objectPath);
    }

    @Test
    @DisplayName("Should delete file successfully")
    void shouldDeleteFile() {
        // Given
        String objectPath = "test/" + UUID.randomUUID() + ".txt";
        byte[] data = "Delete test".getBytes(StandardCharsets.UTF_8);
        storageService.uploadFile(TEST_BUCKET, objectPath, data, TEXT_CONTENT_TYPE);
        assertTrue(storageService.fileExists(TEST_BUCKET, objectPath));

        // When
        storageService.deleteFile(TEST_BUCKET, objectPath);

        // Then
        assertFalse(storageService.fileExists(TEST_BUCKET, objectPath));
    }

    @Test
    @DisplayName("Should return false for non-existent file")
    void shouldReturnFalseForNonExistentFile() {
        // Given
        String objectPath = "non-existent/" + UUID.randomUUID() + ".txt";

        // When
        boolean exists = storageService.fileExists(TEST_BUCKET, objectPath);

        // Then
        assertFalse(exists);
    }

    @Test
    @DisplayName("Should throw exception when downloading non-existent file")
    void shouldThrowExceptionWhenDownloadingNonExistentFile() {
        // Given
        String objectPath = "non-existent/" + UUID.randomUUID() + ".txt";

        // When & Then
        assertThrows(StorageException.class, () -> 
            storageService.downloadFile(TEST_BUCKET, objectPath)
        );
    }

    @Test
    @DisplayName("Should ensure bucket exists and create if needed")
    void shouldEnsureBucketExistsAndCreateIfNeeded() {
        // Given
        String newBucket = "test-bucket-" + UUID.randomUUID().toString().substring(0, 8);

        // When
        storageService.ensureBucketExists(newBucket);

        // Then - Upload should succeed to new bucket
        String objectPath = "test.txt";
        byte[] data = "Bucket test".getBytes(StandardCharsets.UTF_8);
        
        assertDoesNotThrow(() -> 
            storageService.uploadFile(newBucket, objectPath, data, TEXT_CONTENT_TYPE)
        );

        // Cleanup
        storageService.deleteFile(newBucket, objectPath);
    }

    @Test
    @DisplayName("Should get default bucket name from configuration")
    void shouldGetDefaultBucketName() {
        // When
        String defaultBucket = storageService.getDefaultBucketName();

        // Then
        assertNotNull(defaultBucket);
        assertEquals(TEST_BUCKET, defaultBucket);
    }

    @Test
    @DisplayName("Should handle nested paths correctly")
    void shouldHandleNestedPathsCorrectly() {
        // Given
        String objectPath = "tenant1/certificates/2024/12/05/" + UUID.randomUUID() + ".pdf";
        byte[] data = "Nested path test".getBytes(StandardCharsets.UTF_8);

        // When
        storageService.uploadFile(TEST_BUCKET, objectPath, data, PDF_CONTENT_TYPE);

        // Then
        assertTrue(storageService.fileExists(TEST_BUCKET, objectPath));
        
        String signedUrl = storageService.generateSignedUrl(TEST_BUCKET, objectPath, 60);
        assertNotNull(signedUrl);

        // Cleanup
        storageService.deleteFile(TEST_BUCKET, objectPath);
    }

    @Test
    @DisplayName("Should handle special characters in path")
    void shouldHandleSpecialCharactersInPath() {
        // Given - Path with allowed special characters
        String objectPath = "test/certificate_v1-final_" + UUID.randomUUID() + ".pdf";
        byte[] data = "Special chars test".getBytes(StandardCharsets.UTF_8);

        // When
        storageService.uploadFile(TEST_BUCKET, objectPath, data, PDF_CONTENT_TYPE);

        // Then
        assertTrue(storageService.fileExists(TEST_BUCKET, objectPath));

        // Cleanup
        storageService.deleteFile(TEST_BUCKET, objectPath);
    }
}
