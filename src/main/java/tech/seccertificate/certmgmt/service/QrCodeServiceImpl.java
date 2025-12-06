package tech.seccertificate.certmgmt.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of QrCodeService using ZXing library.
 * Generates QR codes containing verification URLs for certificates.
 */
@Slf4j
@Service
public class QrCodeServiceImpl implements QrCodeService {

    private static final int DEFAULT_SIZE = 300;
    private static final int QUIET_ZONE = 4;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public BufferedImage generateQrCode(String verificationUrl, int width, int height) {
        if (verificationUrl == null || verificationUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification URL cannot be null or empty");
        }

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, QUIET_ZONE);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(verificationUrl, BarcodeFormat.QR_CODE, width, height, hints);

            BufferedImage qrImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            qrImage.createGraphics();

            Graphics2D graphics = (Graphics2D) qrImage.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (bitMatrix.get(x, y)) {
                        graphics.fillRect(x, y, 1, 1);
                    }
                }
            }

            graphics.dispose();
            log.debug("Generated QR code for URL: {} (size: {}x{})", verificationUrl, width, height);
            return qrImage;

        } catch (WriterException e) {
            log.error("Failed to generate QR code for URL: {}", verificationUrl, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    @Override
    public BufferedImage generateQrCode(String verificationUrl) {
        return generateQrCode(verificationUrl, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    @Override
    public String generateQrCodeAsBase64(String verificationUrl) {
        BufferedImage qrImage = generateQrCode(verificationUrl);
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            return "data:image/png;base64," + base64Image;
        } catch (IOException e) {
            log.error("Failed to convert QR code to Base64 for URL: {}", verificationUrl, e);
            throw new RuntimeException("Failed to convert QR code to Base64", e);
        }
    }
}
