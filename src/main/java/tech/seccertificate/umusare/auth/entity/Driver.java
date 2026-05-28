package tech.seccertificate.umusare.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Driver profile entity for the Umusare platform.
 * 
 * <p>Links to a user and provides driver-specific data including
 * verification status and documents. The driver ID is used as
 * entityId in JWT claims when the user's primary role is DRIVER.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "umusare_drivers")
public class Driver {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Reference to the user account.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    /**
     * Driver's license number.
     */
    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;
    
    /**
     * License expiration date.
     */
    @Column(name = "license_expiry", nullable = false)
    private LocalDate licenseExpiry;
    
    /**
     * National ID number for verification.
     */
    @Column(name = "national_id", length = 50)
    private String nationalId;
    
    /**
     * Vehicle registration plate.
     */
    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;
    
    /**
     * Vehicle make and model.
     */
    @Column(name = "vehicle_model", length = 100)
    private String vehicleModel;
    
    /**
     * Vehicle color.
     */
    @Column(name = "vehicle_color", length = 50)
    private String vehicleColor;
    
    /**
     * Vehicle year.
     */
    @Column(name = "vehicle_year")
    private Integer vehicleYear;
    
    /**
     * Driver verification status.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
    
    /**
     * Date when driver was verified.
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    /**
     * ID of the admin who verified this driver.
     */
    @Column(name = "verified_by")
    private UUID verifiedBy;
    
    /**
     * Current availability status.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean available = false;
    
    /**
     * Current location latitude.
     */
    @Column(name = "current_latitude")
    private Double currentLatitude;
    
    /**
     * Current location longitude.
     */
    @Column(name = "current_longitude")
    private Double currentLongitude;
    
    /**
     * Last location update timestamp.
     */
    @Column(name = "location_updated_at")
    private LocalDateTime locationUpdatedAt;
    
    /**
     * Total completed rides.
     */
    @Builder.Default
    @Column(name = "total_rides")
    private Integer totalRides = 0;
    
    /**
     * Average rating from passengers.
     */
    @Column(name = "average_rating")
    private Double averageRating;
    
    /**
     * Total ratings count.
     */
    @Builder.Default
    @Column(name = "total_ratings")
    private Integer totalRatings = 0;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum VerificationStatus {
        PENDING,
        UNDER_REVIEW,
        VERIFIED,
        REJECTED,
        SUSPENDED
    }
}
