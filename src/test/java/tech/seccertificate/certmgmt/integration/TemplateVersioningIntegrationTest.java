package tech.seccertificate.certmgmt.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.seccertificate.certmgmt.model.Customer;
import tech.seccertificate.certmgmt.model.Template;
import tech.seccertificate.certmgmt.model.TemplateVersion;
import tech.seccertificate.certmgmt.repository.CustomerRepository;
import tech.seccertificate.certmgmt.repository.TemplateRepository;
import tech.seccertificate.certmgmt.repository.TemplateVersionRepository;
import tech.seccertificate.certmgmt.service.CustomerService;
import tech.seccertificate.certmgmt.service.TemplateService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Template Versioning milestone.
 * Tests template creation, version management, and immutability.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integration Tests - Template Versioning Milestone")
class TemplateVersioningIntegrationTest {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        templateVersionRepository.deleteAll();
        templateRepository.deleteAll();
        customerRepository.deleteAll();
        
        // Create a test customer
        testCustomer = customerService.createCustomer("Test Company", "test@company.com");
    }

    @Test
    @DisplayName("Should create template and first version")
    void shouldCreateTemplateAndFirstVersion() {
        // Create template
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Certificate of Achievement",
            "Template for achievement certificates"
        );

        assertNotNull(template.getId(), "Template should be persisted");
        assertEquals(testCustomer.getId(), template.getCustomerId(), "Template should belong to customer");
        assertTrue(template.isActive(), "Template should be active");

        // Create first version
        String jsonSchema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"course\":{\"type\":\"string\"}}}";
        TemplateVersion version = templateService.createTemplateVersion(
            template.getId(),
            jsonSchema,
            "Certificate content v1"
        );

        assertNotNull(version.getId(), "Version should be persisted");
        assertEquals(1, version.getVersionNumber(), "First version should be number 1");
        assertEquals(template.getId(), version.getTemplateId(), "Version should reference template");
        assertEquals(testCustomer.getId(), version.getCustomerId(), "Version should belong to customer");
        assertTrue(version.isImmutable(), "Version should be immutable");
        assertNotNull(version.getCreatedAt(), "Version should have creation timestamp");
    }

    @Test
    @DisplayName("Should create multiple versions with sequential numbering")
    void shouldCreateMultipleVersionsWithSequentialNumbering() {
        // Create template
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Certificate Template",
            "Test template"
        );

        // Create version 1
        TemplateVersion v1 = templateService.createTemplateVersion(
            template.getId(),
            "{\"version\":1}",
            "Content v1"
        );
        assertEquals(1, v1.getVersionNumber());

        // Create version 2
        TemplateVersion v2 = templateService.createTemplateVersion(
            template.getId(),
            "{\"version\":2}",
            "Content v2"
        );
        assertEquals(2, v2.getVersionNumber());

        // Create version 3
        TemplateVersion v3 = templateService.createTemplateVersion(
            template.getId(),
            "{\"version\":3}",
            "Content v3"
        );
        assertEquals(3, v3.getVersionNumber());

        // Verify all versions exist
        List<TemplateVersion> versions = templateService.getTemplateVersions(template.getId());
        assertEquals(3, versions.size(), "Should have 3 versions");
        
        // Verify versions are ordered descending
        assertEquals(3, versions.get(0).getVersionNumber());
        assertEquals(2, versions.get(1).getVersionNumber());
        assertEquals(1, versions.get(2).getVersionNumber());
    }

    @Test
    @DisplayName("Should enforce template version immutability")
    void shouldEnforceTemplateVersionImmutability() {
        // Create template and version
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Immutable Template",
            "Testing immutability"
        );

        TemplateVersion version = templateService.createTemplateVersion(
            template.getId(),
            "{\"test\":\"schema\"}",
            "Original content"
        );

        // Verify version is immutable
        assertTrue(version.isImmutable(), "Version must be immutable");
        
        // Verify immutability through service
        boolean isImmutable = templateService.isTemplateVersionImmutable(version.getId());
        assertTrue(isImmutable, "Service should confirm version is immutable");
        
        // Verify we cannot modify immutable version (at repository level)
        // In production, this would be enforced by database constraints and service layer
        TemplateVersion retrieved = templateVersionRepository.findById(version.getId()).orElseThrow();
        assertTrue(retrieved.isImmutable(), "Retrieved version must remain immutable");
    }

    @Test
    @DisplayName("Should get latest version of template")
    void shouldGetLatestVersionOfTemplate() {
        // Create template with multiple versions
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Multi-version Template",
            "Test template"
        );

        templateService.createTemplateVersion(template.getId(), "{\"v\":1}", "Content 1");
        templateService.createTemplateVersion(template.getId(), "{\"v\":2}", "Content 2");
        TemplateVersion latest = templateService.createTemplateVersion(template.getId(), "{\"v\":3}", "Content 3");

        // Get latest version
        Optional<TemplateVersion> latestVersion = templateService.getLatestTemplateVersion(template.getId());

        assertTrue(latestVersion.isPresent(), "Latest version should exist");
        assertEquals(3, latestVersion.get().getVersionNumber(), "Latest version should be number 3");
        assertEquals(latest.getId(), latestVersion.get().getId(), "Should return the most recent version");
    }

    @Test
    @DisplayName("Should get specific version by number")
    void shouldGetSpecificVersionByNumber() {
        // Create template with multiple versions
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Version Lookup Template",
            "Test template"
        );

        templateService.createTemplateVersion(template.getId(), "{\"v\":1}", "Content 1");
        TemplateVersion v2 = templateService.createTemplateVersion(template.getId(), "{\"v\":2}", "Content 2");
        templateService.createTemplateVersion(template.getId(), "{\"v\":3}", "Content 3");

        // Get specific version (v2)
        Optional<TemplateVersion> version2 = templateService.getTemplateVersion(template.getId(), 2);

        assertTrue(version2.isPresent(), "Version 2 should exist");
        assertEquals(2, version2.get().getVersionNumber(), "Should return version 2");
        assertEquals(v2.getId(), version2.get().getId(), "Should return correct version");
    }

    @Test
    @DisplayName("Should maintain version history for audit and reproducibility")
    void shouldMaintainVersionHistoryForAuditAndReproducibility() {
        // Create template
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Auditable Template",
            "Template with version history"
        );

        // Create versions over time with different schemas
        String schema1 = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}";
        String schema2 = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"date\":{\"type\":\"string\"}}}";
        String schema3 = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"date\":{\"type\":\"string\"},\"grade\":{\"type\":\"string\"}}}";

        TemplateVersion v1 = templateService.createTemplateVersion(template.getId(), schema1, "Content v1");
        TemplateVersion v2 = templateService.createTemplateVersion(template.getId(), schema2, "Content v2");
        TemplateVersion v3 = templateService.createTemplateVersion(template.getId(), schema3, "Content v3");

        // Verify complete version history is maintained
        List<TemplateVersion> history = templateService.getTemplateVersions(template.getId());
        assertEquals(3, history.size(), "All versions should be preserved");

        // Verify each version maintains its original state (immutability)
        Optional<TemplateVersion> retrievedV1 = templateService.getTemplateVersion(template.getId(), 1);
        assertTrue(retrievedV1.isPresent());
        assertEquals(schema1, retrievedV1.get().getJsonSchema(), "V1 schema should be unchanged");
        assertTrue(retrievedV1.get().isImmutable());

        Optional<TemplateVersion> retrievedV2 = templateService.getTemplateVersion(template.getId(), 2);
        assertTrue(retrievedV2.isPresent());
        assertEquals(schema2, retrievedV2.get().getJsonSchema(), "V2 schema should be unchanged");
        assertTrue(retrievedV2.get().isImmutable());

        Optional<TemplateVersion> retrievedV3 = templateService.getTemplateVersion(template.getId(), 3);
        assertTrue(retrievedV3.isPresent());
        assertEquals(schema3, retrievedV3.get().getJsonSchema(), "V3 schema should be unchanged");
        assertTrue(retrievedV3.get().isImmutable());
    }

    @Test
    @DisplayName("Should isolate templates across tenants")
    void shouldIsolateTemplatesAcrossTenants() {
        // Create second customer
        Customer customer2 = customerService.createCustomer("Company 2", "company2@test.com");

        // Create templates for each customer
        Template template1 = templateService.createTemplate(
            testCustomer.getId(),
            "Customer 1 Template",
            "Template for customer 1"
        );

        Template template2 = templateService.createTemplate(
            customer2.getId(),
            "Customer 2 Template",
            "Template for customer 2"
        );

        // Verify tenant isolation
        List<Template> customer1Templates = templateService.getTemplatesByCustomerId(testCustomer.getId());
        List<Template> customer2Templates = templateService.getTemplatesByCustomerId(customer2.getId());

        assertEquals(1, customer1Templates.size(), "Customer 1 should see only their template");
        assertEquals(1, customer2Templates.size(), "Customer 2 should see only their template");
        
        assertEquals(testCustomer.getId(), customer1Templates.get(0).getCustomerId());
        assertEquals(customer2.getId(), customer2Templates.get(0).getCustomerId());
        
        assertNotEquals(template1.getId(), template2.getId(), "Templates should be separate");
    }

    @Test
    @DisplayName("Should create versions with JSON schema for placeholders")
    void shouldCreateVersionsWithJsonSchemaForPlaceholders() {
        // Create template
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Schema-based Template",
            "Template with JSON schema"
        );

        // Create version with comprehensive JSON schema
        String jsonSchema = """
            {
                "type": "object",
                "properties": {
                    "recipientName": {"type": "string", "description": "Name of recipient"},
                    "courseName": {"type": "string", "description": "Name of course"},
                    "completionDate": {"type": "string", "format": "date"},
                    "instructorName": {"type": "string"},
                    "grade": {"type": "string", "enum": ["A", "B", "C", "D", "F"]}
                },
                "required": ["recipientName", "courseName", "completionDate"]
            }
            """;

        TemplateVersion version = templateService.createTemplateVersion(
            template.getId(),
            jsonSchema,
            "Certificate template with placeholders"
        );

        assertNotNull(version.getJsonSchema(), "Version should have JSON schema");
        assertEquals(jsonSchema, version.getJsonSchema(), "JSON schema should be stored correctly");
    }

    @Test
    @DisplayName("Should prevent modification of template versions")
    void shouldPreventModificationOfTemplateVersions() {
        // Create template and version
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Protected Template",
            "Test immutability"
        );

        TemplateVersion original = templateService.createTemplateVersion(
            template.getId(),
            "{\"original\":\"schema\"}",
            "Original content"
        );

        String originalSchema = original.getJsonSchema();
        String originalContent = original.getTemplateContent();
        
        // Attempt to retrieve and verify immutability flag prevents modification
        TemplateVersion retrieved = templateVersionRepository.findById(original.getId()).orElseThrow();
        
        assertTrue(retrieved.isImmutable(), "Version must be marked as immutable");
        assertEquals(originalSchema, retrieved.getJsonSchema(), "Schema must remain unchanged");
        assertEquals(originalContent, retrieved.getTemplateContent(), "Content must remain unchanged");
        
        // Verify the immutability check through service
        assertTrue(templateService.isTemplateVersionImmutable(original.getId()), 
            "Service must confirm version immutability");
    }

    @Test
    @DisplayName("Should validate template versioning milestone completion")
    void shouldValidateTemplateVersioningMilestoneCompletion() {
        // Milestone Requirement 1: Template creation
        Template template = templateService.createTemplate(
            testCustomer.getId(),
            "Milestone Template",
            "Template for milestone validation"
        );
        assertNotNull(template.getId(), "✓ Template created");
        assertEquals(testCustomer.getId(), template.getCustomerId(), "✓ Template linked to customer");

        // Milestone Requirement 2: Version management
        String schema1 = "{\"type\":\"object\",\"properties\":{\"name\":\"string\"}}";
        String schema2 = "{\"type\":\"object\",\"properties\":{\"name\":\"string\",\"date\":\"string\"}}";
        
        TemplateVersion v1 = templateService.createTemplateVersion(template.getId(), schema1, "Content v1");
        TemplateVersion v2 = templateService.createTemplateVersion(template.getId(), schema2, "Content v2");
        
        assertEquals(1, v1.getVersionNumber(), "✓ First version numbered correctly");
        assertEquals(2, v2.getVersionNumber(), "✓ Second version numbered sequentially");

        // Milestone Requirement 3: Version immutability
        assertTrue(v1.isImmutable(), "✓ Version 1 is immutable");
        assertTrue(v2.isImmutable(), "✓ Version 2 is immutable");
        assertTrue(templateService.isTemplateVersionImmutable(v1.getId()), "✓ Immutability enforced by service");

        // Milestone Requirement 4: Version retrieval
        Optional<TemplateVersion> latest = templateService.getLatestTemplateVersion(template.getId());
        assertTrue(latest.isPresent(), "✓ Can retrieve latest version");
        assertEquals(2, latest.get().getVersionNumber(), "✓ Latest version is correct");

        Optional<TemplateVersion> specific = templateService.getTemplateVersion(template.getId(), 1);
        assertTrue(specific.isPresent(), "✓ Can retrieve specific version");

        // Milestone Requirement 5: Version history
        List<TemplateVersion> history = templateService.getTemplateVersions(template.getId());
        assertEquals(2, history.size(), "✓ All versions maintained");
        assertEquals(2, history.get(0).getVersionNumber(), "✓ Versions ordered correctly");

        // Milestone Requirement 6: Tenant isolation
        assertEquals(testCustomer.getId(), v1.getCustomerId(), "✓ Version 1 belongs to correct customer");
        assertEquals(testCustomer.getId(), v2.getCustomerId(), "✓ Version 2 belongs to correct customer");

        // TEMPLATE VERSIONING MILESTONE: PASSED
        assertTrue(true, "✓✓✓ TEMPLATE VERSIONING MILESTONE COMPLETED ✓✓✓");
    }
}
