package tech.seccertificate.certmgmt.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.config.TenantResolutionFilter;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.repository.CustomerRepository;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.StorageService;
import tech.seccertificate.certmgmt.service.TemplateService;
import tech.seccertificate.certmgmt.service.TenantService;

import java.util.Map;
import java.util.Random;
import java.util.UUID;


/**
 * Base class for integration tests.
 * Provides common setup and utilities for testing REST controllers.
 *
 * <p>Uses Testcontainers to provide isolated PostgreSQL and MinIO instances.
 * This ensures:
 * <ul>
 *   <li>Fresh database for each test run</li>
 *   <li>All migrations run cleanly</li>
 *   <li>No schema conflicts or cleanup issues</li>
 *   <li>Complete test isolation</li>
 *   <li>S3/MinIO storage for certificate PDFs</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    private final static Logger log = LoggerFactory.getLogger(BaseIntegrationTest.class);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("certmanagement_test")
            .withUsername("admin")
            .withPassword("pw")
            .withReuse(true); // Reuse container across test runs for faster execution

    /**
     * MinIO Testcontainer instance for S3-compatible storage.
     * Shared across all tests in the same JVM for performance.
     */
    @Container
    static final MinIOContainer minio = new MinIOContainer(
            DockerImageName.parse("minio/minio:latest"))
            .withUserName("minioadmin")
            .withPassword("minioadmin")
            .withReuse(true);

    /**
     * Dynamically configure Spring Boot properties with Testcontainers connections.
     * This overrides the properties in application-test.properties.
     */
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
    }

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected CustomerRepository customerRepository;

    @Autowired
    protected TemplateService templateService;

    @Autowired
    protected TenantService tenantService;

    @Autowired
    protected TenantResolutionFilter tenantResolutionFilter;

    @Autowired
    protected StorageService storageService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        initMockMvc();
        clearTenantContext(); // Ensure we start with a clean slate
    }

    /**
     * Initialize MockMvc before each test.
     */
    protected void initMockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders
                    .webAppContextSetup(webApplicationContext)
                    .addFilters(tenantResolutionFilter)
                    .build();
        }
    }

    /**
     * Create a test customer and return it.
     * The customer will have a tenant schema created, and the tenant context will be set.
     */
    protected Customer createTestCustomer(String name, String domain, String tenantSchema) {
        var createdCustomer = customerService.onboardCustomer(Customer.builder()
                .name(name)
                .domain(domain)
                .tenantSchema(tenantSchema)
                .maxUsers(10)
                .maxCertificatesPerMonth(1000)
                .status(Customer.CustomerStatus.ACTIVE)
                .build());

        setTenantContext(createdCustomer.getTenantSchema());

        return createdCustomer;
    }

    /**
     * Create a test template for earlier created customer
     * The template has to be linked to a customer.
     */
    protected Template createTestTemplate(Customer customer) {
        log.info("Creating test template for customer {}", customer);
        log.debug("Current tenant context before creating template: {}", TenantContext.getTenantSchema());

        var digitsOnlyUUID = UUID.randomUUID().toString()
                .replaceAll("-", "")
                .replaceAll("[^0-9]", "");
        log.info("Getting digits only code {}", digitsOnlyUUID);

        var template = templateService.createTemplate(Template.builder()
                .customerId(customer.getId())
                .code(digitsOnlyUUID)
                .name("Test Template" + customer.getId())
                .description("Test_Template_" + digitsOnlyUUID)
                .currentVersion(new Random().nextInt(10))
                .build());

        log.debug("Template created: {}", template);

        return template;
    }

    /**
     * Creates a test template version
     * The template version has to be linked to an already existing Template
     */
    protected TemplateVersion createTestTemplateVersion(Template template) {
        log.info("Creating a template version for template with details: {}", template);

        var fs = "";
        try {
            var fsMap = Map.of("name", "string", "email", "string");
            fs = objectMapper.writeValueAsString(fsMap);
        }
        catch (Exception _) {}

        var tvr = TemplateVersion.builder()
                .template(template)
                .createdBy(UUID.randomUUID())
                .htmlContent("<html><h1>Test</h1></html>")
                .fieldSchema(fs)
                .build();

        try {
            return templateService.createTemplateVersion(template.getId(), tvr);
        } catch (Exception e) {
            log.error("An error occurred while creating a template version for template with ID {}: {}",
                    template.getId(), e.getMessage());
            return TemplateVersion.builder().build();
        }
    }

    /**
     * Set tenant context for the current thread and updates the current transaction's search_path.
     */
    protected void setTenantContext(Long customerId) {
        customerRepository.findById(customerId).ifPresent(customer -> {
            setTenantContext(customer.getTenantSchema());
        });
    }

    /**
     * Set tenant context using schema name and updates the current transaction's search_path.
     * This method is now safe to call both inside and outside a transaction.
     */
    protected void setTenantContext(String schemaName) {
        TenantContext.setTenantSchema(schemaName);

        var sanitizedSchema = (schemaName == null || schemaName.isEmpty() || "public".equals(schemaName))
                ? "public"
                : schemaName.replaceAll("[^a-zA-Z0-9_]", "");

        // Checks if a transaction is active.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // If in a transaction, flush, clear, and use the session's connection.
            log.debug("Active transaction detected. Flushing, clearing, and setting search_path on Hibernate session.");
            entityManager.flush();
            entityManager.clear();

            var session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET search_path TO " + sanitizedSchema + ", public");
                }
            });
        } else {
            // If not in a transaction, use JdbcTemplate to set the search_path.
            // This is suitable for tests with Propagation.NOT_SUPPORTED.
            log.debug("No active transaction. Setting search_path using JdbcTemplate.");
            jdbcTemplate.execute("SET search_path TO " + sanitizedSchema + ", public");
        }
        log.info("Successfully set search_path to '{}'.", sanitizedSchema);
    }

    /**
     * Clear tenant context and resets the search_path to public.
     */
    protected void clearTenantContext() {
        setTenantContext("public");
    }

    /**
     * Add X-Tenant-Id header to request builder.
     */
    protected MockHttpServletRequestBuilder withTenantHeader(
            MockHttpServletRequestBuilder builder,
            Long customerId) {
        return builder.header("X-Tenant-Id", customerId.toString());
    }

    /**
     * Add X-Tenant-Schema header to request builder.
     */
    protected MockHttpServletRequestBuilder withTenantSchemaHeader(
            MockHttpServletRequestBuilder builder,
            String schemaName) {
        return builder.header("X-Tenant-Schema", schemaName);
    }

    /**
     * Cleanup after each test - clear tenant context.
     */
    protected void cleanup() {
        clearTenantContext();
    }
}
