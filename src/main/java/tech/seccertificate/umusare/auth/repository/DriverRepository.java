package tech.seccertificate.umusare.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.seccertificate.umusare.auth.entity.Driver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Driver entity operations.
 * 
 * <p>Used by DriverQuery to resolve entityId for JWT claims
 * when the user's primary role is DRIVER.
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    
    /**
     * Find driver profile by user ID.
     * Returns null if profile does not exist yet per §9.4.
     *
     * @param userId the user ID
     * @return the driver profile if exists
     */
    Optional<Driver> findByUserId(UUID userId);
    
    /**
     * Check if driver profile exists for user.
     *
     * @param userId the user ID
     * @return true if profile exists
     */
    boolean existsByUserId(UUID userId);
    
    /**
     * Find all available drivers within a radius.
     *
     * @param latitude center latitude
     * @param longitude center longitude
     * @param radiusKm radius in kilometers
     * @return list of available drivers
     */
    @Query("""
            SELECT d FROM Driver d 
            WHERE d.available = true 
            AND d.active = true 
            AND d.verificationStatus = 'VERIFIED'
            AND d.currentLatitude IS NOT NULL 
            AND d.currentLongitude IS NOT NULL
            AND (6371 * acos(cos(radians(:latitude)) * cos(radians(d.currentLatitude)) 
                * cos(radians(d.currentLongitude) - radians(:longitude)) 
                + sin(radians(:latitude)) * sin(radians(d.currentLatitude)))) <= :radiusKm
            ORDER BY (6371 * acos(cos(radians(:latitude)) * cos(radians(d.currentLatitude)) 
                * cos(radians(d.currentLongitude) - radians(:longitude)) 
                + sin(radians(:latitude)) * sin(radians(d.currentLatitude))))
            """)
    List<Driver> findAvailableDriversNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm
    );
    
    /**
     * Find driver by license number.
     *
     * @param licenseNumber the license number
     * @return the driver if found
     */
    Optional<Driver> findByLicenseNumber(String licenseNumber);
}
