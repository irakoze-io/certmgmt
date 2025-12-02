package tech.seccertificate.certmgmt.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantIdentifierResolver Unit Tests")
class TenantIdentifierResolverTest {

    private TenantIdentifierResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantIdentifierResolver();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should resolve current tenant identifier when schema is set")
    void resolveCurrentTenantIdentifier_SchemaSet_ReturnsSchema() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act
        String result = resolver.resolveCurrentTenantIdentifier();

        // Assert
        assertThat(result).isEqualTo("test_schema");
    }

    @Test
    @DisplayName("Should return default identifier when no schema is set")
    void resolveCurrentTenantIdentifier_NoSchema_ReturnsDefault() {
        // Act
        String result = resolver.resolveCurrentTenantIdentifier();

        // Assert
        assertThat(result).isEqualTo("public");
    }

    @Test
    @DisplayName("Should return default identifier when schema is empty")
    void resolveCurrentTenantIdentifier_EmptySchema_ReturnsDefault() {
        // Arrange
        TenantContext.setTenantSchema("");

        // Act
        String result = resolver.resolveCurrentTenantIdentifier();

        // Assert
        assertThat(result).isEqualTo("public");
    }

    @Test
    @DisplayName("Should return default identifier when schema is null")
    void resolveCurrentTenantIdentifier_NullSchema_ReturnsDefault() {
        // Arrange
        TenantContext.setTenantSchema(null);

        // Act
        String result = resolver.resolveCurrentTenantIdentifier();

        // Assert
        assertThat(result).isEqualTo("public");
    }

    @Test
    @DisplayName("Should not validate existing current sessions")
    void validateExistingCurrentSessions_ReturnsFalse() {
        // Act
        boolean result = resolver.validateExistingCurrentSessions();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should identify public schema as root")
    void isRoot_PublicSchema_ReturnsTrue() {
        // Act
        boolean result = resolver.isRoot("public");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not identify tenant schema as root")
    void isRoot_TenantSchema_ReturnsFalse() {
        // Act
        boolean result = resolver.isRoot("tenant_schema");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should not identify null as root")
    void isRoot_Null_ReturnsFalse() {
        // Act
        boolean result = resolver.isRoot(null);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should not identify non-String object as root")
    void isRoot_NonStringObject_ReturnsFalse() {
        // Act
        boolean result = resolver.isRoot(123);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle different tenant schemas")
    void resolveCurrentTenantIdentifier_DifferentSchemas_ReturnsCorrectly() {
        // Test schema 1
        TenantContext.setTenantSchema("schema1");
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("schema1");

        // Test schema 2
        TenantContext.setTenantSchema("schema2");
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("schema2");

        // Test clearing
        TenantContext.clear();
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("public");
    }
}
