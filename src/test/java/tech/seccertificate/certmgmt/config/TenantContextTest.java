package tech.seccertificate.certmgmt.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantContext Unit Tests")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocal after each test
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should set and get tenant schema")
    void setAndGetTenantSchema_Success() {
        // Act
        TenantContext.setTenantSchema("test_schema");
        String result = TenantContext.getTenantSchema();

        // Assert
        assertThat(result).isEqualTo("test_schema");
    }

    @Test
    @DisplayName("Should return null when no schema is set")
    void getTenantSchema_NoSchemaSet_ReturnsNull() {
        // Act
        String result = TenantContext.getTenantSchema();

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should clear tenant schema")
    void clear_RemovesSchema() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");
        assertThat(TenantContext.getTenantSchema()).isNotNull();

        // Act
        TenantContext.clear();

        // Assert
        assertThat(TenantContext.getTenantSchema()).isNull();
    }

    @Test
    @DisplayName("Should return true when tenant schema is set")
    void hasTenantSchema_SchemaSet_ReturnsTrue() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act
        boolean result = TenantContext.hasTenantSchema();

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when tenant schema is not set")
    void hasTenantSchema_NoSchemaSet_ReturnsFalse() {
        // Act
        boolean result = TenantContext.hasTenantSchema();

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null schema assignment")
    void setTenantSchema_NullValue_SetsNull() {
        // Arrange
        TenantContext.setTenantSchema("test_schema");

        // Act
        TenantContext.setTenantSchema(null);

        // Assert
        assertThat(TenantContext.getTenantSchema()).isNull();
        assertThat(TenantContext.hasTenantSchema()).isFalse();
    }

    @Test
    @DisplayName("Should handle empty schema assignment")
    void setTenantSchema_EmptyValue_SetsEmpty() {
        // Act
        TenantContext.setTenantSchema("");

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEmpty();
        assertThat(TenantContext.hasTenantSchema()).isTrue();
    }

    @Test
    @DisplayName("Should override previous schema")
    void setTenantSchema_Override_UpdatesSchema() {
        // Arrange
        TenantContext.setTenantSchema("schema1");

        // Act
        TenantContext.setTenantSchema("schema2");

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEqualTo("schema2");
    }

    @Test
    @DisplayName("Should be thread-safe across different threads")
    void threadLocal_DifferentThreads_IsolatesValues() throws InterruptedException {
        // Arrange
        TenantContext.setTenantSchema("main_thread");
        String[] otherThreadValue = new String[1];

        // Act
        Thread otherThread = new Thread(() -> {
            TenantContext.setTenantSchema("other_thread");
            otherThreadValue[0] = TenantContext.getTenantSchema();
        });

        otherThread.start();
        otherThread.join();

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEqualTo("main_thread");
        assertThat(otherThreadValue[0]).isEqualTo("other_thread");
    }
}
