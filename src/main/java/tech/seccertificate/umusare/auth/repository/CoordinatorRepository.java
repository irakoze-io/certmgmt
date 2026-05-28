package tech.seccertificate.umusare.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.umusare.auth.entity.Coordinator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Coordinator entity operations.
 * 
 * <p>Used by CoordinatorQuery to resolve entityId and venueId
 * for JWT claims when the user's primary role is COORDINATOR.
 */
@Repository
public interface CoordinatorRepository extends JpaRepository<Coordinator, UUID> {
    
    /**
     * Find coordinator profile by user ID.
     * Returns null if profile does not exist yet per §9.4.
     *
     * @param userId the user ID
     * @return the coordinator profile if exists
     */
    Optional<Coordinator> findByUserId(UUID userId);
    
    /**
     * Check if coordinator profile exists for user.
     *
     * @param userId the user ID
     * @return true if profile exists
     */
    boolean existsByUserId(UUID userId);
    
    /**
     * Find all coordinators for a venue.
     *
     * @param venueId the venue ID
     * @return list of coordinators
     */
    List<Coordinator> findByVenueId(UUID venueId);
    
    /**
     * Find the primary coordinator for a venue.
     *
     * @param venueId the venue ID
     * @return the primary coordinator if exists
     */
    Optional<Coordinator> findByVenueIdAndIsPrimaryTrue(UUID venueId);
    
    /**
     * Count active coordinators for a venue.
     *
     * @param venueId the venue ID
     * @return count of active coordinators
     */
    long countByVenueIdAndActiveTrue(UUID venueId);
}
