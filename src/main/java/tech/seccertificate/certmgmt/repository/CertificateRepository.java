package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.Certificate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID>, CertificateRepositoryCustom {

    Optional<Certificate> findByCertificateNumber(String certificateNumber);

    List<Certificate> findByCustomerId(Long customerId);

    List<Certificate> findByTemplateVersionId(UUID templateVersionId);

    List<Certificate> findByStatus(Certificate.CertificateStatus status);

    List<Certificate> findByCustomerIdAndStatus(Long customerId, Certificate.CertificateStatus status);

    List<Certificate> findByIssuedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Certificate> findByExpiresAtBefore(LocalDateTime date);

    List<Certificate> findByExpiresAtAfter(LocalDateTime date);

    long countByCustomerId(Long customerId);

    long countByStatus(Certificate.CertificateStatus status);

    boolean existsByCertificateNumber(String certificateNumber);
}
