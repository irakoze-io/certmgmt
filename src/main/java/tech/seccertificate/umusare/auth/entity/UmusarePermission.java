package tech.seccertificate.umusare.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.umusare.auth.domain.Permission;

import java.util.UUID;

/**
 * Permission entity for the Umusare platform.
 * 
 * <p>Maps to the permissions table. Permissions are resolved through
 * the user_roles → roles → permissions join per INV-I14.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "umusare_permissions")
public class UmusarePermission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Permission type from the Permission enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private Permission name;
    
    @Column(length = 255)
    private String description;
}
