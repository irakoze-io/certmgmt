package tech.seccertificate.certmgmt.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.CertificateHash;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
public class CertificateHashRepositoryImpl implements CertificateHashRepositoryCustom {

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
    public Optional<CertificateHash> findByCertificateIdInSchema(String tenantSchema, UUID certificateId) {
        setTenantSchema(tenantSchema);
        String jpql = "SELECT ch FROM CertificateHash ch WHERE ch.certificate.id = :certificateId";
        TypedQuery<CertificateHash> query = entityManager.createQuery(jpql, CertificateHash.class);
        query.setParameter("certificateId", certificateId);

        try {
            CertificateHash certificateHash = query.getSingleResult();
            return Optional.of(certificateHash);
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateHash> findByHashValueInSchema(String tenantSchema, String hashValue) {
        setTenantSchema(tenantSchema);
        var jpql = "SELECT ch FROM CertificateHash ch WHERE ch.hashValue = :hashValue";
        var query = entityManager.createQuery(jpql, CertificateHash.class);
        query.setParameter("hashValue", hashValue);

        try {
            var certificateHash = query.getSingleResult();
            return Optional.of(certificateHash);
        } catch (jakarta.persistence.NoResultException e) {
            log.error("Failed to load certificate hash for hash value {}", hashValue, e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public CertificateHash saveInSchema(String tenantSchema, CertificateHash certificateHash) {
        setTenantSchema(tenantSchema);
        if (certificateHash.getId() == null) {
            entityManager.persist(certificateHash);
        } else {
            certificateHash = entityManager.merge(certificateHash);
        }
        entityManager.flush();
        return certificateHash;
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
