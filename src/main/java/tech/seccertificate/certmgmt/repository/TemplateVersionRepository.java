package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID>, TemplateVersionRepositoryCustom {

    Optional<TemplateVersion> findByTemplate_IdAndVersion(Long templateId, Integer version);

    @Query("SELECT tv FROM TemplateVersion tv LEFT JOIN FETCH tv.createdByUser WHERE tv.template.id = :templateId")
    List<TemplateVersion> findByTemplate_Id(@Param("templateId") Long templateId);

    List<TemplateVersion> findByStatus(TemplateVersion.TemplateVersionStatus status);

    @Query("SELECT tv FROM TemplateVersion tv LEFT JOIN FETCH tv.createdByUser WHERE tv.template.id = :templateId ORDER BY tv.version DESC")
    List<TemplateVersion> findByTemplate_IdOrderByVersionDesc(@Param("templateId") Long templateId);

    Optional<TemplateVersion> findFirstByTemplate_IdAndStatusOrderByVersionDesc(Long templateId, TemplateVersion.TemplateVersionStatus status);

    long countByTemplate_Id(Long templateId);

    boolean existsByTemplate_IdAndVersion(Long templateId, Integer version);
}
