package tech.seccertificate.certmgmt.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TemplateVersionRepositoryImpl implements TemplateVersionRepositoryCustom {

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
    public Optional<TemplateVersion> findByTemplateIdAndVersionInSchema(String tenantSchema, Long templateId, Integer version) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT tv FROM TemplateVersion tv WHERE tv.template.id = :templateId AND tv.version = :version";
        TypedQuery<TemplateVersion> query = entityManager.createQuery(jpql, TemplateVersion.class);
        query.setParameter("templateId", templateId);
        query.setParameter("version", version);

        try {
            TemplateVersion templateVersion = query.getSingleResult();
            return Optional.of(templateVersion);
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateVersion> findByTemplateIdInSchema(String tenantSchema, Long templateId) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT tv FROM TemplateVersion tv WHERE tv.template.id = :templateId ORDER BY tv.version DESC";
        TypedQuery<TemplateVersion> query = entityManager.createQuery(jpql, TemplateVersion.class);
        query.setParameter("templateId", templateId);
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateVersion> findAllInSchema(String tenantSchema) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT tv FROM TemplateVersion tv";
        TypedQuery<TemplateVersion> query = entityManager.createQuery(jpql, TemplateVersion.class);
        return query.getResultList();
    }

    @Override
    @Transactional
    public TemplateVersion saveInSchema(String tenantSchema, TemplateVersion templateVersion) {
        setTenantSchema(tenantSchema);
        if (templateVersion.getId() == null) {
            entityManager.persist(templateVersion);
        } else {
            templateVersion = entityManager.merge(templateVersion);
        }
        entityManager.flush();
        return templateVersion;
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
