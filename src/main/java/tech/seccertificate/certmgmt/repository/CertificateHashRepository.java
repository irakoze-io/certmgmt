package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.CertificateHash;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateHashRepository extends JpaRepository<CertificateHash, Long>, CertificateHashRepositoryCustom {

    Optional<CertificateHash> findByCertificateId(UUID certificateId);

    boolean existsByCertificateId(UUID certificateId);
}
