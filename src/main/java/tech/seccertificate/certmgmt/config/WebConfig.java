package tech.seccertificate.certmgmt.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web configuration for multi-tenant support.
 *
 * Based on Level 2: Schema Separation Implementation pattern from:
 * https://medium.com/@shahharsh172/building-secure-multi-tenant-applications-with-spring-boot
 *
 * Registers TenantResolutionFilter as a Servlet Filter (runs before Spring MVC).
 * This provides earlier tenant validation and better security compared to interceptors.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Dec 4, 2024
 */
@Configuration
public class WebConfig {

    /**
     * Register TenantResolutionFilter as a Servlet Filter.
     * Filters run before Spring MVC, providing earlier tenant validation.
     *
     * @param tenantResolutionFilter The tenant resolution filter
     * @return FilterRegistrationBean configured with the filter
     */
    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(
            TenantResolutionFilter tenantResolutionFilter) {
        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tenantResolutionFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // High priority - run early
        registration.setName("tenantResolutionFilter");
        return registration;
    }
}
