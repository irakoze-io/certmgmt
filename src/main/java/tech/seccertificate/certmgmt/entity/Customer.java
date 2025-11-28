package tech.seccertificate.certmgmt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@Table(name = "customer", schema = "public")
@NoArgsConstructor
@AllArgsConstructor
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
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate;

    @Version
    private Long version;

    public enum CustomerStatus {ACTIVE, SUSPENDED, TRIAL, CANCELLED}
}