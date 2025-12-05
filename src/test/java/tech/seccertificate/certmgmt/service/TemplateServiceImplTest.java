package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tech.seccertificate.certmgmt.config.TenantSchemaValidator;
import tech.seccertificate.certmgmt.entity.Template;
import tech.seccertificate.certmgmt.entity.TemplateVersion;
import tech.seccertificate.certmgmt.exception.TemplateNotFoundException;
import tech.seccertificate.certmgmt.repository.TemplateRepository;
import tech.seccertificate.certmgmt.repository.TemplateVersionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateServiceImpl Unit Tests")
class TemplateServiceImplTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private TemplateVersionRepository templateVersionRepository;

    @Mock
    private TenantSchemaValidator tenantSchemaValidator;

    @InjectMocks
    private TemplateServiceImpl templateService;

    private Template validTemplate;
    private TemplateVersion validTemplateVersion;
    private UUID testUserId;
    private UUID testVersionId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testVersionId = UUID.randomUUID();

        validTemplate = Template.builder()
                .id(1L)
                .customerId(1L)
                .name("Test Template")
                .code("test_template")
                .description("Test Description")
                .currentVersion(1)
                .metadata("{}")
                .build();

        validTemplateVersion = TemplateVersion.builder()
                .id(testVersionId)
                .template(validTemplate)
                .version(1)
                .htmlContent("<html><body>Test</body></html>")
                .fieldSchema("{\"fields\": []}")
                .cssStyles("body { margin: 0; }")
                .settings("{}")
                .status(TemplateVersion.TemplateVersionStatus.DRAFT)
                .createdBy(testUserId)
                .build();
    }

    @AfterEach
    void tearDown() {
        // Clean up if needed
    }

    // ==================== createTemplate Tests ====================

    @Test
    @DisplayName("Should create template successfully")
    void createTemplate_ValidTemplate_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.existsByCode(anyString())).thenReturn(false);
        when(templateRepository.save(any(Template.class))).thenReturn(validTemplate);

        // Act
        Template result = templateService.createTemplate(validTemplate);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Template");
        verify(tenantSchemaValidator).validateTenantSchema("createTemplate");
        verify(templateRepository).save(any(Template.class));
    }

    @Test
    @DisplayName("Should set default values when creating template")
    void createTemplate_WithoutDefaults_SetsDefaults() {
        // Arrange
        Template templateWithoutDefaults = Template.builder()
                .customerId(1L)
                .name("Test Template")
                .code("test_template")
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.existsByCode(anyString())).thenReturn(false);
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> {
            Template saved = invocation.getArgument(0);
            return saved;
        });

        // Act
        Template result = templateService.createTemplate(templateWithoutDefaults);

        // Assert
        assertThat(result.getCurrentVersion()).isEqualTo(1);
        assertThat(result.getMetadata()).isEqualTo("{}");
    }

    @Test
    @DisplayName("Should throw exception when template code is taken")
    void createTemplate_CodeTaken_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.existsByCode(anyString())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> templateService.createTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template code is already in use");

        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when data integrity is violated")
    void createTemplate_DataIntegrityViolation_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.existsByCode(anyString())).thenReturn(false);
        when(templateRepository.save(any(Template.class)))
                .thenThrow(new DataIntegrityViolationException("Constraint violation"));

        // Act & Assert
        assertThatThrownBy(() -> templateService.createTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template data violates constraints");
    }

    // ==================== findById Tests ====================

    @Test
    @DisplayName("Should find template by ID")
    void findById_ExistingId_ReturnsTemplate() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));

        // Act
        Optional<Template> result = templateService.findById(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(validTemplate);
        verify(tenantSchemaValidator).validateTenantSchema("findById");
    }

    @Test
    @DisplayName("Should return empty when template not found")
    void findById_NonExistingId_ReturnsEmpty() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Template> result = templateService.findById(999L);

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== findByCode Tests ====================

    @Test
    @DisplayName("Should find template by code")
    void findByCode_ExistingCode_ReturnsTemplate() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findByCode("test_template")).thenReturn(Optional.of(validTemplate));

        // Act
        Optional<Template> result = templateService.findByCode("test_template");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("test_template");
    }

    @Test
    @DisplayName("Should return empty when code is null")
    void findByCode_NullCode_ReturnsEmpty() {
        // Act
        Optional<Template> result = templateService.findByCode(null);

        // Assert
        assertThat(result).isEmpty();
        verify(tenantSchemaValidator, never()).validateTenantSchema(anyString());
    }

    @Test
    @DisplayName("Should return empty when code is empty")
    void findByCode_EmptyCode_ReturnsEmpty() {
        // Act
        Optional<Template> result = templateService.findByCode("   ");

        // Assert
        assertThat(result).isEmpty();
        verify(tenantSchemaValidator, never()).validateTenantSchema(anyString());
    }

    @Test
    @DisplayName("Should trim code when searching")
    void findByCode_CodeWithSpaces_TrimsCode() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findByCode("test_template")).thenReturn(Optional.of(validTemplate));

        // Act
        Optional<Template> result = templateService.findByCode("  test_template  ");

        // Assert
        assertThat(result).isPresent();
        verify(templateRepository).findByCode("test_template");
    }

    // ==================== findByCustomerId Tests ====================

    @Test
    @DisplayName("Should find templates by customer ID")
    void findByCustomerId_ExistingCustomer_ReturnsTemplates() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findByCustomerId(1L)).thenReturn(List.of(validTemplate));

        // Act
        List<Template> result = templateService.findByCustomerId(1L);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(1L);
    }

    // ==================== findAll Tests ====================

    @Test
    @DisplayName("Should find all templates")
    void findAll_ReturnsAllTemplates() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findAll()).thenReturn(List.of(validTemplate));

        // Act
        List<Template> result = templateService.findAll();

        // Assert
        assertThat(result).hasSize(1);
        verify(tenantSchemaValidator).validateTenantSchema("findAll");
    }

    // ==================== updateTemplate Tests ====================

    @Test
    @DisplayName("Should update template successfully")
    void updateTemplate_ValidUpdate_Success() {
        // Arrange
        Template updatedTemplate = Template.builder()
                .id(1L)
                .name("Updated Name")
                .code("test_template")
                .description("Updated Description")
                .customerId(1L)
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateRepository.save(any(Template.class))).thenReturn(validTemplate);

        // Act
        Template result = templateService.updateTemplate(updatedTemplate);

        // Assert
        assertThat(result).isNotNull();
        verify(templateRepository).save(any(Template.class));
    }

    @Test
    @DisplayName("Should throw exception when updating with null template")
    void updateTemplate_NullTemplate_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.updateTemplate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template and template ID must not be null");
    }

    @Test
    @DisplayName("Should throw exception when updating with null template ID")
    void updateTemplate_NullTemplateId_ThrowsException() {
        // Arrange
        Template templateWithoutId = Template.builder().name("Test").build();

        // Act & Assert
        assertThatThrownBy(() -> templateService.updateTemplate(templateWithoutId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template and template ID must not be null");
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent template")
    void updateTemplate_NonExistent_ThrowsException() {
        // Arrange
        validTemplate.setId(999L);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> templateService.updateTemplate(validTemplate))
                .isInstanceOf(TemplateNotFoundException.class)
                .hasMessageContaining("was not found");
    }

    @Test
    @DisplayName("Should throw exception when updating to a taken code")
    void updateTemplate_CodeTaken_ThrowsException() {
        // Arrange
        Template updatedTemplate = Template.builder()
                .id(1L)
                .name("Test")
                .code("different_code")
                .customerId(1L)
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateRepository.existsByCode("different_code")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> templateService.updateTemplate(updatedTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template code is already in use");
    }

    // ==================== deleteTemplate Tests ====================

    @Test
    @DisplayName("Should delete template successfully")
    void deleteTemplate_ExistingTemplate_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateVersionRepository.findByTemplate_Id(1L)).thenReturn(List.of(validTemplateVersion));
        doNothing().when(templateVersionRepository).deleteAll(anyList());
        doNothing().when(templateRepository).delete(any(Template.class));

        // Act
        templateService.deleteTemplate(1L);

        // Assert
        verify(templateVersionRepository).deleteAll(anyList());
        verify(templateRepository).delete(validTemplate);
    }

    @Test
    @DisplayName("Should throw exception when deleting with null ID")
    void deleteTemplate_NullId_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.deleteTemplate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template ID must not be null");
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent template")
    void deleteTemplate_NonExistent_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> templateService.deleteTemplate(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template not found");
    }

    // ==================== isCodeTaken Tests ====================

    @Test
    @DisplayName("Should return true when code is taken")
    void isCodeTaken_TakenCode_ReturnsTrue() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.existsByCode("taken_code")).thenReturn(true);

        // Act
        boolean result = templateService.isCodeTaken("taken_code");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when code is not taken")
    void isCodeTaken_AvailableCode_ReturnsFalse() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.existsByCode("available_code")).thenReturn(false);

        // Act
        boolean result = templateService.isCodeTaken("available_code");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when code is null")
    void isCodeTaken_NullCode_ReturnsFalse() {
        // Act
        boolean result = templateService.isCodeTaken(null);

        // Assert
        assertThat(result).isFalse();
        verify(tenantSchemaValidator, never()).validateTenantSchema(anyString());
    }

    @Test
    @DisplayName("Should trim code when checking")
    void isCodeTaken_CodeWithSpaces_TrimsCode() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.existsByCode("test_code")).thenReturn(true);

        // Act
        boolean result = templateService.isCodeTaken("  test_code  ");

        // Assert
        assertThat(result).isTrue();
        verify(templateRepository).existsByCode("test_code");
    }

    // ==================== validateTemplate Tests ====================

    @Test
    @DisplayName("Should validate template successfully")
    void validateTemplate_ValidTemplate_NoException() {
        // Act & Assert
        assertThatCode(() -> templateService.validateTemplate(validTemplate))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when template is null")
    void validateTemplate_NullTemplate_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void validateTemplate_NullName_ThrowsException() {
        // Arrange
        validTemplate.setName(null);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template name is required");
    }

    @Test
    @DisplayName("Should throw exception when name is empty")
    void validateTemplate_EmptyName_ThrowsException() {
        // Arrange
        validTemplate.setName("   ");

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template name is required");
    }

    @Test
    @DisplayName("Should throw exception when name exceeds 255 characters")
    void validateTemplate_NameTooLong_ThrowsException() {
        // Arrange
        validTemplate.setName("a".repeat(256));

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 255 characters");
    }

    @Test
    @DisplayName("Should throw exception when code is null")
    void validateTemplate_NullCode_ThrowsException() {
        // Arrange
        validTemplate.setCode(null);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template code is required");
    }

    @Test
    @DisplayName("Should throw exception when code is empty")
    void validateTemplate_EmptyCode_ThrowsException() {
        // Arrange
        validTemplate.setCode("   ");

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template code is required");
    }

    @Test
    @DisplayName("Should throw exception when code has invalid format")
    void validateTemplate_InvalidCodeFormat_ThrowsException() {
        // Arrange
        validTemplate.setCode("invalid code!");

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only alphanumeric characters, underscores, and hyphens");
    }

    @Test
    @DisplayName("Should throw exception when code exceeds 100 characters")
    void validateTemplate_CodeTooLong_ThrowsException() {
        // Arrange
        validTemplate.setCode("a".repeat(101));

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 100 characters");
    }

    @Test
    @DisplayName("Should throw exception when customer ID is null")
    void validateTemplate_NullCustomerId_ThrowsException() {
        // Arrange
        validTemplate.setCustomerId(null);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer ID is required");
    }

    @Test
    @DisplayName("Should throw exception when current version is less than 1")
    void validateTemplate_CurrentVersionLessThanOne_ThrowsException() {
        // Arrange
        validTemplate.setCurrentVersion(0);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplate(validTemplate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current version must be at least 1");
    }

    // ==================== createTemplateVersion Tests ====================

    @Test
    @DisplayName("Should create template version successfully")
    void createTemplateVersion_ValidVersion_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenReturn(validTemplateVersion);

        // Act
        TemplateVersion result = templateService.createTemplateVersion(1L, validTemplateVersion);

        // Assert
        assertThat(result).isNotNull();
        verify(templateVersionRepository).save(any(TemplateVersion.class));
    }

    @Test
    @DisplayName("Should auto-increment version number when not provided")
    void createTemplateVersion_WithoutVersionNumber_AutoIncrements() {
        // Arrange
        validTemplateVersion.setVersion(null);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateVersionRepository.countByTemplate_Id(1L)).thenReturn(2L);
        when(templateVersionRepository.findByTemplate_IdOrderByVersionDesc(1L))
                .thenReturn(List.of(
                        TemplateVersion.builder().version(2).build(),
                        TemplateVersion.builder().version(1).build()
                ));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenReturn(validTemplateVersion);
        when(templateRepository.save(any(Template.class))).thenReturn(validTemplate);

        // Act
        TemplateVersion result = templateService.createTemplateVersion(1L, validTemplateVersion);

        // Assert
        assertThat(result).isNotNull();
        verify(templateVersionRepository).save(any(TemplateVersion.class));
    }

    @Test
    @DisplayName("Should throw exception when template ID is null")
    void createTemplateVersion_NullTemplateId_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.createTemplateVersion(null, validTemplateVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template ID must not be null");
    }

    @Test
    @DisplayName("Should throw exception when version is null")
    void createTemplateVersion_NullVersion_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.createTemplateVersion(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template version cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when template not found")
    void createTemplateVersion_TemplateNotFound_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> templateService.createTemplateVersion(999L, validTemplateVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    @DisplayName("Should throw exception when version already exists")
    void createTemplateVersion_VersionExists_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateVersionRepository.existsByTemplate_IdAndVersion(1L, 1)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> templateService.createTemplateVersion(1L, validTemplateVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version 1 already exists");
    }

    @Test
    @DisplayName("Should set default status to DRAFT")
    void createTemplateVersion_WithoutStatus_SetsDraft() {
        // Arrange
        validTemplateVersion.setStatus(null);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(invocation -> {
            TemplateVersion saved = invocation.getArgument(0);
            assertThat(saved.getStatus()).isEqualTo(TemplateVersion.TemplateVersionStatus.DRAFT);
            return saved;
        });

        // Act
        templateService.createTemplateVersion(1L, validTemplateVersion);

        // Assert
        verify(templateVersionRepository).save(any(TemplateVersion.class));
    }

    // ==================== findVersionById Tests ====================

    @Test
    @DisplayName("Should find version by ID")
    void findVersionById_ExistingId_ReturnsVersion() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findById(testVersionId)).thenReturn(Optional.of(validTemplateVersion));

        // Act
        Optional<TemplateVersion> result = templateService.findVersionById(testVersionId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(validTemplateVersion);
    }

    // ==================== findVersionByTemplateIdAndVersion Tests ====================

    @Test
    @DisplayName("Should find version by template ID and version number")
    void findVersionByTemplateIdAndVersion_ExistingVersion_ReturnsVersion() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findByTemplate_IdAndVersion(1L, 1))
                .thenReturn(Optional.of(validTemplateVersion));

        // Act
        Optional<TemplateVersion> result = templateService.findVersionByTemplateIdAndVersion(1L, 1);

        // Assert
        assertThat(result).isPresent();
    }

    // ==================== findVersionsByTemplateId Tests ====================

    @Test
    @DisplayName("Should find all versions for template")
    void findVersionsByTemplateId_ExistingTemplate_ReturnsVersions() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findByTemplate_IdOrderByVersionDesc(1L))
                .thenReturn(List.of(validTemplateVersion));

        // Act
        List<TemplateVersion> result = templateService.findVersionsByTemplateId(1L);

        // Assert
        assertThat(result).hasSize(1);
    }

    // ==================== findLatestPublishedVersion Tests ====================

    @Test
    @DisplayName("Should find latest published version")
    void findLatestPublishedVersion_ExistingPublished_ReturnsVersion() {
        // Arrange
        validTemplateVersion.setStatus(TemplateVersion.TemplateVersionStatus.PUBLISHED);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findFirstByTemplate_IdAndStatusOrderByVersionDesc(
                1L, TemplateVersion.TemplateVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(validTemplateVersion));

        // Act
        Optional<TemplateVersion> result = templateService.findLatestPublishedVersion(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(TemplateVersion.TemplateVersionStatus.PUBLISHED);
    }

    // ==================== findCurrentVersion Tests ====================

    @Test
    @DisplayName("Should find current version")
    void findCurrentVersion_ExistingTemplate_ReturnsCurrentVersion() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));
        when(templateVersionRepository.findByTemplate_IdAndVersion(1L, 1))
                .thenReturn(Optional.of(validTemplateVersion));

        // Act
        Optional<TemplateVersion> result = templateService.findCurrentVersion(1L);

        // Assert
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Should return empty when template has no current version")
    void findCurrentVersion_NoCurrentVersion_ReturnsEmpty() {
        // Arrange
        validTemplate.setCurrentVersion(null);
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(1L)).thenReturn(Optional.of(validTemplate));

        // Act
        Optional<TemplateVersion> result = templateService.findCurrentVersion(1L);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should throw exception when template not found")
    void findCurrentVersion_TemplateNotFound_ThrowsException() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> templateService.findCurrentVersion(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template not found");
    }

    // ==================== updateTemplateVersion Tests ====================

    @Test
    @DisplayName("Should update template version successfully")
    void updateTemplateVersion_ValidUpdate_Success() {
        // Arrange
        TemplateVersion updatedVersion = TemplateVersion.builder()
                .id(testVersionId)
                .htmlContent("<html><body>Updated</body></html>")
                .fieldSchema("{\"fields\": []}")
                .createdBy(testUserId)
                .version(1)
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findById(testVersionId)).thenReturn(Optional.of(validTemplateVersion));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenReturn(validTemplateVersion);

        // Act
        TemplateVersion result = templateService.updateTemplateVersion(updatedVersion);

        // Assert
        assertThat(result).isNotNull();
        verify(templateVersionRepository).save(any(TemplateVersion.class));
    }

    @Test
    @DisplayName("Should throw exception when version number is changed")
    void updateTemplateVersion_ChangeVersionNumber_ThrowsException() {
        // Arrange
        TemplateVersion updatedVersion = TemplateVersion.builder()
                .id(testVersionId)
                .version(2)
                .htmlContent("<html></html>")
                .fieldSchema("{}")
                .createdBy(testUserId)
                .build();

        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findById(testVersionId)).thenReturn(Optional.of(validTemplateVersion));

        // Act & Assert
        assertThatThrownBy(() -> templateService.updateTemplateVersion(updatedVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version number cannot be changed");
    }

    // ==================== publishVersion Tests ====================

    @Test
    @DisplayName("Should publish version successfully")
    void publishVersion_ValidVersion_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findById(testVersionId)).thenReturn(Optional.of(validTemplateVersion));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenReturn(validTemplateVersion);
        when(templateRepository.save(any(Template.class))).thenReturn(validTemplate);

        // Act
        TemplateVersion result = templateService.publishVersion(testVersionId);

        // Assert
        assertThat(result).isNotNull();
        verify(templateVersionRepository).save(any(TemplateVersion.class));
        verify(templateRepository).save(any(Template.class));
    }

    // ==================== archiveVersion Tests ====================

    @Test
    @DisplayName("Should archive version successfully")
    void archiveVersion_ValidVersion_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findById(testVersionId)).thenReturn(Optional.of(validTemplateVersion));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenReturn(validTemplateVersion);

        // Act
        TemplateVersion result = templateService.archiveVersion(testVersionId);

        // Assert
        assertThat(result).isNotNull();
        verify(templateVersionRepository).save(any(TemplateVersion.class));
    }

    // ==================== setVersionAsDraft Tests ====================

    @Test
    @DisplayName("Should set version as draft successfully")
    void setVersionAsDraft_ValidVersion_Success() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.findById(testVersionId)).thenReturn(Optional.of(validTemplateVersion));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenReturn(validTemplateVersion);

        // Act
        TemplateVersion result = templateService.setVersionAsDraft(testVersionId);

        // Assert
        assertThat(result).isNotNull();
        verify(templateVersionRepository).save(any(TemplateVersion.class));
    }

    // ==================== validateTemplateVersion Tests ====================

    @Test
    @DisplayName("Should validate template version successfully")
    void validateTemplateVersion_ValidVersion_NoException() {
        // Act & Assert
        assertThatCode(() -> templateService.validateTemplateVersion(validTemplateVersion, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when template version is null")
    void validateTemplateVersion_NullVersion_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplateVersion(null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template version cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when template ID is null")
    void validateTemplateVersion_NullTemplateId_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplateVersion(validTemplateVersion, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template ID cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when HTML content is null")
    void validateTemplateVersion_NullHtmlContent_ThrowsException() {
        // Arrange
        validTemplateVersion.setHtmlContent(null);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplateVersion(validTemplateVersion, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTML content is required");
    }

    @Test
    @DisplayName("Should throw exception when field schema is null")
    void validateTemplateVersion_NullFieldSchema_ThrowsException() {
        // Arrange
        validTemplateVersion.setFieldSchema(null);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplateVersion(validTemplateVersion, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field schema is required");
    }

    @Test
    @DisplayName("Should throw exception when field schema is not JSON")
    void validateTemplateVersion_InvalidFieldSchema_ThrowsException() {
        // Arrange
        validTemplateVersion.setFieldSchema("not json");

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplateVersion(validTemplateVersion, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field schema must be valid JSON");
    }

    @Test
    @DisplayName("Should throw exception when version number is less than 1")
    void validateTemplateVersion_VersionLessThanOne_ThrowsException() {
        // Arrange
        validTemplateVersion.setVersion(0);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplateVersion(validTemplateVersion, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version number must be at least 1");
    }

    @Test
    @DisplayName("Should throw exception when createdBy is null")
    void validateTemplateVersion_NullCreatedBy_ThrowsException() {
        // Arrange
        validTemplateVersion.setCreatedBy(null);

        // Act & Assert
        assertThatThrownBy(() -> templateService.validateTemplateVersion(validTemplateVersion, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Created by (user ID) is required");
    }

    // ==================== isVersionExists Tests ====================

    @Test
    @DisplayName("Should return true when version exists")
    void isVersionExists_ExistingVersion_ReturnsTrue() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.existsByTemplate_IdAndVersion(1L, 1)).thenReturn(true);

        // Act
        boolean result = templateService.isVersionExists(1L, 1);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when version does not exist")
    void isVersionExists_NonExistingVersion_ReturnsFalse() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.existsByTemplate_IdAndVersion(1L, 999)).thenReturn(false);

        // Act
        boolean result = templateService.isVersionExists(1L, 999);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when parameters are null")
    void isVersionExists_NullParameters_ReturnsFalse() {
        // Act
        boolean result = templateService.isVersionExists(null, null);

        // Assert
        assertThat(result).isFalse();
        verify(tenantSchemaValidator, never()).validateTenantSchema(anyString());
    }

    // ==================== getNextVersionNumber Tests ====================

    @Test
    @DisplayName("Should return 1 when no versions exist")
    void getNextVersionNumber_NoVersions_ReturnsOne() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.countByTemplate_Id(1L)).thenReturn(0L);

        // Act
        Integer result = templateService.getNextVersionNumber(1L);

        // Assert
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return incremented version number")
    void getNextVersionNumber_ExistingVersions_ReturnsIncremented() {
        // Arrange
        doNothing().when(tenantSchemaValidator).validateTenantSchema(anyString());
        when(templateVersionRepository.countByTemplate_Id(1L)).thenReturn(3L);
        when(templateVersionRepository.findByTemplate_IdOrderByVersionDesc(1L))
                .thenReturn(List.of(
                        TemplateVersion.builder().version(3).build(),
                        TemplateVersion.builder().version(2).build(),
                        TemplateVersion.builder().version(1).build()
                ));

        // Act
        Integer result = templateService.getNextVersionNumber(1L);

        // Assert
        assertThat(result).isEqualTo(4);
    }

    @Test
    @DisplayName("Should throw exception when template ID is null")
    void getNextVersionNumber_NullTemplateId_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> templateService.getNextVersionNumber(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template ID must not be null");
    }
}
