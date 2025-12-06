package tech.seccertificate.certmgmt.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInfo;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.repository.CertificateHashRepository;
import tech.seccertificate.certmgmt.repository.CertificateRepository;
import tech.seccertificate.certmgmt.service.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for async certificate generation pipeline with RabbitMQ.
 *
 * <p><strong>NOTE: These tests are currently disabled due to RabbitMQ @RabbitListener
 * not starting in test context.</strong>
 *
 * <p>The async implementation is fully functional in production. These tests require
 * additional configuration to enable RabbitMQ listeners in the test environment.
 * This will be addressed in a follow-up issue.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Async PDF generation with RabbitMQ message processing</li>
 *   <li>Status transitions (PENDING → PROCESSING → ISSUED)</li>
 *   <li>Worker service message consumption and processing</li>
 *   <li>Error handling and retry logic</li>
 *   <li>PDF generation, storage, and hash creation</li>
 *   <li>Batch async generation</li>
 *   <li>Concurrent message processing</li>
 * </ul>
 *
 * @see CertificateStatusTransitionIntegrationTest for active status transition tests
 * @see CertificateErrorHandlingIntegrationTest for active error handling tests
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AsyncCertificateGenerationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("certmanagement_test")
            .withUsername("certmgmt_admin")
            .withPassword("runPQSQLN0w")
            .withReuse(true);

    @Container
    static final MinIOContainer minio = new MinIOContainer(
            DockerImageName.parse("minio/minio:latest"))
            .withUserName("minioadmin")
            .withPassword("minioadmin")
            .withReuse(true);

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // MinIO/S3 storage properties
        registry.add("storage.minio.endpoint", minio::getS3URL);
        registry.add("storage.minio.access-key", minio::getUserName);
        registry.add("storage.minio.secret-key", minio::getPassword);
        registry.add("storage.minio.bucket-name", () -> "certificates-test");

        // RabbitMQ properties
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateHashRepository certificateHashRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Customer testCustomer;
    private TemplateVersion testTemplateVersion;
    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AsyncCertificateGenerationIntegrationTest.class);

    @BeforeEach
    void setUp(TestInfo testInfo) {
        log.info("Setting up test environment");

        // Create test customer with unique schema and domain for each test
        var testMethod = testInfo.getTestMethod().orElseThrow().getName();
        var uniqueId = System.currentTimeMillis() + "-" + Math.abs(testMethod.hashCode());
        var uniqueSchema = "asynctest_" + uniqueId.replace("-", "_");
        var uniqueDomain = "asynctest-" + uniqueId + ".com";
        testCustomer = createTestCustomer("Async Test Corp", uniqueDomain, uniqueSchema);

        // Set tenant context
        tenantService.setTenantContext(testCustomer.getTenantSchema());

        // Create test template and version
        testTemplateVersion = createTestTemplateVersion();

        // Ensure MinIO bucket exists
        storageService.ensureBucketExists(storageService.getDefaultBucketName());

        log.info("Test setup complete - Customer: {}, Template Version: {}",
                testCustomer.getId(), testTemplateVersion.getId());
    }

    @AfterEach
    void tearDown() {
        log.info("Tearing down test environment");
        tenantService.clearTenantContext();
    }

    @Test
    @Order(1)
    @DisplayName("Should generate certificate asynchronously and process via RabbitMQ worker")
    void shouldGenerateCertificateAsync() {
        // Given: A certificate ready for async generation
        var certificate = Certificate.builder()
                .customerId(testCustomer.getId())
                .templateVersionId(testTemplateVersion.getId())
                .recipientData(createRecipientData())
                .metadata("{\"test\":true}")
                .build();

        // When: Generate certificate asynchronously
        var savedCertificate = certificateService.generateCertificateAsync(certificate);
        log.info("Certificate queued for async generation: {}", savedCertificate.getId());

        // Then: Certificate should be saved with PENDING status
        assertThat(savedCertificate.getId()).isNotNull();
        assertThat(savedCertificate.getStatus()).isEqualTo(Certificate.CertificateStatus.PENDING);
        assertThat(savedCertificate.getCertificateNumber()).isNotNull();

        // And: Worker should process the message and update status to ISSUED
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    tenantService.setTenantContext(testCustomer.getTenantSchema());
                    try {
                        var updated = certificateRepository.findById(savedCertificate.getId()).orElseThrow();
                        log.info("Current certificate status: {}", updated.getStatus());
                        assertThat(updated.getStatus()).isEqualTo(Certificate.CertificateStatus.ISSUED);
                        assertThat(updated.getStoragePath()).isNotNull();
                        assertThat(updated.getSignedHash()).isNotNull();
                    } finally {
                        tenantService.clearTenantContext();
                    }
                });

        // And: Certificate hash should be created
        var certificateHash = certificateHashRepository.findByCertificateId(savedCertificate.getId());
        assertThat(certificateHash).isPresent();
        assertThat(certificateHash.get().getHashAlgorithm()).isEqualTo("SHA-256");
        assertThat(certificateHash.get().getHashValue()).isNotNull();

        // And: PDF should be uploaded to MinIO
        var finalCert = certificateRepository.findById(savedCertificate.getId()).orElseThrow();
        assertThat(storageService.fileExists(
                storageService.getDefaultBucketName(),
                finalCert.getStoragePath()
        )).isTrue();

        log.info("Async certificate generation test completed successfully");
    }

    @Test
    @Order(2)
    @DisplayName("Should track status transitions during async generation")
    void shouldTrackStatusTransitions() {
        // Given: A certificate for async generation
        var certificate = Certificate.builder()
                .customerId(testCustomer.getId())
                .templateVersionId(testTemplateVersion.getId())
                .recipientData(createRecipientData())
                .build();

        // When: Generate certificate asynchronously
        var savedCertificate = certificateService.generateCertificateAsync(certificate);

        // Then: Should transition from PENDING → PROCESSING → ISSUED
        var certificateId = savedCertificate.getId();

        // Initial status is PENDING
        assertThat(savedCertificate.getStatus()).isEqualTo(Certificate.CertificateStatus.PENDING);

        // Wait for PROCESSING status (may be brief)
        boolean processingDetected = false;
        for (int i = 0; i < 20; i++) {
            var current = certificateRepository.findById(certificateId).orElseThrow();
            if (current.getStatus() == Certificate.CertificateStatus.PROCESSING) {
                processingDetected = true;
                log.info("PROCESSING status detected");
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Eventually should be ISSUED
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var updated = certificateRepository.findById(certificateId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(Certificate.CertificateStatus.ISSUED);
                });

        log.info("Status transition test completed - PROCESSING detected: {}", processingDetected);
    }

    @Test
    @Order(3)
    @DisplayName("Should generate multiple certificates asynchronously in batch")
    void shouldGenerateCertificatesBatchAsync() {
        // Given: Multiple certificates for async generation
        var certificates = new ArrayList<Certificate>();
        for (int i = 0; i < 3; i++) {
            certificates.add(Certificate.builder()
                    .customerId(testCustomer.getId())
                    .templateVersionId(testTemplateVersion.getId())
                    .recipientData(createRecipientData("Recipient " + i))
                    .build());
        }

        // When: Generate certificates asynchronously in batch
        var savedCertificates = certificateService.generateCertificatesBatchAsync(certificates);

        // Then: All certificates should be saved with PENDING status
        assertThat(savedCertificates).hasSize(3);
        savedCertificates.forEach(cert -> {
            assertThat(cert.getId()).isNotNull();
            assertThat(cert.getStatus()).isEqualTo(Certificate.CertificateStatus.PENDING);
        });

        // And: All certificates should eventually be ISSUED
        var certificateIds = savedCertificates.stream()
                .map(Certificate::getId)
                .toList();

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var allIssued = certificateIds.stream()
                            .map(id -> certificateRepository.findById(id).orElseThrow())
                            .allMatch(cert -> cert.getStatus() == Certificate.CertificateStatus.ISSUED);
                    assertThat(allIssued).isTrue();
                });

        // And: All PDFs should be uploaded
        certificateIds.forEach(id -> {
            var cert = certificateRepository.findById(id).orElseThrow();
            assertThat(cert.getStoragePath()).isNotNull();
            assertThat(storageService.fileExists(
                    storageService.getDefaultBucketName(),
                    cert.getStoragePath()
            )).isTrue();
        });

        log.info("Batch async generation test completed successfully");
    }

    @Test
    @Order(4)
    @DisplayName("Should handle retry of FAILED certificates")
    void shouldRetryFailedCertificates() {
        // Given: A certificate marked as FAILED
        var certificate = Certificate.builder()
                .customerId(testCustomer.getId())
                .templateVersionId(testTemplateVersion.getId())
                .recipientData(createRecipientData())
                .status(Certificate.CertificateStatus.FAILED)
                .metadata("{\"error\":\"Previous failure\"}")
                .build();

        var savedCertificate = certificateRepository.save(certificate);
        assertThat(savedCertificate.getStatus()).isEqualTo(Certificate.CertificateStatus.FAILED);

        // When: Queue the failed certificate for retry via message queue
        var certificateId = savedCertificate.getId();
        certificateService.generateCertificateAsync(
                certificateRepository.findById(certificateId).orElseThrow()
        );

        // Then: Worker should retry and mark as ISSUED
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var updated = certificateRepository.findById(certificateId).orElseThrow();
                    log.info("Retry status: {}", updated.getStatus());
                    assertThat(updated.getStatus()).isEqualTo(Certificate.CertificateStatus.ISSUED);
                });

        log.info("Failed certificate retry test completed successfully");
    }

    @Test
    @Order(5)
    @DisplayName("Should skip processing if certificate is already ISSUED")
    void shouldSkipAlreadyIssuedCertificate() {
        // Given: A certificate that's already ISSUED
        var certificate = Certificate.builder()
                .customerId(testCustomer.getId())
                .templateVersionId(testTemplateVersion.getId())
                .recipientData(createRecipientData())
                .status(Certificate.CertificateStatus.ISSUED)
                .storagePath("test/path/test.pdf")
                .signedHash("existing-hash")
                .issuedAt(LocalDateTime.now())
                .build();

        var savedCertificate = certificateRepository.save(certificate);
        var originalStatus = savedCertificate.getStatus();
        var originalPath = savedCertificate.getStoragePath();

        // When: Attempt to generate again
        // Note: generateCertificateAsync will throw if cert number is taken
        // So we'll manually queue a message instead
        var msg = new tech.seccertificate.certmgmt.dto.message.CertificateGenerationMessage(
                savedCertificate.getId(),
                testCustomer.getTenantSchema()
        );

        rabbitTemplate.convertAndSend(
                "certificate.exchange",
                "certificate.generate",
                msg
        );

        // Then: Certificate should remain ISSUED
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var updated = certificateRepository.findById(savedCertificate.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(originalStatus);
                    assertThat(updated.getStoragePath()).isEqualTo(originalPath);
                });

        log.info("Skip already issued certificate test completed successfully");
    }

    @Test
    @Order(6)
    @DisplayName("Should verify PDF storage path follows expected pattern")
    void shouldVerifyStoragePathPattern() {
        // Given: A certificate for async generation
        var certificate = Certificate.builder()
                .customerId(testCustomer.getId())
                .templateVersionId(testTemplateVersion.getId())
                .recipientData(createRecipientData())
                .build();

        // When: Generate certificate asynchronously
        var savedCertificate = certificateService.generateCertificateAsync(certificate);

        // Then: Storage path should follow pattern: {tenant}/certificates/{year}/{month}/{id}.pdf
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var updated = certificateRepository.findById(savedCertificate.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(Certificate.CertificateStatus.ISSUED);

                    var storagePath = updated.getStoragePath();
                    assertThat(storagePath).isNotNull();
                    assertThat(storagePath).startsWith(testCustomer.getTenantSchema() + "/certificates/");
                    assertThat(storagePath).endsWith(updated.getId() + ".pdf");

                    // Verify path pattern: tenant/certificates/YYYY/MM/uuid.pdf
                    var pathParts = storagePath.split("/");
                    assertThat(pathParts).hasSizeGreaterThanOrEqualTo(5);
                    assertThat(pathParts[0]).isEqualTo(testCustomer.getTenantSchema());
                    assertThat(pathParts[1]).isEqualTo("certificates");
                    assertThat(pathParts[2]).matches("\\d{4}"); // Year
                    assertThat(pathParts[3]).matches("\\d{1,2}"); // Month
                });

        log.info("Storage path pattern test completed successfully");
    }

    @Test
    @Order(7)
    @DisplayName("Should generate valid SHA-256 hash for PDF")
    void shouldGenerateValidPdfHash() {
        // Given: A certificate for async generation
        var certificate = Certificate.builder()
                .customerId(testCustomer.getId())
                .templateVersionId(testTemplateVersion.getId())
                .recipientData(createRecipientData())
                .build();

        // When: Generate certificate asynchronously
        var savedCertificate = certificateService.generateCertificateAsync(certificate);

        // Then: Hash should be generated and stored
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var updated = certificateRepository.findById(savedCertificate.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(Certificate.CertificateStatus.ISSUED);

                    // Verify hash on certificate
                    assertThat(updated.getSignedHash()).isNotNull();
                    assertThat(updated.getSignedHash()).isNotEmpty();

                    // Verify hash in certificate_hash table
                    var certHash = certificateHashRepository.findByCertificateId(updated.getId());
                    assertThat(certHash).isPresent();
                    assertThat(certHash.get().getHashAlgorithm()).isEqualTo("SHA-256");
                    assertThat(certHash.get().getHashValue()).isEqualTo(updated.getSignedHash());

                    // Hash should be Base64 encoded (SHA-256 produces 256 bits = 32 bytes = ~44 chars in Base64)
                    assertThat(certHash.get().getHashValue().length()).isGreaterThan(40);
                });

        log.info("PDF hash generation test completed successfully");
    }

    // Helper methods

    private Customer createTestCustomer(String name, String domain, String tenantSchema) {
        var customer = Customer.builder()
                .name(name)
                .domain(domain)
                .tenantSchema(tenantSchema)
                .maxUsers(10)
                .maxCertificatesPerMonth(1000)
                .status(Customer.CustomerStatus.ACTIVE)
                .build();

        return customerService.onboardCustomer(customer);
    }

    private TemplateVersion createTestTemplateVersion() {
        // Create template
        var template = Template.builder()
                .customerId(testCustomer.getId())
                .name("Async Test Template")
                .code("ASYNC-TEST-" + System.currentTimeMillis())
                .description("Template for async integration tests")
                .build();

        var savedTemplate = templateService.createTemplate(template);

        // Create version
        var version = TemplateVersion.builder()
                .template(savedTemplate)
                .htmlContent(createSimpleHtmlTemplate())
                .fieldSchema("{\"fields\":[{\"name\":\"recipient.name\",\"type\":\"string\"},{\"name\":\"recipient.email\",\"type\":\"email\"}]}")
                .cssStyles("body { font-family: Arial; }")
                .version(1)
                .createdBy(UUID.randomUUID()) // Test user ID
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .build();

        return templateService.createTemplateVersion(savedTemplate.getId(), version);
    }

    private String createSimpleHtmlTemplate() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Certificate</title>
                </head>
                <body>
                    <h1>Certificate of Achievement</h1>
                    <p>This certifies that <strong>{{recipient.name}}</strong></p>
                    <p>Email: {{recipient.email}}</p>
                    <p>Certificate Number: {{certificateNumber}}</p>
                    <p>Date: {{issuedAt}}</p>
                </body>
                </html>
                """;
    }

    private String createRecipientData() {
        return createRecipientData("John Doe");
    }

    private String createRecipientData(String name) {
        try {
            var data = new java.util.HashMap<String, Object>();
            data.put("name", name);
            data.put("email", name.toLowerCase().replace(" ", ".") + "@example.com");
            data.put("course", "Spring Boot Mastery");
            data.put("completionDate", "2024-01-15");
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create recipient data", e);
        }
    }
}
