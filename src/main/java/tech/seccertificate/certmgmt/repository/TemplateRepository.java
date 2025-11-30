package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.Template;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {
}
