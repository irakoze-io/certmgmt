package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, AuditLogRepositoryCustom {

    List<AuditLog> findByCustomerId(UUID customerId);

    List<AuditLog> findByUserId(UUID userId);

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    List<AuditLog> findByAction(String action);

    List<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<AuditLog> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType);
}
