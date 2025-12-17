package tech.seccertificate.certmgmt.service;

import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.io.ByteArrayOutputStream;

/**
 * Service interface for PDF generation from certificate templates.
 * Handles HTML template rendering with recipient data and conversion to PDF.
 */
public interface PdfGenerationService {

    /**
     * Generate PDF from a certificate template version and recipient data.
     *
     * @param templateVersion The template version containing HTML content and CSS
     * @param certificate The certificate containing recipient data and metadata
     * @return ByteArrayOutputStream containing the generated PDF bytes
     */
    ByteArrayOutputStream generatePdf(TemplateVersion templateVersion, Certificate certificate);

    /**
     * Generate PDF from a certificate template version and recipient data,
     * with option to exclude verification footer.
     * Used for hash calculation (without footer) and final storage (with footer).
     *
     * @param templateVersion The template version containing HTML content and CSS
     * @param certificate The certificate containing recipient data and metadata
     * @param includeVerificationFooter Whether to include the auto-appended verification footer
     * @return ByteArrayOutputStream containing the generated PDF bytes
     */
    ByteArrayOutputStream generatePdf(TemplateVersion templateVersion, Certificate certificate, boolean includeVerificationFooter);

    /**
     * Render HTML from template with recipient data.
     * This method processes the template HTML content, applies CSS styles,
     * and replaces template variables with recipient data.
     *
     * @param templateVersion The template version containing HTML content
     * @param certificate The certificate containing recipient data
     * @return Rendered HTML string ready for PDF conversion
     */
    String renderHtml(TemplateVersion templateVersion, Certificate certificate);

    /**
     * Render HTML from template with recipient data, with option to exclude verification footer.
     * Performance optimization: Use this to cache rendered HTML between passes.
     *
     * @param templateVersion The template version containing HTML content
     * @param certificate The certificate containing recipient data
     * @param includeVerificationFooter Whether to include the auto-appended verification footer
     * @return Rendered HTML string ready for PDF conversion
     */
    String renderHtml(TemplateVersion templateVersion, Certificate certificate, boolean includeVerificationFooter);

    /**
     * Render HTML from template with recipient data, with option to exclude verification variables
     * from the template context (verificationUrl, qrCodeImage, certificateHash).
     *
     * <p>This is useful for public verification pages where we want to display the certificate
     * content without showing QR codes or verification links.
     */
    String renderHtml(TemplateVersion templateVersion,
                      Certificate certificate,
                      boolean includeVerificationFooter,
                      boolean includeVerificationData);

    /**
     * Convert already-rendered HTML to PDF.
     * Performance optimization: Allows reuse of rendered HTML without re-processing template.
     *
     * @param html The rendered HTML content
     * @param templateVersion The template version (for PDF settings)
     * @return ByteArrayOutputStream containing the generated PDF bytes
     */
    ByteArrayOutputStream convertHtmlToPdf(String html, TemplateVersion templateVersion);

    /**
     * Append verification footer to already-rendered HTML.
     * Performance optimization: Fast string operation to add footer to cached HTML.
     *
     * @param html The rendered HTML content without footer
     * @param certificate The certificate containing verification data
     * @return HTML with verification footer appended
     */
    String appendVerificationFooterToHtml(String html, Certificate certificate);
}
