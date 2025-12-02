package tech.seccertificate.certmgmt.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.TenantService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * Base class for integration tests.
 * Provides common setup and utilities for testing REST controllers.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

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
    protected org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTenantHeader(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            Long customerId) {
        return builder.header("X-Tenant-Id", customerId.toString());
    }

    /**
     * Add X-Tenant-Schema header to request builder.
     */
    protected org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withTenantSchemaHeader(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
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
