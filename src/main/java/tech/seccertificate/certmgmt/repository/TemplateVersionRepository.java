package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID>, TemplateVersionRepositoryCustom {

    Optional<TemplateVersion> findByTemplate_IdAndVersion(Long templateId, Integer version);

    List<TemplateVersion> findByTemplate_Id(Long templateId);

    List<TemplateVersion> findByStatus(TemplateVersion.TemplateVersionStatus status);

    List<TemplateVersion> findByTemplate_IdOrderByVersionDesc(Long templateId);

    Optional<TemplateVersion> findFirstByTemplate_IdAndStatusOrderByVersionDesc(Long templateId, TemplateVersion.TemplateVersionStatus status);

    long countByTemplate_Id(Long templateId);

    boolean existsByTemplate_IdAndVersion(Long templateId, Integer version);
}
