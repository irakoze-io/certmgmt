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
     * Render HTML from template with recipient data.
     * This method processes the template HTML content, applies CSS styles,
     * and replaces template variables with recipient data.
     *
     * @param templateVersion The template version containing HTML content
     * @param certificate The certificate containing recipient data
     * @return Rendered HTML string ready for PDF conversion
     */
    String renderHtml(TemplateVersion templateVersion, Certificate certificate);
}
