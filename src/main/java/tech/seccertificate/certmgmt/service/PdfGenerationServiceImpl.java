package tech.seccertificate.certmgmt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.exception.PdfGenerationException;
import tech.seccertificate.certmgmt.repository.CertificateHashRepository;

import jakarta.annotation.PostConstruct;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of PdfGenerationService.
 * Uses Thymeleaf for HTML template rendering and OpenHTMLtoPDF for PDF conversion.
 *
 * <p>This service supports:
 * <ul>
 *   <li>Thymeleaf template syntax (th:text, th:utext, etc.)</li>
 *   <li>Simple variable replacement ({{variableName}})</li>
 *   <li>CSS style injection</li>
 *   <li>Recipient data from JSON</li>
 *   <li>Certificate metadata and fields</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerationServiceImpl implements PdfGenerationService {

    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final CertificateTemplateResolver certificateTemplateResolver;
    private final QrCodeService qrCodeService;
    private final CertificateHashRepository certificateHashRepository;

    @PostConstruct
    public void configureTemplateEngine() {
        // Configure custom template resolver for certificate templates
        certificateTemplateResolver.setTemplateMode(TemplateMode.HTML);
        certificateTemplateResolver.setCacheable(false); // Don't cache templates
        certificateTemplateResolver.setOrder(1); // High priority
        templateEngine.addTemplateResolver(certificateTemplateResolver);

        log.debug("Configured CertificateTemplateResolver for PDF generation");
    }

    @Override
    public ByteArrayOutputStream generatePdf(TemplateVersion templateVersion, Certificate certificate) {
        log.info("Generating PDF for certificate ID: {}", certificate.getId());

        try {
            var htmlContent = renderHtml(templateVersion, certificate);

            var pdfOutputStream = new ByteArrayOutputStream();

            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(pdfOutputStream);
            builder.useFastMode();
            builder.run();

            log.info("PDF generated successfully for certificate ID: {}, size: {} bytes",
                    certificate.getId(), pdfOutputStream.size());

            return pdfOutputStream;
        } catch (PdfGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate PDF for certificate ID: {}", certificate.getId(), e);
            throw new PdfGenerationException(
                    "Failed to generate PDF for certificate: " + certificate.getId(), e);
        }
    }

    @Override
    public String renderHtml(TemplateVersion templateVersion, Certificate certificate) {
        log.debug("Rendering HTML for certificate ID: {}", certificate.getId());

        try {
            var recipientData = parseRecipientData(certificate.getRecipientData());

            var context = createTemplateContext(certificate, templateVersion, recipientData);

            var htmlTemplate = templateVersion.getHtmlContent();
            if (htmlTemplate == null || htmlTemplate.trim().isEmpty()) {
                throw new PdfGenerationException(
                        "Template HTML content is empty for template version: " + templateVersion.getId());
            }

            var renderedHtml = processTemplate(htmlTemplate, context, certificate.getId());

            if (templateVersion.getCssStyles() != null && !templateVersion.getCssStyles().trim().isEmpty()) {
                renderedHtml = injectCssStyles(renderedHtml, templateVersion.getCssStyles());
            }

            renderedHtml = appendVerificationFooter(renderedHtml, context, certificate);

            log.debug("HTML rendered successfully for certificate ID: {}", certificate.getId());
            return renderedHtml;
        } catch (PdfGenerationException e) {
            // Re-throw PdfGenerationException as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to render HTML for certificate ID: {}", certificate.getId(), e);
            throw new PdfGenerationException(
                    "Failed to render HTML for certificate: " + certificate.getId(), e);
        }
    }

    /**
     * Create Thymeleaf context with all available variables.
     */
    private Context createTemplateContext(Certificate certificate,
                                          TemplateVersion templateVersion,
                                          Map<String, Object> recipientData) {
        var context = new Context();

        // Recipient data (parsed from JSON)
        context.setVariable("recipient", recipientData);

        // Certificate fields
        context.setVariable("certificate", certificate);
        context.setVariable("certificateId", certificate.getId());
        context.setVariable("certificateNumber", certificate.getCertificateNumber());
        context.setVariable("issuedAt", formatDateTime(certificate.getIssuedAt()));
        context.setVariable("expiresAt", formatDateTime(certificate.getExpiresAt()));

        // Template information
        context.setVariable("templateVersion", templateVersion.getVersion());
        if (templateVersion.getTemplate() != null) {
            context.setVariable("templateName", templateVersion.getTemplate().getName());
            context.setVariable("templateCode", templateVersion.getTemplate().getCode());
        }

        // Parse metadata if available
        if (certificate.getMetadata() != null && !certificate.getMetadata().trim().isEmpty()) {
            try {
                var metadata = objectMapper.readValue(
                        certificate.getMetadata(),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
                );
                context.setVariable("metadata", metadata);
            } catch (Exception e) {
                log.warn("Failed to parse certificate metadata: {}", e.getMessage());
                context.setVariable("metadata", new HashMap<String, Object>());
            }
        } else {
            context.setVariable("metadata", new HashMap<String, Object>());
        }

        // Current date/time helpers
        LocalDateTime now = LocalDateTime.now();
        context.setVariable("currentDate", formatDate(now));
        context.setVariable("currentTime", formatTime(now));
        context.setVariable("currentDateTime", formatDateTime(now));

        // Add certificate hash and QR code for verification (if hash exists)
        addVerificationDataToContext(context, certificate);

        return context;
    }

    /**
     * Add certificate hash and QR code verification data to template context.
     * This enables templates to display verification information.
     */
    private void addVerificationDataToContext(Context context, Certificate certificate) {
        try {
            var certificateHashOpt = certificateHashRepository.findByCertificateId(certificate.getId());
            
            if (certificateHashOpt.isPresent()) {
                var certificateHash = certificateHashOpt.get();
                var hash = certificateHash.getHashValue();
                var baseUrl = getBaseUrl();
                var verificationUrl = baseUrl + "/api/certificates/verify/" + hash;
                
                // Add hash to context
                context.setVariable("certificateHash", hash);
                context.setVariable("verificationUrl", verificationUrl);
                
                // Generate QR code image as base64 data URI
                try {
                    var qrCodeDataUri = generateQrCodeDataUri(verificationUrl);
                    context.setVariable("qrCodeImage", qrCodeDataUri);
                    log.debug("Added QR code and hash to template context for certificate {}", certificate.getId());
                } catch (Exception e) {
                    log.warn("Failed to generate QR code for certificate {}, continuing without QR code", 
                            certificate.getId(), e);
                    // Continue without QR code - hash will still be available
                }
            } else {
                log.debug("No hash found for certificate {}, verification data not added", certificate.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to add verification data to context for certificate {}: {}", 
                    certificate.getId(), e.getMessage());
            // Continue without verification data - certificate will still be generated
        }
    }

    /**
     * Generate QR code as base64 data URI for embedding in HTML.
     */
    private String generateQrCodeDataUri(String verificationUrl) {
        try {
            var qrCodeImage = qrCodeService.generateQrCode(verificationUrl, 200, 200);
            var baos = new ByteArrayOutputStream();
            ImageIO.write(qrCodeImage, "PNG", baos);
            var base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            log.error("Failed to generate QR code data URI for URL: {}", verificationUrl, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Get base URL for generating verification URLs.
     * Uses configuration property or defaults to localhost:8080.
     */
    private String getBaseUrl() {
        // Try to get from environment or configuration
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = System.getProperty("app.base-url");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }
        // Remove trailing slash if present
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Process HTML template with Thymeleaf or simple variable replacement.
     *
     * <p>For Thymeleaf templates, we register the template content in our cache
     * and then process it using Thymeleaf. For simple templates, we use basic
     * variable replacement.
     */
    private String processTemplate(String htmlTemplate, Context context, UUID certificateId) {
        // Check if template contains Thymeleaf syntax
        boolean hasThymeleafSyntax = htmlTemplate.contains("th:")
                || htmlTemplate.contains("${")
                || htmlTemplate.contains("#{")
                || htmlTemplate.contains("*{");

        if (hasThymeleafSyntax) {
            // Use Thymeleaf to process the template
            try {
                // Use a unique template name
                String templateName = "certificate-template-" + certificateId;

                // Register template content with resolver
                certificateTemplateResolver.registerTemplate(templateName, htmlTemplate);

                try {
                    // Process the template
                    StringWriter writer = new StringWriter();
                    templateEngine.process(templateName, context, writer);
                    return writer.toString();
                } finally {
                    // Clean up template from cache
                    certificateTemplateResolver.removeTemplate(templateName);
                }
            } catch (Exception e) {
                log.warn("Thymeleaf processing failed, falling back to simple replacement: {}", e.getMessage());
                // Fall back to simple variable replacement
                return replaceSimpleVariables(htmlTemplate, context);
            }
        } else {
            // Use simple variable replacement for non-Thymeleaf templates
            return replaceSimpleVariables(htmlTemplate, context);
        }
    }

    /**
     * Replace simple variables in HTML template (for non-Thymeleaf templates).
     * Supports {{variableName}} and {{recipient.fieldName}} syntax.
     */
    private String replaceSimpleVariables(@NotNull String html, Context context) {

        Map<String, Object> variables = new HashMap<>();
        // for (var varName : context.getVariableNames()) {
        //     variables.put(varName, context.getVariable(varName));
        // }
        context.getVariableNames()
                .forEach(v -> variables.put(v, context.getVariable(v)));

        var result = html;

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            var varName = entry.getKey();
            var value = entry.getValue();
            var strValue = value != null ? value.toString() : "";

            result = result.replace("{{" + varName + "}}", strValue);
        }

        if (variables.containsKey("recipient") && variables.get("recipient") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> recipient = (Map<String, Object>) variables.get("recipient");
            for (Map.Entry<String, Object> entry : recipient.entrySet()) {
                String placeholder = "{{recipient." + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }

        if (variables.containsKey("metadata") && variables.get("metadata") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) variables.get("metadata");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String placeholder = "{{metadata." + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }

        return result;
    }

    /**
     * Parse recipient data JSON string into a Map.
     */
    private Map<String, Object> parseRecipientData(String recipientDataJson) {
        if (recipientDataJson == null || recipientDataJson.trim().isEmpty()) {
            log.warn("Recipient data is null or empty, using empty map");
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(
                    recipientDataJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
        } catch (Exception e) {
            log.warn("Failed to parse recipient data JSON, using empty map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Format LocalDateTime to readable date string (yyyy-MM-dd).
     */
    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * Format LocalDateTime to readable time string (HH:mm:ss).
     */
    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * Format LocalDateTime to readable date-time string (yyyy-MM-dd HH:mm:ss).
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Inject CSS styles into HTML content.
     * Adds a <style> tag in the <head> section, or creates one if it doesn't exist.
     * Author: aider AI
     */
    private String injectCssStyles(@NotNull String html, String cssStyles) {
        if (cssStyles == null || cssStyles.trim().isEmpty()) {
            return html;
        }

        var trimmedCss = cssStyles.trim();

        if (html.contains("<head>") && html.contains("</head>")) {
            if (html.contains("<style>") && html.contains("</style>")) {
                return html.replace("</style>", "\n" + trimmedCss + "\n</style>");
            } else {
                return html.replace("</head>", "<style>\n" + trimmedCss + "\n</style></head>");
            }
        } else if (html.contains("<html>")) {
            return html.replace("<html>", "<html><head><style>\n" + trimmedCss + "\n</style></head>");
        } else {
            return "<style>\n" + trimmedCss + "\n</style>\n" + html;
        }
    }

    /**
     * Automatically append verification footer to all certificates.
     * The footer includes QR code and verification URL for certificate verification.
     * Only appends footer if the certificate has verification data (hash).
     *
     * @param html The rendered HTML content
     * @param context The Thymeleaf context containing template variables
     * @param certificate The certificate entity
     * @return HTML with verification footer appended
     */
    private String appendVerificationFooter(@NotNull String html, Context context, Certificate certificate) {
        try {
            // Check if verification data is available in context
            Object verificationUrlObj = context.getVariable("verificationUrl");
            Object qrCodeImageObj = context.getVariable("qrCodeImage");

            if (verificationUrlObj == null || qrCodeImageObj == null) {
                log.debug("No verification data available for certificate {}, skipping footer", certificate.getId());
                return html;
            }

            var verificationUrl = verificationUrlObj.toString();
            var qrCodeImage = qrCodeImageObj.toString();

            var footerHtml = generateVerificationFooterHtml(verificationUrl, qrCodeImage);

            if (html.contains("</body>")) {
                return html.replace("</body>", footerHtml + "\n</body>");
            } else if (html.contains("</html>")) {
                return html.replace("</html>", footerHtml + "\n</html>");
            } else {
                return html + "\n" + footerHtml;
            }
        } catch (Exception e) {
            log.warn("Failed to append verification footer for certificate {}, continuing without footer: {}", 
                    certificate.getId(), e.getMessage());

            // Let the application continue without footer - certificate will still be generated
            return html;
        }
    }

    /**
     * Generate HTML for verification footer.
     * Includes QR code image and verification URL text.
     *
     * @param verificationUrl The verification URL
     * @param qrCodeImage Base64 data URI of QR code image
     * @return HTML string for verification footer
     */
    private String generateVerificationFooterHtml(String verificationUrl, String qrCodeImage) {
        return """
                <div style="margin-top: 40px; padding-top: 20px; border-top: 2px solid #e0e0e0; text-align: center; font-family: Arial, sans-serif; font-size: 12px; color: #666;">
                    <div style="margin-bottom: 15px;">
                        <span style="font-weight: bold; color: #333;">Scan to Verify</span>
                    </div>
                    <div style="margin-bottom: 15px;">
                        <img src="%s" alt="QR Code" style="width: 150px; height: 150px; display: block; margin: 0 auto;" />
                    </div>
                    <div style="margin-top: 10px; color: #555;">
                        <span>or visit </span>
                        <a href="%s" style="color: #0066cc; text-decoration: none; word-break: break-all;">%s</a>
                    </div>
                </div>
                """.formatted(qrCodeImage, verificationUrl, verificationUrl);
    }
}
