package tech.seccertificate.umusare.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User entity for the Umusare platform.
 * 
 * <p>Maps to the users table and supports:
 * <ul>
 *   <li>Multiple roles via user_roles join table</li>
 *   <li>Locale preference for JWT claims</li>
 *   <li>Password hash for authentication</li>
 * </ul>
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "umusare_users")
public class UmusareUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 100)
    private String email;
    
    @Column(name = "phone_number", unique = true, length = 20)
    private String phoneNumber;
    
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    
    @Column(name = "first_name", length = 100)
    private String firstName;
    
    @Column(name = "last_name", length = 100)
    private String lastName;
    
    /**
     * User's locale preference for JWT claims.
     * Defaults to "en" per §9.4.
     */
    @Builder.Default
    @Column(nullable = false, length = 10)
    private String locale = "en";
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
    
    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;
    
    @Column(name = "phone_verified")
    @Builder.Default
    private Boolean phoneVerified = false;
    
    /**
     * User roles - many-to-many relationship via user_roles join table.
     * Permissions are resolved through roles per INV-I14.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "umusare_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<UmusareRole> roles = new HashSet<>();
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
