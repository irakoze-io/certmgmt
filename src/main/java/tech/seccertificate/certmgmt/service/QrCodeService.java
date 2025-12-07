package tech.seccertificate.certmgmt.service;

import java.awt.image.BufferedImage;

/**
 * Service interface for QR code generation.
 * Generates QR codes containing verification URLs for certificates.
 */
public interface QrCodeService {

    /**
     * Generate QR code image containing the verification URL.
     * 
     * @param verificationUrl The URL to encode in the QR code
     * @param width The width of the QR code image in pixels
     * @param height The height of the QR code image in pixels
     * @return BufferedImage containing the QR code
     */
    BufferedImage generateQrCode(String verificationUrl, int width, int height);

    /**
     * Generate QR code image with default size (300x300 pixels).
     * 
     * @param verificationUrl The URL to encode in the QR code
     * @return BufferedImage containing the QR code
     */
    BufferedImage generateQrCode(String verificationUrl);

    /**
     * Generate QR code as Base64-encoded PNG image string.
     * 
     * @param verificationUrl The URL to encode in the QR code
     * @return Base64-encoded PNG image string (data URI format)
     */
    String generateQrCodeAsBase64(String verificationUrl);
}
