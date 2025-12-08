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
        log.info("Generating PDF for certificate ID: {}, Template Version ID: {}",
                certificate.getId(), templateVersion.getId());

        try {
            var htmlContent = renderHtml(templateVersion, certificate);

            var pdfOutputStream = new ByteArrayOutputStream();

            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(pdfOutputStream);

            // Apply template version settings to PDF builder
            applyPdfSettings(builder, templateVersion);

            builder.useFastMode();
            builder.run();

            log.info("PDF generated successfully for certificate ID: {}, size: {} bytes",
                    certificate.getId(), pdfOutputStream.size());

            return pdfOutputStream;
        } catch (PdfGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate PDF for certificate ID: {}, Template Version ID: {}",
                    certificate.getId(), templateVersion.getId(), e);
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

            // Inject CSS styles from template version
            if (templateVersion.getCssStyles() != null && !templateVersion.getCssStyles().trim().isEmpty()) {
                renderedHtml = injectCssStyles(renderedHtml, templateVersion.getCssStyles());
            }

            // Inject PDF page settings as CSS @page rules
            if (templateVersion.getSettings() != null && !templateVersion.getSettings().trim().isEmpty()) {
                renderedHtml = injectPageSettings(renderedHtml, templateVersion.getSettings());
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
     * Ensures proper HTML structure with meta charset tag.
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
            // HTML tag exists but no head, create head with meta charset and style
            return html.replace("<html>", "<html><head><meta charset=\"UTF-8\" /><style>\n" + trimmedCss + "\n</style></head>");
        } else {
            // No HTML structure, wrap with proper HTML structure
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\" /><style>\n" + trimmedCss + "\n</style></head><body>\n" + html + "\n</body></html>";
        }
    }

    /**
     * Inject PDF page settings as CSS @page rules into HTML content.
     * Converts template version settings (pageSize, orientation, margins) to CSS.
     *
     * @param html         The HTML content
     * @param settingsJson The settings JSON string from template version
     * @return HTML with @page CSS rules injected
     */
    @SuppressWarnings("unchecked")
    private String injectPageSettings(@NotNull String html, String settingsJson) {
        if (settingsJson == null || settingsJson.trim().isEmpty()) {
            return html;
        }

        try {
            Map<String, Object> settings = objectMapper.readValue(
                    settingsJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );

            if (settings == null || settings.isEmpty()) {
                return html;
            }

            StringBuilder pageCss = new StringBuilder();
            pageCss.append("@page {\n");

            // Page size
            if (settings.containsKey("pageSize")) {
                String pageSize = getStringValue(settings, "pageSize", "A4");
                pageCss.append("  size: ").append(pageSize).append(";\n");
            } else if (settings.containsKey("pageWidth") && settings.containsKey("pageHeight")) {
                double width = getDoubleValue(settings, "pageWidth", 210.0);
                double height = getDoubleValue(settings, "pageHeight", 297.0);
                String unit = getStringValue(settings, "unit", "mm");
                pageCss.append(String.format("  size: %.2f%s %.2f%s;\n", width, unit, height, unit));
            }

            // Orientation
            if (settings.containsKey("orientation")) {
                String orientation = getStringValue(settings, "orientation", "portrait");
                if ("landscape".equalsIgnoreCase(orientation)) {
                    // If size is already set, we need to swap dimensions or use landscape keyword
                    // For simplicity, we'll add it as a separate property
                    pageCss.append("  size: landscape;\n");
                }
            }

            // Margins
            if (settings.containsKey("margins")) {
                Object marginsObj = settings.get("margins");
                if (marginsObj instanceof Map) {
                    Map<String, Object> margins = (Map<String, Object>) marginsObj;
                    String marginCss = buildMarginCss(margins);
                    if (!marginCss.isEmpty()) {
                        pageCss.append("  ").append(marginCss).append("\n");
                    }
                } else if (marginsObj instanceof String) {
                    // Single margin value
                    pageCss.append("  margin: ").append(marginsObj).append(";\n");
                }
            }

            pageCss.append("}\n");

            // Inject @page CSS into HTML (similar to injectCssStyles but for @page rules)
            String pageCssString = pageCss.toString();
            return injectPageCss(html, pageCssString);

        } catch (Exception e) {
            log.warn("Failed to inject page settings into HTML: {}", e.getMessage());
            return html; // Return original HTML if settings parsing fails
        }
    }

    /**
     * Build CSS margin string from margins map.
     */
    private String buildMarginCss(Map<String, Object> margins) {
        StringBuilder marginCss = new StringBuilder();

        if (margins.containsKey("top") || margins.containsKey("right")
                || margins.containsKey("bottom") || margins.containsKey("left")) {
            String top = getStringValue(margins, "top", "0");
            String right = getStringValue(margins, "right", "0");
            String bottom = getStringValue(margins, "bottom", "0");
            String left = getStringValue(margins, "left", "0");

            marginCss.append("margin: ").append(top).append(" ")
                    .append(right).append(" ").append(bottom).append(" ").append(left).append(";");
        } else if (margins.containsKey("all")) {
            marginCss.append("margin: ").append(getStringValue(margins, "all", "0")).append(";");
        }

        return marginCss.toString();
    }

    /**
     * Inject @page CSS rules into HTML content.
     * Adds @page rules to the <style> tag in the <head> section.
     */
    private String injectPageCss(@NotNull String html, String pageCss) {
        if (pageCss == null || pageCss.trim().isEmpty()) {
            return html;
        }

        var trimmedCss = pageCss.trim();

        // Check if @page already exists
        if (html.contains("@page")) {
            // Append to existing @page or add before closing </style>
            if (html.contains("</style>")) {
                return html.replace("</style>", "\n" + trimmedCss + "\n</style>");
            }
        }

        // Inject into existing style tag or create new one
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

    /**
     * Apply PDF settings from template version to PDF builder.
     * Supports common PDF settings like pageSize, orientation, margins, etc.
     *
     * @param builder The PDF renderer builder
     * @param templateVersion The template version containing settings
     */
    @SuppressWarnings("unchecked")
    private void applyPdfSettings(PdfRendererBuilder builder, TemplateVersion templateVersion) {
        if (templateVersion.getSettings() == null || templateVersion.getSettings().trim().isEmpty()) {
            log.debug("No settings found in template version, using defaults");
            return;
        }

        try {
            Map<String, Object> settings = objectMapper.readValue(
                    templateVersion.getSettings(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );

            if (settings == null || settings.isEmpty()) {
                return;
            }

            // Apply page size (A4, Letter, Legal, etc.)
            if (settings.containsKey("pageSize")) {
                String pageSize = getStringValue(settings, "pageSize", "A4");
                applyPageSize(builder, pageSize);
                log.debug("Applied page size: {}", pageSize);
            }

            // Apply orientation (portrait, landscape)
            if (settings.containsKey("orientation")) {
                String orientation = getStringValue(settings, "orientation", "portrait");
                applyOrientation(builder, orientation);
                log.debug("Applied orientation: {}", orientation);
            }

            // Apply margins (top, bottom, left, right in mm or pixels)
            if (settings.containsKey("margins")) {
                Object marginsObj = settings.get("margins");
                if (marginsObj instanceof Map) {
                    Map<String, Object> margins = (Map<String, Object>) marginsObj;
                    applyMargins(builder, margins);
                    log.debug("Applied margins: {}", margins);
                }
            }

            // Apply DPI (dots per inch)
            // Note: DPI is typically handled via CSS and OpenHTMLtoPDF defaults
            // Custom DPI settings can be applied via CSS @page rules if needed
            if (settings.containsKey("dpi")) {
                int dpi = getIntValue(settings, "dpi", 96);
                log.debug("DPI setting: {} (applied via CSS)", dpi);
                // DPI is handled by the PDF renderer automatically
            }

            // Apply page width and height (in mm or pixels)
            if (settings.containsKey("pageWidth") && settings.containsKey("pageHeight")) {
                double width = getDoubleValue(settings, "pageWidth", 210.0); // A4 width in mm
                double height = getDoubleValue(settings, "pageHeight", 297.0); // A4 height in mm
                String unit = getStringValue(settings, "unit", "mm");
                applyPageDimensions(builder, width, height, unit);
                log.debug("Applied page dimensions: {}x{} {}", width, height, unit);
            }

        } catch (Exception e) {
            log.warn("Failed to parse or apply PDF settings from template version: {}", e.getMessage());
            // Continue with default settings - don't fail PDF generation
        }
    }

    /**
     * Apply page size to PDF builder.
     */
    private void applyPageSize(PdfRendererBuilder builder, String pageSize) {
        // OpenHTMLtoPDF uses CSS @page rules, but we can also set it via builder
        // For now, we'll inject it into the HTML via CSS
        // The actual page size will be handled by CSS @page rules in the HTML
        log.debug("Page size setting: {} (applied via CSS)", pageSize);
    }

    /**
     * Apply orientation to PDF builder.
     */
    private void applyOrientation(PdfRendererBuilder builder, String orientation) {
        // Orientation is typically handled via CSS @page rules
        // OpenHTMLtoPDF respects CSS @page { size: landscape; } or { size: portrait; }
        log.debug("Orientation setting: {} (applied via CSS)", orientation);
    }

    /**
     * Apply margins to PDF builder.
     */
    private void applyMargins(PdfRendererBuilder builder, Map<String, Object> margins) {
        // Margins are typically handled via CSS @page rules
        // OpenHTMLtoPDF respects CSS @page { margin: ...; }
        log.debug("Margins setting applied via CSS");
    }

    /**
     * Apply custom page dimensions to PDF builder.
     */
    private void applyPageDimensions(PdfRendererBuilder builder, double width, double height, String unit) {
        // Convert to points (OpenHTMLtoPDF uses points)
        double widthPoints = convertToPoints(width, unit);
        double heightPoints = convertToPoints(height, unit);

        // OpenHTMLtoPDF doesn't directly support custom page sizes via builder API
        // This would need to be handled via CSS @page rules in the HTML
        log.debug("Page dimensions: {}x{} points (applied via CSS)", widthPoints, heightPoints);
    }

    /**
     * Convert a measurement to points (1 inch = 72 points).
     */
    private double convertToPoints(double value, String unit) {
        return switch (unit.toLowerCase()) {
            case "mm" -> value * 72.0 / 25.4; // mm to points
            case "cm" -> value * 72.0 / 2.54; // cm to points
            case "in", "inch" -> value * 72.0; // inches to points
            case "px", "pixel" -> value * 72.0 / 96.0; // pixels to points (assuming 96 DPI)
            default -> value; // Assume already in points
        };
    }

    /**
     * Helper method to get string value from map with default.
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * Helper method to get int value from map with default.
     */
    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Helper method to get double value from map with default.
     */
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
