package tech.seccertificate.certmgmt.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.model.TemplateVersion;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVersionRepository extends CrudRepository<TemplateVersion, Long> {
    List<TemplateVersion> findByTemplateId(Long templateId);
    List<TemplateVersion> findByTemplateIdOrderByVersionNumberDesc(Long templateId);
    Optional<TemplateVersion> findByTemplateIdAndVersionNumber(Long templateId, Integer versionNumber);
}
