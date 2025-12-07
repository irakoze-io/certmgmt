package tech.seccertificate.certmgmt.repository;

import tech.seccertificate.certmgmt.entity.CertificateHash;

import java.util.Optional;
import java.util.UUID;

public interface CertificateHashRepositoryCustom {

    void setTenantSchema(String tenantSchema);

    Optional<CertificateHash> findByCertificateIdInSchema(String tenantSchema, UUID certificateId);

    Optional<CertificateHash> findByHashValueInSchema(String tenantSchema, String hashValue);

    CertificateHash saveInSchema(String tenantSchema, CertificateHash certificateHash);
}
