package tech.seccertificate.certmgmt.config;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.function.Consumer;

/**
 * JPA Configuration for multi-tenant schema switching.
 * Configures Hibernate to use SCHEMA-based multi-tenancy.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Oct 5, 2024
 * @Project AuthHub
 */
@Configuration
public class JpaConfig {

    @Bean
    public Consumer<java.util.Map<String, Object>> hibernatePropertiesCustomizer(
            MultiTenantConnectionProvider multiTenantConnectionProvider,
            CurrentTenantIdentifierResolver currentTenantIdentifierResolver) {
        return hibernateProperties -> {
            hibernateProperties.put("hibernate.multiTenancy", "SCHEMA");
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantIdentifierResolver);
        };
    }

    @Bean
    public CurrentTenantIdentifierResolver currentTenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    public MultiTenantConnectionProvider multiTenantConnectionProvider(DataSource dataSource) {
        return new TenantConnectionProvider(dataSource);
    }
}
