package tech.seccertificate.certmgmt.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.TenantService;

/**
 * Base class for integration tests.
 * Provides common setup and utilities for testing REST controllers.
 *
 * <p>Uses Testcontainers to provide an isolated PostgreSQL database instance.
 * This ensures:
 * <ul>
 *   <li>Fresh database for each test run</li>
 *   <li>All migrations run cleanly</li>
 *   <li>No schema conflicts or cleanup issues</li>
 *   <li>Complete test isolation</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Testcontainers
public abstract class BaseIntegrationTest {

    /**
     * PostgreSQL Testcontainer instance.
     * Shared across all tests in the same JVM for performance (container reuse).
     * The container is started automatically by Testcontainers.
     */
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("certmanagement_test")
            .withUsername("certmgmt_admin")
            .withPassword("runPQSQLN0w")
            .withReuse(true); // Reuse container across test runs for faster execution

    /**
     * Dynamically configure Spring Boot properties with Testcontainers database connection.
     * This overrides the properties in application-test.properties.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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
    protected TenantService tenantService;

    /**
     * Initialize MockMvc before each test.
     */
    protected void initMockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        }
    }

    /**
     * Create a test customer and return it.
     * The customer will have a tenant schema created.
     */
    protected Customer createTestCustomer(String name, String domain, String tenantSchema) {
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

    /**
     * Set tenant context for the current thread.
     */
    protected void setTenantContext(Long customerId) {
        tenantService.setTenantContext(customerId);
    }

    /**
     * Set tenant context using schema name.
     */
    protected void setTenantContext(String schemaName) {
        tenantService.setTenantContext(schemaName);
    }

    /**
     * Clear tenant context.
     */
    protected void clearTenantContext() {
        tenantService.clearTenantContext();
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
        TenantContext.clear();
    }
}
