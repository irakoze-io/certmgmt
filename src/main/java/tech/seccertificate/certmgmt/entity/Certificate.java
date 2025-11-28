package tech.seccertificate.certmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    private UUID customerId;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Column(name = "certificate_number", unique = true, nullable = false, length = 100)
    private String certificateNumber;

    @Column(name = "recipient_data", columnDefinition = "jsonb", nullable = false)
    private String recipientData;

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

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "certificate", cascade = CascadeType.ALL)
    private CertificateHash certificateHash;

    @Getter
    public enum CertificateStatus {
        PENDING("Certificate is pending generation"),
        ISSUED("Certificate has been issued"),
        EXPIRED("Certificate has expired"),
        REVOKED("Certificate has been revoked");

        private final String description;

        CertificateStatus(String description) {
            this.description = description;
        }
    }
}