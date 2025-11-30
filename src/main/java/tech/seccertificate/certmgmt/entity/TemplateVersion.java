package tech.seccertificate.certmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "template_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "version"}))
public class TemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(nullable = false)
    private Integer version;

    @Lob
    @Column(name = "html_content", nullable = false, columnDefinition = "TEXT")
    private String htmlContent;

    @Column(name = "field_schema", columnDefinition = "jsonb", nullable = false)
    private String fieldSchema;

    @Lob
    @Column(name = "css_styles", columnDefinition = "TEXT")
    private String cssStyles;

    @Column(columnDefinition = "jsonb")
    private String settings;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TemplateVersionStatus status = TemplateVersionStatus.DRAFT;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Getter
    public enum TemplateVersionStatus {
        DRAFT("Template version is in draft state"),
        ACTIVE("Template version is active and published"),
        ARCHIVED("Template version is archived"),
        DEPRECATED("Template version is deprecated");

        private final String description;

        TemplateVersionStatus(String description) {
            this.description = description;
        }
    }
}