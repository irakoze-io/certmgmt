package tech.seccertificate.umusare.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Coordinator profile entity for the Umusare platform.
 * 
 * <p>Links to a user and a venue. Coordinators manage drivers at
 * specific venues. The coordinator ID is used as entityId in JWT
 * claims when the user's primary role is COORDINATOR.
 * 
 * <p>The venueId is included in JWT claims per §9.4 when the
 * user has the COORDINATOR role.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "umusare_coordinators")
public class Coordinator {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Reference to the user account.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    /**
     * Venue ID this coordinator manages.
     * This is included in JWT claims as venueId per §9.4.
     */
    @Column(name = "venue_id", nullable = false)
    private UUID venueId;
    
    /**
     * Job title or position at the venue.
     */
    @Column(length = 100)
    private String title;
    
    /**
     * Contact phone for coordination.
     */
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;
    
    /**
     * Whether this coordinator can manage other coordinators.
     */
    @Builder.Default
    @Column(name = "is_primary")
    private Boolean isPrimary = false;
    
    /**
     * Maximum number of drivers this coordinator can manage.
     */
    @Column(name = "max_drivers")
    private Integer maxDrivers;
    
    /**
     * Total drivers currently assigned.
     */
    @Builder.Default
    @Column(name = "assigned_drivers")
    private Integer assignedDrivers = 0;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
