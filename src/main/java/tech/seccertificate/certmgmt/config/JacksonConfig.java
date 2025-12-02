package tech.seccertificate.certmgmt.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson JSON configuration.
 * Explicitly provides ObjectMapper bean for dependency injection.
 * 
 * <p>Spring Boot auto-configures ObjectMapper when spring-boot-starter-webmvc
 * is included. This configuration explicitly exposes it as a bean to ensure
 * it's available for injection in controllers and other components.
 * 
 * <p>Note: Spring Boot's auto-configuration already provides ObjectMapper,
 * but this explicit bean definition ensures it's available and allows
 * customization if needed.
 */
@Configuration
public class JacksonConfig {

    /**
     * Provides ObjectMapper bean for JSON serialization/deserialization.
     * 
     * <p>Uses JsonMapper.builder() (non-deprecated API) to create an ObjectMapper
     * with sensible defaults including:
     * <ul>
     *   <li>JavaTimeModule support (via findAndAddModules())</li>
     *   <li>Camel case property naming</li>
     *   <li>Fail on unknown properties disabled</li>
     * </ul>
     * 
     * @return ObjectMapper bean
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .build()
                .configure(DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
