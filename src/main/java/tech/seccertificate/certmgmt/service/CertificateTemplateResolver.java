package tech.seccertificate.certmgmt.service;

import org.springframework.stereotype.Component;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolution;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom template resolver for certificate templates stored in the database.
 * Allows dynamic registration of HTML templates for Thymeleaf processing.
 */
@Component
public class CertificateTemplateResolver extends AbstractConfigurableTemplateResolver {

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public CertificateTemplateResolver() {
        setResolvablePatterns(Set.of("certificate-template-*"));
    }

    /**
     * Register a template with the given name and content.
     *
     * @param templateName The template name
     * @param templateContent The template HTML content
     */
    public void registerTemplate(String templateName, String templateContent) {
        templateCache.put(templateName, templateContent);
    }

    /**
     * Remove a template from the cache.
     *
     * @param templateName The template name
     */
    public void removeTemplate(String templateName) {
        templateCache.remove(templateName);
    }

    @Override
    protected ITemplateResource computeTemplateResource(
            IEngineConfiguration configuration,
            String ownerTemplate, String template, String resourceName,
            String characterEncoding, Map<String, Object> templateResolutionAttributes) {

        String templateContent = templateCache.get(template);
        if (templateContent == null) {
            return null;
        }

        return new StringTemplateResource(templateContent);
    }
}
