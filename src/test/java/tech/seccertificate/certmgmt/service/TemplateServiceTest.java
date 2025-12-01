package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.seccertificate.certmgmt.model.Template;
import tech.seccertificate.certmgmt.model.TemplateVersion;
import tech.seccertificate.certmgmt.repository.TemplateRepository;
import tech.seccertificate.certmgmt.repository.TemplateVersionRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateService Unit Tests - Template Versioning Milestone")
class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private TemplateVersionRepository templateVersionRepository;

    @InjectMocks
    private TemplateService templateService;

    @Test
    @DisplayName("Should create template with valid data")
    void shouldCreateTemplateWithValidData() {
        // Arrange
        Long customerId = 1L;
        String name = "Certificate Template";
        String description = "Test template description";
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> {
            Template template = invocation.getArgument(0);
            template.setId(1L);
            return template;
        });

        // Act
        Template result = templateService.createTemplate(customerId, name, description);

        // Assert
        assertNotNull(result);
        assertEquals(customerId, result.getCustomerId());
        assertEquals(name, result.getName());
        assertEquals(description, result.getDescription());
        assertTrue(result.isActive());
        verify(templateRepository, times(1)).save(any(Template.class));
    }

    @Test
    @DisplayName("Should throw exception when creating template with null customer ID")
    void shouldThrowExceptionWhenCreatingTemplateWithNullCustomerId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> templateService.createTemplate(null, "Template", "Description")
        );
        assertEquals("Customer ID cannot be null", exception.getMessage());
        verify(templateRepository, never()).save(any(Template.class));
    }

    @Test
    @DisplayName("Should throw exception when creating template with null name")
    void shouldThrowExceptionWhenCreatingTemplateWithNullName() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> templateService.createTemplate(1L, null, "Description")
        );
        assertEquals("Template name cannot be empty", exception.getMessage());
        verify(templateRepository, never()).save(any(Template.class));
    }

    @Test
    @DisplayName("Should get templates by customer ID")
    void shouldGetTemplatesByCustomerId() {
        // Arrange
        Long customerId = 1L;
        Template template1 = new Template(customerId, "Template 1", "Description 1");
        Template template2 = new Template(customerId, "Template 2", "Description 2");
        List<Template> templates = Arrays.asList(template1, template2);
        when(templateRepository.findByCustomerId(customerId)).thenReturn(templates);

        // Act
        List<Template> result = templateService.getTemplatesByCustomerId(customerId);

        // Assert
        assertEquals(2, result.size());
        assertEquals(customerId, result.get(0).getCustomerId());
        assertEquals(customerId, result.get(1).getCustomerId());
        verify(templateRepository, times(1)).findByCustomerId(customerId);
    }

    @Test
    @DisplayName("Should create first template version with version number 1")
    void shouldCreateFirstTemplateVersionWithVersionNumber1() {
        // Arrange
        Long templateId = 1L;
        Long customerId = 1L;
        Template template = new Template(customerId, "Test Template", "Description");
        template.setId(templateId);
        String jsonSchema = "{\"type\":\"object\",\"properties\":{\"name\":\"string\"}}";
        String templateContent = "Template content";
        
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
            .thenReturn(Collections.emptyList());
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(invocation -> {
            TemplateVersion version = invocation.getArgument(0);
            version.setId(1L);
            return version;
        });

        // Act
        TemplateVersion result = templateService.createTemplateVersion(templateId, jsonSchema, templateContent);

        // Assert
        assertNotNull(result);
        assertEquals(templateId, result.getTemplateId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(1, result.getVersionNumber());
        assertEquals(jsonSchema, result.getJsonSchema());
        assertEquals(templateContent, result.getTemplateContent());
        assertTrue(result.isImmutable());
        verify(templateVersionRepository, times(1)).save(any(TemplateVersion.class));
    }

    @Test
    @DisplayName("Should create subsequent template versions with incremented version numbers")
    void shouldCreateSubsequentTemplateVersionsWithIncrementedVersionNumbers() {
        // Arrange
        Long templateId = 1L;
        Long customerId = 1L;
        Template template = new Template(customerId, "Test Template", "Description");
        template.setId(templateId);
        
        TemplateVersion existingVersion = new TemplateVersion(templateId, customerId, 1, "{}");
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
            .thenReturn(Collections.singletonList(existingVersion));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(invocation -> {
            TemplateVersion version = invocation.getArgument(0);
            version.setId(2L);
            return version;
        });

        // Act
        TemplateVersion result = templateService.createTemplateVersion(
            templateId, 
            "{\"type\":\"object\"}", 
            "New content"
        );

        // Assert
        assertEquals(2, result.getVersionNumber());
        assertTrue(result.isImmutable());
        verify(templateVersionRepository, times(1)).save(any(TemplateVersion.class));
    }

    @Test
    @DisplayName("Should verify that template versions are immutable")
    void shouldVerifyThatTemplateVersionsAreImmutable() {
        // Arrange
        Long versionId = 1L;
        TemplateVersion version = new TemplateVersion(1L, 1L, 1, "{}");
        version.setId(versionId);
        version.setImmutable(true);
        when(templateVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        // Act
        boolean result = templateService.isTemplateVersionImmutable(versionId);

        // Assert
        assertTrue(result);
        verify(templateVersionRepository, times(1)).findById(versionId);
    }

    @Test
    @DisplayName("Should throw exception when creating version for non-existent template")
    void shouldThrowExceptionWhenCreatingVersionForNonExistentTemplate() {
        // Arrange
        Long templateId = 999L;
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> templateService.createTemplateVersion(templateId, "{}", "content")
        );
        verify(templateVersionRepository, never()).save(any(TemplateVersion.class));
    }

    @Test
    @DisplayName("Should get specific template version")
    void shouldGetSpecificTemplateVersion() {
        // Arrange
        Long templateId = 1L;
        Integer versionNumber = 2;
        TemplateVersion version = new TemplateVersion(templateId, 1L, versionNumber, "{}");
        when(templateVersionRepository.findByTemplateIdAndVersionNumber(templateId, versionNumber))
            .thenReturn(Optional.of(version));

        // Act
        Optional<TemplateVersion> result = templateService.getTemplateVersion(templateId, versionNumber);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(templateId, result.get().getTemplateId());
        assertEquals(versionNumber, result.get().getVersionNumber());
        verify(templateVersionRepository, times(1))
            .findByTemplateIdAndVersionNumber(templateId, versionNumber);
    }

    @Test
    @DisplayName("Should get latest template version")
    void shouldGetLatestTemplateVersion() {
        // Arrange
        Long templateId = 1L;
        TemplateVersion version1 = new TemplateVersion(templateId, 1L, 1, "{}");
        TemplateVersion version2 = new TemplateVersion(templateId, 1L, 2, "{}");
        TemplateVersion version3 = new TemplateVersion(templateId, 1L, 3, "{}");
        // Versions ordered by version number descending
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
            .thenReturn(Arrays.asList(version3, version2, version1));

        // Act
        Optional<TemplateVersion> result = templateService.getLatestTemplateVersion(templateId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(3, result.get().getVersionNumber());
        verify(templateVersionRepository, times(1))
            .findByTemplateIdOrderByVersionNumberDesc(templateId);
    }

    @Test
    @DisplayName("Should return empty when no versions exist for template")
    void shouldReturnEmptyWhenNoVersionsExistForTemplate() {
        // Arrange
        Long templateId = 1L;
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
            .thenReturn(Collections.emptyList());

        // Act
        Optional<TemplateVersion> result = templateService.getLatestTemplateVersion(templateId);

        // Assert
        assertFalse(result.isPresent());
        verify(templateVersionRepository, times(1))
            .findByTemplateIdOrderByVersionNumberDesc(templateId);
    }

    @Test
    @DisplayName("Should get all versions of a template in descending order")
    void shouldGetAllVersionsOfTemplateInDescendingOrder() {
        // Arrange
        Long templateId = 1L;
        TemplateVersion version1 = new TemplateVersion(templateId, 1L, 1, "{}");
        TemplateVersion version2 = new TemplateVersion(templateId, 1L, 2, "{}");
        TemplateVersion version3 = new TemplateVersion(templateId, 1L, 3, "{}");
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
            .thenReturn(Arrays.asList(version3, version2, version1));

        // Act
        List<TemplateVersion> result = templateService.getTemplateVersions(templateId);

        // Assert
        assertEquals(3, result.size());
        assertEquals(3, result.get(0).getVersionNumber());
        assertEquals(2, result.get(1).getVersionNumber());
        assertEquals(1, result.get(2).getVersionNumber());
        verify(templateVersionRepository, times(1))
            .findByTemplateIdOrderByVersionNumberDesc(templateId);
    }

    @Test
    @DisplayName("Should deactivate template")
    void shouldDeactivateTemplate() {
        // Arrange
        Long templateId = 1L;
        Template template = new Template(1L, "Test Template", "Description");
        template.setId(templateId);
        template.setActive(true);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        templateService.deactivateTemplate(templateId);

        // Assert
        assertFalse(template.isActive());
        verify(templateRepository, times(1)).save(template);
    }

    @Test
    @DisplayName("Should enforce immutability on all created template versions")
    void shouldEnforceImmutabilityOnAllCreatedTemplateVersions() {
        // Arrange
        Long templateId = 1L;
        Template template = new Template(1L, "Test Template", "Description");
        template.setId(templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
            .thenReturn(Collections.emptyList());
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(invocation -> {
            TemplateVersion version = invocation.getArgument(0);
            version.setId(1L);
            return version;
        });

        // Act
        TemplateVersion result = templateService.createTemplateVersion(templateId, "{}", "content");

        // Assert
        assertTrue(result.isImmutable(), "Template version must be immutable");
        assertNotNull(result.getCreatedAt(), "Template version must have creation timestamp");
    }
}
