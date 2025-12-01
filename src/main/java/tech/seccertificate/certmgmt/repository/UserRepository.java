package tech.seccertificate.certmgmt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.seccertificate.certmgmt.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity with tenant schema support.
 * All operations are scoped to the current tenant schema set in TenantContext.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    
    /**
     * Find a user by email within the current tenant schema.
     * @param email The user's email address
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find a user by Keycloak ID within the current tenant schema.
     * @param keycloakId The Keycloak user ID
     * @return Optional containing the user if found
     */
    Optional<User> findByKeycloakId(String keycloakId);
    
    /**
     * Find all active users within the current tenant schema.
     * @return List of active users
     */
    List<User> findByActiveTrue();
    
    /**
     * Find all users by role within the current tenant schema.
     * @param role The user role
     * @return List of users with the specified role
     */
    List<User> findByRole(User.UserRole role);
    
    /**
     * Find all users for a specific customer within the current tenant schema.
     * @param customerId The customer ID
     * @return List of users for the customer
     */
    List<User> findByCustomerId(Long customerId);
    
    /**
     * Check if a user with the given email exists within the current tenant schema.
     * @param email The email to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if a user with the given Keycloak ID exists within the current tenant schema.
     * @param keycloakId The Keycloak ID to check
     * @return true if user exists, false otherwise
     */
    boolean existsByKeycloakId(String keycloakId);
    
    /**
     * Count active users within the current tenant schema.
     * @return Number of active users
     */
    long countByActiveTrue();
}
