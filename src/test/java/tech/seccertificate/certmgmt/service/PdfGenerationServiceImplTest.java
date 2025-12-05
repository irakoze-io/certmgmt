package tech.seccertificate.certmgmt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import tech.seccertificate.certmgmt.entity.Certificate;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.exception.PdfGenerationException;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for PdfGenerationServiceImpl.
 * Tests PDF generation with various template types:
 * - Simple variable replacement templates
 * - Thymeleaf templates
 * - Templates with CSS styling
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class PdfGenerationServiceImplTest {

    @Autowired
    private PdfGenerationService pdfGenerationService;

    @Autowired
    private ObjectMapper objectMapper;

    private Template testTemplate;
    private TemplateVersion simpleTemplateVersion;
    private TemplateVersion thymeleafTemplateVersion;
    private TemplateVersion styledTemplateVersion;
    private Certificate testCertificate;

    @BeforeEach
    void setUp() throws IOException {
        // Create a test template
        testTemplate = Template.builder()
                .id(1L)
                .customerId(100L)
                .name("Test Certificate Template")
                .code("TEST-CERT-001")
                .description("Test template for PDF generation")
                .currentVersion(1)
                .metadata("{}")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Load simple template HTML
        String simpleHtml = loadTemplateFromResource("templates/simple-certificate-template.html");
        simpleTemplateVersion = TemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(testTemplate)
                .version(1)
                .htmlContent(simpleHtml)
                .fieldSchema("{\"fields\": [\"name\", \"courseName\"]}")
                .cssStyles(null)
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .createdBy(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();

        // Load Thymeleaf template HTML
        String thymeleafHtml = loadTemplateFromResource("templates/thymeleaf-certificate-template.html");
        thymeleafTemplateVersion = TemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(testTemplate)
                .version(2)
                .htmlContent(thymeleafHtml)
                .fieldSchema("{\"fields\": [\"name\", \"courseName\", \"score\"]}")
                .cssStyles(null)
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .createdBy(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();

        // Load styled template HTML with CSS
        String styledHtml = loadTemplateFromResource("templates/styled-certificate-template.html");
        String cssStyles = loadTemplateFromResource("templates/certificate-styles.css");
        styledTemplateVersion = TemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(testTemplate)
                .version(3)
                .htmlContent(styledHtml)
                .fieldSchema("{\"fields\": [\"name\", \"courseName\"]}")
                .cssStyles(cssStyles)
                .status(TemplateVersion.TemplateVersionStatus.PUBLISHED)
                .createdBy(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();

        // Create test certificate with recipient data
        Map<String, Object> recipientData = new HashMap<>();
        recipientData.put("name", "John Doe");
        recipientData.put("courseName", "Advanced Java Programming");
        recipientData.put("score", 95);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("instructor", "Dr. Jane Smith");
        metadata.put("department", "Computer Science");

        testCertificate = Certificate.builder()
                .id(UUID.randomUUID())
                .customerId(100L)
                .templateVersionId(simpleTemplateVersion.getId())
                .certificateNumber("CERT-2025-001")
                .recipientData(objectMapper.writeValueAsString(recipientData))
                .metadata(objectMapper.writeValueAsString(metadata))
                .status(Certificate.CertificateStatus.PENDING)
                .issuedAt(LocalDateTime.of(2025, 12, 5, 10, 30))
                .expiresAt(LocalDateTime.of(2026, 12, 5, 10, 30))
                .issuedBy(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully generate PDF with simple template")
    void testGeneratePdfWithSimpleTemplate() throws IOException {
        // When
        ByteArrayOutputStream pdfOutput = pdfGenerationService.generatePdf(
                simpleTemplateVersion,
                testCertificate
        );

        // Then
        assertNotNull(pdfOutput);
        assertTrue(pdfOutput.size() > 0, "PDF output should not be empty");

        // Verify PDF header (PDF files start with %PDF-)
        byte[] pdfBytes = pdfOutput.toByteArray();
        String pdfHeader = new String(pdfBytes, 0, Math.min(8, pdfBytes.length));
        assertTrue(pdfHeader.startsWith("%PDF-"), "Output should be a valid PDF file");

        // Save PDF for manual inspection (optional)
        savePdfToFile(pdfBytes, "test-simple-certificate.pdf");

        System.out.println("✓ Simple template PDF generated successfully: " + pdfBytes.length + " bytes");
    }

    @Test
    @DisplayName("Should successfully generate PDF with Thymeleaf template")
    void testGeneratePdfWithThymeleafTemplate() throws IOException {
        // When
        ByteArrayOutputStream pdfOutput = pdfGenerationService.generatePdf(
                thymeleafTemplateVersion,
                testCertificate
        );

        // Then
        assertNotNull(pdfOutput);
        assertTrue(pdfOutput.size() > 0, "PDF output should not be empty");

        // Verify PDF header
        byte[] pdfBytes = pdfOutput.toByteArray();
        String pdfHeader = new String(pdfBytes, 0, Math.min(8, pdfBytes.length));
        assertTrue(pdfHeader.startsWith("%PDF-"), "Output should be a valid PDF file");

        // Save PDF for manual inspection
        savePdfToFile(pdfBytes, "test-thymeleaf-certificate.pdf");

        System.out.println("✓ Thymeleaf template PDF generated successfully: " + pdfBytes.length + " bytes");
    }

    @Test
    @DisplayName("Should successfully generate PDF with styled template")
    void testGeneratePdfWithStyledTemplate() throws IOException {
        // When
        ByteArrayOutputStream pdfOutput = pdfGenerationService.generatePdf(
                styledTemplateVersion,
                testCertificate
        );

        // Then
        assertNotNull(pdfOutput);
        assertTrue(pdfOutput.size() > 0, "PDF output should not be empty");

        // Verify PDF header
        byte[] pdfBytes = pdfOutput.toByteArray();
        String pdfHeader = new String(pdfBytes, 0, Math.min(8, pdfBytes.length));
        assertTrue(pdfHeader.startsWith("%PDF-"), "Output should be a valid PDF file");

        // Save PDF for manual inspection
        savePdfToFile(pdfBytes, "test-styled-certificate.pdf");

        System.out.println("✓ Styled template PDF generated successfully: " + pdfBytes.length + " bytes");
    }

    @Test
    @DisplayName("Should render HTML correctly with simple template")
    void testRenderHtmlWithSimpleTemplate() {
        // When
        String renderedHtml = pdfGenerationService.renderHtml(
                simpleTemplateVersion,
                testCertificate
        );

        // Then
        assertNotNull(renderedHtml);
        assertFalse(renderedHtml.isEmpty(), "Rendered HTML should not be empty");

        // Verify that variables were replaced
        assertTrue(renderedHtml.contains("John Doe"),
                "HTML should contain recipient name");
        assertTrue(renderedHtml.contains("Advanced Java Programming"),
                "HTML should contain course name");
        assertTrue(renderedHtml.contains("CERT-2025-001"),
                "HTML should contain certificate number");

        System.out.println("✓ Simple template HTML rendered successfully");
        System.out.println("Rendered HTML preview:\n" + renderedHtml.substring(0, Math.min(500, renderedHtml.length())));
    }

    @Test
    @DisplayName("Should render HTML correctly with Thymeleaf template")
    void testRenderHtmlWithThymeleafTemplate() {
        // When
        String renderedHtml = pdfGenerationService.renderHtml(
                thymeleafTemplateVersion,
                testCertificate
        );

        // Then
        assertNotNull(renderedHtml);
        assertFalse(renderedHtml.isEmpty(), "Rendered HTML should not be empty");

        // Verify that Thymeleaf expressions were processed
        assertTrue(renderedHtml.contains("John Doe"),
                "HTML should contain recipient name");
        assertTrue(renderedHtml.contains("Advanced Java Programming"),
                "HTML should contain course name");
        assertTrue(renderedHtml.contains("95"),
                "HTML should contain score");
        assertFalse(renderedHtml.contains("th:text"),
                "Thymeleaf attributes should be processed and removed");

        System.out.println("✓ Thymeleaf template HTML rendered successfully");
    }

    @Test
    @DisplayName("Should inject CSS styles into HTML")
    void testCssStyleInjection() {
        // When
        String renderedHtml = pdfGenerationService.renderHtml(
                styledTemplateVersion,
                testCertificate
        );

        // Then
        assertNotNull(renderedHtml);
        assertTrue(renderedHtml.contains("<style>"),
                "Rendered HTML should contain style tag");
        assertTrue(renderedHtml.contains(".certificate-container"),
                "Rendered HTML should contain CSS rules");
        assertTrue(renderedHtml.contains("font-family"),
                "Rendered HTML should contain CSS properties");

        System.out.println("✓ CSS styles injected successfully");
    }

    @Test
    @DisplayName("Should handle empty recipient data gracefully")
    void testEmptyRecipientData() {
        // Given
        Certificate certificateWithEmptyData = Certificate.builder()
                .id(UUID.randomUUID())
                .customerId(100L)
                .templateVersionId(simpleTemplateVersion.getId())
                .certificateNumber("CERT-2025-002")
                .recipientData("{}") // Empty JSON
                .metadata("{}")
                .status(Certificate.CertificateStatus.PENDING)
                .issuedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        String renderedHtml = pdfGenerationService.renderHtml(
                simpleTemplateVersion,
                certificateWithEmptyData
        );

        // Then
        assertNotNull(renderedHtml);
        assertFalse(renderedHtml.isEmpty());

        System.out.println("✓ Empty recipient data handled gracefully");
    }

    @Test
    @DisplayName("Should throw exception when template HTML is empty")
    void testEmptyTemplateHtml() {
        // Given
        TemplateVersion emptyTemplate = TemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(testTemplate)
                .version(1)
                .htmlContent("") // Empty HTML
                .fieldSchema("{}")
                .status(TemplateVersion.TemplateVersionStatus.DRAFT)
                .createdBy(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();

        // When/Then
        assertThrows(PdfGenerationException.class, () -> {
            pdfGenerationService.renderHtml(emptyTemplate, testCertificate);
        }, "Should throw PdfGenerationException for empty HTML content");

        System.out.println("✓ Empty template validation works correctly");
    }

    @Test
    @DisplayName("Should handle null recipient data")
    void testNullRecipientData() {
        // Given
        Certificate certificateWithNullData = Certificate.builder()
                .id(UUID.randomUUID())
                .customerId(100L)
                .templateVersionId(simpleTemplateVersion.getId())
                .certificateNumber("CERT-2025-003")
                .recipientData(null) // Null data
                .metadata(null)
                .status(Certificate.CertificateStatus.PENDING)
                .issuedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        String renderedHtml = pdfGenerationService.renderHtml(
                simpleTemplateVersion,
                certificateWithNullData
        );

        // Then
        assertNotNull(renderedHtml);
        assertFalse(renderedHtml.isEmpty());

        System.out.println("✓ Null recipient data handled gracefully");
    }

    // Helper methods

    private String loadTemplateFromResource(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return Files.readString(Paths.get(resource.getURI()));
    }

    private void savePdfToFile(byte[] pdfBytes, String filename) {
        try {
            String outputDir = "build/test-output";
            Files.createDirectories(Paths.get(outputDir));

            String filepath = outputDir + "/" + filename;
            try (FileOutputStream fos = new FileOutputStream(filepath)) {
                fos.write(pdfBytes);
            }
            System.out.println("  PDF saved to: " + filepath);
        } catch (IOException e) {
            System.err.println("  Warning: Could not save PDF file: " + e.getMessage());
        }
    }
}
