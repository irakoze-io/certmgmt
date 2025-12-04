package tech.seccertificate.certmgmt.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Multi-Tenant Configuration for Schema Separation (Level 2).
 * 
 * Based on Level 2: Schema Separation Implementation pattern from:
 * https://medium.com/@shahharsh172/building-secure-multi-tenant-applications-with-spring-boot
 * 
 * This configuration centralizes all multi-tenant related beans:
 * - MultiTenantConnectionProvider: Handles schema switching
 * - CurrentTenantIdentifierResolver: Resolves current tenant from context
 * - Hibernate properties: Configures Hibernate for SCHEMA-based multi-tenancy
 *
 * @author Ivan-Beaudry Irakoze
 * @since Dec 4, 2024
 */
@Configuration
@Slf4j
public class MultiTenantConfig {

    private MultiTenantConnectionProvider multiTenantConnectionProvider;
    private CurrentTenantIdentifierResolver currentTenantIdentifierResolver;

    @Autowired
    private DataSource dataSource;

    /**
     * Configures EntityManagerFactory with Hibernate multi-tenancy support.
     * This explicitly configures Hibernate to use SCHEMA-based multi-tenancy
     * with our custom connection provider and tenant identifier resolver.
     * 
     * @param multiTenantConnectionProvider The connection provider that handles schema switching
     * @param currentTenantIdentifierResolver The resolver that identifies the current tenant
     * @return LocalContainerEntityManagerFactoryBean configured for multi-tenancy
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            MultiTenantConnectionProvider multiTenantConnectionProvider,
            CurrentTenantIdentifierResolver currentTenantIdentifierResolver) {
        
        this.multiTenantConnectionProvider = multiTenantConnectionProvider;
        this.currentTenantIdentifierResolver = currentTenantIdentifierResolver;
        
        log.info("Configuring EntityManagerFactory with multi-tenancy");
        log.debug("MultiTenantConnectionProvider: {}", multiTenantConnectionProvider.getClass().getName());
        log.debug("CurrentTenantIdentifierResolver: {}", currentTenantIdentifierResolver.getClass().getName());
        
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("tech.seccertificate.certmgmt.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        // Configure Hibernate properties with multi-tenancy
        Map<String, Object> properties = new HashMap<>();
        
        // Copy standard JPA/Hibernate properties from application.properties
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");
        
        // CRITICAL: Configure multi-tenancy
        properties.put("hibernate.multiTenancy", "SCHEMA");
        properties.put(
                AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, 
                multiTenantConnectionProvider
        );
        properties.put(
                AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, 
                currentTenantIdentifierResolver
        );
        
        em.setJpaPropertyMap(properties);
        
        log.info("EntityManagerFactory configured with multi-tenancy: SCHEMA strategy");
        log.debug("Connection Provider: {}", multiTenantConnectionProvider.getClass().getName());
        log.debug("Tenant Resolver: {}", currentTenantIdentifierResolver.getClass().getName());
        
        return em;
    }
    
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
    
    @PostConstruct
    public void verifyConfiguration() {
        log.debug("MultiTenantConfig PostConstruct - Verifying beans");
        log.debug("MultiTenantConnectionProvider bean: {}", multiTenantConnectionProvider != null ? "Created" : "Missing");
        log.debug("CurrentTenantIdentifierResolver bean: {}", currentTenantIdentifierResolver != null ? "Created" : "Missing");
    }

    /**
     * Creates the tenant identifier resolver bean.
     * This resolver reads the current tenant from TenantContext.
     * 
     * @return CurrentTenantIdentifierResolver instance
     */
    @Bean
    public CurrentTenantIdentifierResolver currentTenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    /**
     * Creates the multi-tenant connection provider bean.
     * This provider handles schema switching for PostgreSQL using search_path.
     * 
     * @param dataSource The data source to use for connections
     * @return MultiTenantConnectionProvider instance
     */
    @Bean
    public MultiTenantConnectionProvider multiTenantConnectionProvider(DataSource dataSource) {
        return new TenantConnectionProvider(dataSource);
    }
}

