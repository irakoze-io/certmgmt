package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.Certificate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID>, CertificateRepositoryCustom {

    Optional<Certificate> findByCertificateNumber(String certificateNumber);

    @Query("SELECT c FROM Certificate c LEFT JOIN FETCH c.issuedByUser WHERE c.customerId = :customerId")
    List<Certificate> findByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT c FROM Certificate c LEFT JOIN FETCH c.issuedByUser WHERE c.templateVersionId = :templateVersionId")
    List<Certificate> findByTemplateVersionId(@Param("templateVersionId") UUID templateVersionId);

    @Query("SELECT c FROM Certificate c LEFT JOIN FETCH c.issuedByUser WHERE c.status = :status")
    List<Certificate> findByStatus(@Param("status") Certificate.CertificateStatus status);

    @Query("SELECT c FROM Certificate c LEFT JOIN FETCH c.issuedByUser WHERE c.customerId = :customerId AND c.status = :status")
    List<Certificate> findByCustomerIdAndStatus(@Param("customerId") Long customerId, @Param("status") Certificate.CertificateStatus status);

    List<Certificate> findByIssuedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Certificate> findByExpiresAtBefore(LocalDateTime date);

    List<Certificate> findByExpiresAtAfter(LocalDateTime date);

    long countByCustomerId(Long customerId);

    long countByStatus(Certificate.CertificateStatus status);

    boolean existsByCertificateNumber(String certificateNumber);
}
