package tech.seccertificate.umusare.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.seccertificate.umusare.auth.entity.Passenger;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Passenger entity operations.
 * 
 * <p>Used by PassengerQuery to resolve entityId for JWT claims
 * when the user's primary role is PASSENGER.
 */
@Repository
public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    
    /**
     * Find passenger profile by user ID.
     * Returns null if profile does not exist yet per §9.4.
     *
     * @param userId the user ID
     * @return the passenger profile if exists
     */
    Optional<Passenger> findByUserId(UUID userId);
    
    /**
     * Check if passenger profile exists for user.
     *
     * @param userId the user ID
     * @return true if profile exists
     */
    boolean existsByUserId(UUID userId);
}
