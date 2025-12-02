package tech.seccertificate.certmgmt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer", schema = "public")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_schema", unique = true, nullable = false, length = 75)
    private String tenantSchema;

    @Column(name = "name", nullable = false, length = 75)
    private String name;

    @Column(unique = true, nullable = false)
    private String domain;

    @Column(columnDefinition = "jsonb")
    private String settings;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "max_users")
    @Builder.Default
    private Integer maxUsers = 10;

    @Builder.Default
    @Column(name = "max_certs_per_month")
    private Integer maxCertificatesPerMonth = 1_000;

    @CreationTimestamp
    @Column(name = "created_date", columnDefinition = "DATE", nullable = false, updatable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "updated_date", columnDefinition = "DATE", nullable = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDateTime updatedDate;

    @Version
    private Long version;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<GlobalAuditLog> auditLogs = new java.util.ArrayList<>();

    public enum CustomerStatus {ACTIVE, SUSPENDED, TRIAL, CANCELLED}
}