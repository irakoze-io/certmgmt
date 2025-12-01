package tech.seccertificate.certmgmt.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.AuditLog;

import java.util.List;
import java.util.UUID;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void setTenantSchema(String tenantSchema) {
        TenantContext.setTenantSchema(tenantSchema);
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
    public List<AuditLog> findByCustomerIdInSchema(String tenantSchema, UUID customerId) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT al FROM AuditLog al WHERE al.customerId = :customerId ORDER BY al.createdAt DESC";
        TypedQuery<AuditLog> query = entityManager.createQuery(jpql, AuditLog.class);
        query.setParameter("customerId", customerId);
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> findByEntityTypeAndEntityIdInSchema(String tenantSchema, String entityType, UUID entityId) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT al FROM AuditLog al WHERE al.entityType = :entityType AND al.entityId = :entityId ORDER BY al.createdAt DESC";
        TypedQuery<AuditLog> query = entityManager.createQuery(jpql, AuditLog.class);
        query.setParameter("entityType", entityType);
        query.setParameter("entityId", entityId);
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> findAllInSchema(String tenantSchema) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT al FROM AuditLog al ORDER BY al.createdAt DESC";
        TypedQuery<AuditLog> query = entityManager.createQuery(jpql, AuditLog.class);
        return query.getResultList();
    }

    @Override
    @Transactional
    public AuditLog saveInSchema(String tenantSchema, AuditLog auditLog) {
        setTenantSchema(tenantSchema);
        if (auditLog.getId() == null) {
            entityManager.persist(auditLog);
        } else {
            auditLog = entityManager.merge(auditLog);
        }
        entityManager.flush();
        return auditLog;
    }

    private String sanitizeSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }
        String sanitized = schemaName.replaceAll("[^a-zA-Z0-9_]", "");
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Schema name must contain at least one valid character");
        }
        return sanitized;
    }
}
