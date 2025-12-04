package tech.seccertificate.certmgmt.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Provides database connections with tenant-specific schema switching.
 * This implementation sets the PostgreSQL search_path to the tenant schema
 * before any database operations.
 *
 * @author Ivan-Beaudry Irakoze
 * @since Dec 4, 2024
 */
@Slf4j
// Note: Bean is created in MultiTenantConfig, not via @Component
public class TenantConnectionProvider extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl {

    private final DataSource dataSource;

    public TenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        log.debug("TenantConnectionProvider initialized with DataSource: {}", dataSource.getClass().getName());
    }

    @Override
    protected DataSource selectAnyDataSource() {
        return dataSource;
    }

    @Override
    protected DataSource selectDataSource(Object o) {
        // We use the same DataSource for all tenants
        // Schema switching is handled via PostgreSQL search_path
        return dataSource;
    }

    @Override
    public Connection getConnection(Object tenantIdentifier) throws SQLException {
        String tenantId = tenantIdentifier != null ? tenantIdentifier.toString() : "public";
        log.debug("TenantConnectionProvider.getConnection() called with tenantIdentifier: {}", tenantId);
        Connection connection = super.getConnection(tenantIdentifier);

        // Set the search_path for PostgreSQL to use the tenant schema
        // This ensures all queries use the correct schema
        setSchema(connection, tenantId);
        log.debug("Schema search_path set to: {} for connection", tenantId);

        return connection;
    }

    @Override
    public void releaseConnection(Object tenantIdentifier, Connection connection) throws SQLException {
        log.debug("TenantConnectionProvider.releaseConnection() called for tenantIdentifier: {}", tenantIdentifier);
        // Reset search_path to default before releasing connection
        // This prevents schema leakage between tenants
        try {
            if (connection != null && !connection.isClosed()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET search_path TO public");
                }
            }
        } catch (SQLException e) {
            // Log error but don't fail connection release
            // Connection pool will handle cleanup
        }

        super.releaseConnection(tenantIdentifier, connection);
    }

    /**
     * Set the PostgreSQL search_path to the specified schema.
     *
     * @param connection The database connection
     * @param schemaName The schema name to set
     * @throws SQLException If setting the schema fails
     */
    private void setSchema(Connection connection, String schemaName) throws SQLException {
        if (schemaName == null || schemaName.isEmpty() || "public".equals(schemaName)) {
            // Use default public schema
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO public");
            }
            return;
        }

        // Sanitize schema name to prevent SQL injection
        String sanitized = sanitizeSchemaName(schemaName);

        // Set search_path to tenant schema, with public as fallback
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + sanitized + ", public");
        }
    }

    /**
     * Sanitize schema name to prevent SQL injection.
     * Only allows alphanumeric characters and underscores.
     *
     * @param schemaName The schema name to sanitize
     * @return Sanitized schema name
     */
    private String sanitizeSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }

        // Remove any characters that are not alphanumeric or underscore
        String sanitized = schemaName.replaceAll("[^a-zA-Z0-9_]", "");

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Schema name must contain at least one valid character");
        }

        return sanitized;
    }
}
