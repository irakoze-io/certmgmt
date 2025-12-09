package tech.seccertificate.certmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "certificate")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Column(name = "certificate_number", unique = true, nullable = false, length = 100)
    private String certificateNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipient_data", columnDefinition = "jsonb", nullable = false)
    private String recipientData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "signed_hash", length = 512)
    private String signedHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CertificateStatus status = CertificateStatus.PENDING;

    @Column(name = "issued_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime expiresAt;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by", insertable = false, updatable = false)
    private User issuedByUser;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "certificate", cascade = CascadeType.ALL)
    private CertificateHash certificateHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, insertable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_version_id", nullable = false, insertable = false, updatable = false)
    private TemplateVersion templateVersion;

    @Getter
    public enum CertificateStatus {
        PENDING("Certificate is pending generation"),
        PROCESSING("Certificate is being processed"),
        ISSUED("Certificate has been issued"),
        REVOKED("Certificate has been revoked"),
        FAILED("Certificate generation failed");

        private final String description;

        CertificateStatus(String description) {
            this.description = description;
        }
    }
}