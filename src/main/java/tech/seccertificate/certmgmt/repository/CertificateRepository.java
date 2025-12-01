package tech.seccertificate.certmgmt.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.model.Certificate;

import java.util.List;

@Repository
public interface CertificateRepository extends CrudRepository<Certificate, Long> {
    List<Certificate> findByCustomerId(Long customerId);
    List<Certificate> findByTemplateId(Long templateId);
    List<Certificate> findByCustomerIdAndTemplateId(Long customerId, Long templateId);
}
