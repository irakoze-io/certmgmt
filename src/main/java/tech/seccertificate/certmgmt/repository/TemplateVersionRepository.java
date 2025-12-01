package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID>, TemplateVersionRepositoryCustom {

    Optional<TemplateVersion> findByTemplateIdAndVersion(UUID templateId, Integer version);

    List<TemplateVersion> findByTemplateId(UUID templateId);

    List<TemplateVersion> findByStatus(TemplateVersion.TemplateVersionStatus status);

    List<TemplateVersion> findByTemplateIdOrderByVersionDesc(UUID templateId);

    Optional<TemplateVersion> findFirstByTemplateIdAndStatusOrderByVersionDesc(UUID templateId, TemplateVersion.TemplateVersionStatus status);

    long countByTemplateId(UUID templateId);

    boolean existsByTemplateIdAndVersion(UUID templateId, Integer version);
}
