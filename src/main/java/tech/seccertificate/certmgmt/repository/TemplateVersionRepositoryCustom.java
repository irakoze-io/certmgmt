package tech.seccertificate.certmgmt.repository;

import tech.seccertificate.certmgmt.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateVersionRepositoryCustom {

    void setTenantSchema(String tenantSchema);

    Optional<TemplateVersion> findByTemplateIdAndVersionInSchema(String tenantSchema, UUID templateId, Integer version);

    List<TemplateVersion> findByTemplateIdInSchema(String tenantSchema, UUID templateId);

    List<TemplateVersion> findAllInSchema(String tenantSchema);

    TemplateVersion saveInSchema(String tenantSchema, TemplateVersion templateVersion);
}
