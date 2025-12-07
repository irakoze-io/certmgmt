package tech.seccertificate.certmgmt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Generated(GenerationTime.INSERT)
    @Column(name = "uuid", unique = true, nullable = false, updatable = false, columnDefinition = "UUID DEFAULT uuid_generate_v4()")
    private UUID uuid;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(unique = true, nullable = false, length = 75)
    private String email;

    @Column(name = "keycloak_id", unique = true, nullable = false, length = 75)
    private String keycloakId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role = UserRole.VIEWER;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_login", columnDefinition = "TIMESTAMP")
    private LocalDateTime lastLogin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum UserRole {
        ADMIN,
        EDITOR,
        VIEWER,
        API_CLIENT
    }
}
