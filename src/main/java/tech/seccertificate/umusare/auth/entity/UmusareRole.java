package tech.seccertificate.umusare.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.umusare.auth.domain.Role;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Role entity for the Umusare platform.
 * 
 * <p>Maps to the roles table and supports:
 * <ul>
 *   <li>Role type (PASSENGER, DRIVER, COORDINATOR, ADMIN)</li>
 *   <li>Many-to-many relationship with permissions per INV-I14</li>
 * </ul>
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "umusare_roles")
public class UmusareRole {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Role type with precedence ordering per governance §4.1 (C7).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private Role name;
    
    @Column(length = 255)
    private String description;
    
    /**
     * Permissions associated with this role.
     * Resolved via roles → permissions join per INV-I14.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "umusare_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<UmusarePermission> permissions = new HashSet<>();
}
