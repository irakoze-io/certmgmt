package tech.seccertificate.certmgmt.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.model.Template;

import java.util.List;

@Repository
public interface TemplateRepository extends CrudRepository<Template, Long> {
    List<Template> findByCustomerId(Long customerId);
    List<Template> findByCustomerIdAndActive(Long customerId, boolean active);
}
