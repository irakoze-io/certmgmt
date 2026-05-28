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
 * Passenger profile entity for the Umusare platform.
 * 
 * <p>Links to a user and provides passenger-specific data.
 * The passenger ID is used as entityId in JWT claims when the
 * user's primary role is PASSENGER.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "umusare_passengers")
public class Passenger {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Reference to the user account.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    /**
     * Preferred payment method ID.
     */
    @Column(name = "preferred_payment_method_id")
    private UUID preferredPaymentMethodId;
    
    /**
     * Home location for quick ride requests.
     */
    @Column(name = "home_address", length = 500)
    private String homeAddress;
    
    @Column(name = "home_latitude")
    private Double homeLatitude;
    
    @Column(name = "home_longitude")
    private Double homeLongitude;
    
    /**
     * Work location for quick ride requests.
     */
    @Column(name = "work_address", length = 500)
    private String workAddress;
    
    @Column(name = "work_latitude")
    private Double workLatitude;
    
    @Column(name = "work_longitude")
    private Double workLongitude;
    
    /**
     * Total number of completed rides.
     */
    @Builder.Default
    @Column(name = "total_rides")
    private Integer totalRides = 0;
    
    /**
     * Average rating given to drivers.
     */
    @Column(name = "average_rating_given")
    private Double averageRatingGiven;
    
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
