package tech.seccertificate.certmgmt.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.Certificate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CertificateRepositoryImpl implements CertificateRepositoryCustom {

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
    public Optional<Certificate> findByCertificateNumberInSchema(String tenantSchema, String certificateNumber) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT c FROM Certificate c WHERE c.certificateNumber = :certificateNumber";
        TypedQuery<Certificate> query = entityManager.createQuery(jpql, Certificate.class);
        query.setParameter("certificateNumber", certificateNumber);

        try {
            Certificate certificate = query.getSingleResult();
            return Optional.of(certificate);
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByCustomerIdInSchema(String tenantSchema, Long customerId) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT c FROM Certificate c WHERE c.customerId = :customerId ORDER BY c.createdAt DESC";
        TypedQuery<Certificate> query = entityManager.createQuery(jpql, Certificate.class);
        query.setParameter("customerId", customerId);
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findByStatusInSchema(String tenantSchema, Certificate.CertificateStatus status) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT c FROM Certificate c WHERE c.status = :status ORDER BY c.createdAt DESC";
        TypedQuery<Certificate> query = entityManager.createQuery(jpql, Certificate.class);
        query.setParameter("status", status);
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Certificate> findAllInSchema(String tenantSchema) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT c FROM Certificate c ORDER BY c.createdAt DESC";
        TypedQuery<Certificate> query = entityManager.createQuery(jpql, Certificate.class);
        return query.getResultList();
    }

    @Override
    @Transactional
    public Certificate saveInSchema(String tenantSchema, Certificate certificate) {
        setTenantSchema(tenantSchema);
        if (certificate.getId() == null) {
            entityManager.persist(certificate);
        } else {
            certificate = entityManager.merge(certificate);
        }
        entityManager.flush();
        return certificate;
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
