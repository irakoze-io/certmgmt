package tech.seccertificate.certmgmt.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Custom implementation of UserRepository with tenant schema support.
 * This implementation handles dynamic schema switching for multi-tenant operations.
 */
@Repository
public class UserRepositoryImpl implements UserRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    @Transactional
    public void setTenantSchema(String tenantSchema) {
        TenantContext.setTenantSchema(tenantSchema);
        // Set the search path for PostgreSQL to use the tenant schema
        String sanitized = sanitizeSchemaName(tenantSchema);
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + sanitized + ", public");
            }
        });
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmailInSchema(String tenantSchema, String email) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT u FROM User u WHERE u.email = :email";
        TypedQuery<User> query = entityManager.createQuery(jpql, User.class);
        query.setParameter("email", email);
        
        try {
            User user = query.getSingleResult();
            return Optional.of(user);
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByKeycloakIdInSchema(String tenantSchema, String keycloakId) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT u FROM User u WHERE u.keycloakId = :keycloakId";
        TypedQuery<User> query = entityManager.createQuery(jpql, User.class);
        query.setParameter("keycloakId", keycloakId);
        
        try {
            User user = query.getSingleResult();
            return Optional.of(user);
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> findAllInSchema(String tenantSchema) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT u FROM User u";
        TypedQuery<User> query = entityManager.createQuery(jpql, User.class);
        return query.getResultList();
    }
    
    @Override
    @Transactional
    public User saveInSchema(String tenantSchema, User user) {
        setTenantSchema(tenantSchema);
        if (user.getId() == null) {
            entityManager.persist(user);
        } else {
            user = entityManager.merge(user);
        }
        entityManager.flush();
        return user;
    }
    
    /**
     * Sanitize schema name to prevent SQL injection.
     * Only allows alphanumeric characters and underscores.
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
