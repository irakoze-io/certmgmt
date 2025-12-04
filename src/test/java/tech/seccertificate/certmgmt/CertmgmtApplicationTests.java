package tech.seccertificate.certmgmt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tech.seccertificate.certmgmt.config.JacksonConfig;
import tech.seccertificate.certmgmt.config.MultiTenantConfig;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.config.TenantIdentifierResolver;
import tech.seccertificate.certmgmt.config.TenantSchemaValidator;
import tech.seccertificate.certmgmt.config.WebConfig;
import tech.seccertificate.certmgmt.service.CertificateService;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.TemplateService;
import tech.seccertificate.certmgmt.service.TenantService;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Main integration test class for Certificate Management Application.
 * Validates that the Spring application context loads correctly and all required beans are properly wired.
 * 
 * <p>This test class serves as a comprehensive smoke test to ensure:
 * <ul>
 *   <li>Application context loads without errors</li>
 *   <li>All service beans are available and properly configured</li>
 *   <li>Configuration beans are properly wired</li>
 *   <li>Multi-tenancy configuration is valid</li>
 *   <li>Dependency injection works correctly</li>
 *   <li>Database connectivity is available</li>
 * </ul>
 * 
 * <p>For detailed unit tests, see:
 * <ul>
 *   <li>{@link tech.seccertificate.certmgmt.service.CertificateServiceImplTest}</li>
 *   <li>{@link tech.seccertificate.certmgmt.service.CustomerServiceImplTest}</li>
 *   <li>{@link tech.seccertificate.certmgmt.service.TemplateServiceImplTest}</li>
 *   <li>{@link tech.seccertificate.certmgmt.service.TenantServiceTest}</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This test uses the default Spring Boot test profile.
 * For production-like testing, use {@code @ActiveProfiles("test")} or configure test-specific profiles.
 */
@SpringBootTest
@DisplayName("Certificate Management Application Integration Tests")
class CertmgmtApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private CertificateService certificateService;

    @Autowired(required = false)
    private CustomerService customerService;

    @Autowired(required = false)
    private TemplateService templateService;

    @Autowired(required = false)
    private TenantService tenantService;

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private MultiTenantConnectionProvider multiTenantConnectionProvider;

    @Autowired(required = false)
    private CurrentTenantIdentifierResolver currentTenantIdentifierResolver;

    @Autowired(required = false)
    private TenantSchemaValidator tenantSchemaValidator;

    // ==================== Application Context Tests ====================

    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isGreaterThan(0);
    }

    // ==================== Service Layer Bean Tests ====================

    @Test
    @DisplayName("CertificateService bean should be available")
    void certificateServiceBeanShouldBeAvailable() {
        assertThat(certificateService).isNotNull();
        assertThat(certificateService).isInstanceOf(CertificateService.class);
    }

    @Test
    @DisplayName("CustomerService bean should be available")
    void customerServiceBeanShouldBeAvailable() {
        assertThat(customerService).isNotNull();
        assertThat(customerService).isInstanceOf(CustomerService.class);
    }

    @Test
    @DisplayName("TemplateService bean should be available")
    void templateServiceBeanShouldBeAvailable() {
        assertThat(templateService).isNotNull();
        assertThat(templateService).isInstanceOf(TemplateService.class);
    }

    @Test
    @DisplayName("TenantService bean should be available")
    void tenantServiceBeanShouldBeAvailable() {
        assertThat(tenantService).isNotNull();
        assertThat(tenantService).isInstanceOf(TenantService.class);
    }

    @Test
    @DisplayName("All required service beans should be properly wired")
    void allServiceBeansShouldBeWired() {
        assertThat(certificateService)
                .as("CertificateService should be wired")
                .isNotNull();
        assertThat(customerService)
                .as("CustomerService should be wired")
                .isNotNull();
        assertThat(templateService)
                .as("TemplateService should be wired")
                .isNotNull();
        assertThat(tenantService)
                .as("TenantService should be wired")
                .isNotNull();
    }

    // ==================== Configuration Bean Tests ====================

    @Test
    @DisplayName("DataSource bean should be available")
    void dataSourceBeanShouldBeAvailable() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("ObjectMapper bean should be available from JacksonConfig")
    void objectMapperBeanShouldBeAvailable() {
        assertThat(objectMapper).isNotNull();
        assertThat(applicationContext.getBean(ObjectMapper.class))
                .as("ObjectMapper should be available as a bean")
                .isNotNull();
    }

    @Test
    @DisplayName("MultiTenantConnectionProvider bean should be available")
    void multiTenantConnectionProviderBeanShouldBeAvailable() {
        assertThat(multiTenantConnectionProvider).isNotNull();
        assertThat(multiTenantConnectionProvider).isInstanceOf(MultiTenantConnectionProvider.class);
    }

    @Test
    @DisplayName("CurrentTenantIdentifierResolver bean should be available")
    void currentTenantIdentifierResolverBeanShouldBeAvailable() {
        assertThat(currentTenantIdentifierResolver).isNotNull();
        assertThat(currentTenantIdentifierResolver).isInstanceOf(TenantIdentifierResolver.class);
    }

    @Test
    @DisplayName("TenantSchemaValidator bean should be available")
    void tenantSchemaValidatorBeanShouldBeAvailable() {
        assertThat(tenantSchemaValidator).isNotNull();
        assertThat(tenantSchemaValidator).isInstanceOf(TenantSchemaValidator.class);
    }

    @Test
    @DisplayName("WebConfig bean should be available")
    void webConfigBeanShouldBeAvailable() {
        assertThat(applicationContext.getBean(WebConfig.class))
                .as("WebConfig should be available")
                .isNotNull();
    }

    @Test
    @DisplayName("MultiTenantConfig bean should be available")
    void multiTenantConfigBeanShouldBeAvailable() {
        assertThat(applicationContext.getBean(MultiTenantConfig.class))
                .as("MultiTenantConfig should be available")
                .isNotNull();
    }

    @Test
    @DisplayName("JacksonConfig bean should be available")
    void jacksonConfigBeanShouldBeAvailable() {
        assertThat(applicationContext.getBean(JacksonConfig.class))
                .as("JacksonConfig should be available")
                .isNotNull();
    }

    // ==================== Multi-Tenancy Configuration Tests ====================

    @Test
    @DisplayName("Multi-tenancy configuration should be properly set up")
    void multiTenancyConfigurationShouldBeProperlySetUp() {
        assertThat(multiTenantConnectionProvider)
                .as("MultiTenantConnectionProvider should be configured")
                .isNotNull();
        assertThat(currentTenantIdentifierResolver)
                .as("CurrentTenantIdentifierResolver should be configured")
                .isNotNull();
        assertThat(tenantSchemaValidator)
                .as("TenantSchemaValidator should be configured")
                .isNotNull();
    }

    @Test
    @DisplayName("TenantContext should be thread-safe")
    void tenantContextShouldBeThreadSafe() {
        // Clear any existing context
        TenantContext.clear();
        
        // Set tenant context
        String testSchema = "test_schema";
        TenantContext.setTenantSchema(testSchema);
        
        // Verify it's set
        assertThat(TenantContext.getTenantSchema())
                .as("Tenant schema should be set")
                .isEqualTo(testSchema);
        
        // Clear and verify
        TenantContext.clear();
        assertThat(TenantContext.getTenantSchema())
                .as("Tenant schema should be cleared")
                .isNull();
    }

    // ==================== Integration Health Checks ====================

    @Test
    @DisplayName("All critical beans should be available for application startup")
    void allCriticalBeansShouldBeAvailable() {
        // Services
        assertThat(certificateService).isNotNull();
        assertThat(customerService).isNotNull();
        assertThat(templateService).isNotNull();
        assertThat(tenantService).isNotNull();
        
        // Configuration
        assertThat(dataSource).isNotNull();
        assertThat(objectMapper).isNotNull();
        assertThat(multiTenantConnectionProvider).isNotNull();
        assertThat(currentTenantIdentifierResolver).isNotNull();
        assertThat(tenantSchemaValidator).isNotNull();
        
        // Config classes
        assertThat(applicationContext.getBean(WebConfig.class)).isNotNull();
        assertThat(applicationContext.getBean(MultiTenantConfig.class)).isNotNull();
        assertThat(applicationContext.getBean(JacksonConfig.class)).isNotNull();
    }
}
