package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {
}
