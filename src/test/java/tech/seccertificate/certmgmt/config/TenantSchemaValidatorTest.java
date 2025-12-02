package tech.seccertificate.certmgmt.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantSchemaValidator Unit Tests")
class TenantSchemaValidatorTest {

    private TenantSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TenantSchemaValidator();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should validate successfully when tenant schema is set")
    void validateTenantSchema_SchemaSet_NoException() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act & Assert
        assertThatCode(() -> validator.validateTenantSchema("testOperation"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when tenant schema is not set")
    void validateTenantSchema_NoSchema_ThrowsException() {
        // Act & Assert
        assertThatThrownBy(() -> validator.validateTenantSchema("testOperation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant schema set for operation: testOperation")
                .hasMessageContaining("Set tenant context using TenantService.setTenantContext()");
    }

    @Test
    @DisplayName("Should throw exception when tenant schema is empty")
    void validateTenantSchema_EmptySchema_ThrowsException() {
        // Arrange
        TenantContext.setTenantSchema("");

        // Act & Assert
        assertThatThrownBy(() -> validator.validateTenantSchema("testOperation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant schema set for operation");
    }

    @Test
    @DisplayName("Should include operation name in error message")
    void validateTenantSchema_NoSchema_IncludesOperationName() {
        // Act & Assert
        assertThatThrownBy(() -> validator.validateTenantSchema("createTemplate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("createTemplate");
    }

    @Test
    @DisplayName("Should return true when tenant schema is set")
    void hasTenantSchema_SchemaSet_ReturnsTrue() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act
        boolean result = validator.hasTenantSchema();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when tenant schema is not set")
    void hasTenantSchema_NoSchema_ReturnsFalse() {
        // Act
        boolean result = validator.hasTenantSchema();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should validate with different operation names")
    void validateTenantSchema_DifferentOperations_Success() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act & Assert
        assertThatCode(() -> validator.validateTenantSchema("operation1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTenantSchema("operation2"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTenantSchema("some.nested.operation"))
                .doesNotThrowAnyException();
    }
}
