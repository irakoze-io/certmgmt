package tech.seccertificate.certmgmt.repository;

import tech.seccertificate.certmgmt.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Custom repository interface for User with tenant schema operations.
 */
public interface UserRepositoryCustom {
    
    /**
     * Set the tenant schema for subsequent operations.
     * This should be called before any repository operations.
     * @param tenantSchema The tenant schema name
     */
    void setTenantSchema(String tenantSchema);
    
    /**
     * Find user by email in a specific tenant schema.
     * @param tenantSchema The tenant schema name
     * @param email The user's email
     * @return Optional containing the user if found
     */
    Optional<User> findByEmailInSchema(String tenantSchema, String email);
    
    /**
     * Find user by Keycloak ID in a specific tenant schema.
     * @param tenantSchema The tenant schema name
     * @param keycloakId The Keycloak user ID
     * @return Optional containing the user if found
     */
    Optional<User> findByKeycloakIdInSchema(String tenantSchema, String keycloakId);
    
    /**
     * Find all users in a specific tenant schema.
     * @param tenantSchema The tenant schema name
     * @return List of all users in the schema
     */
    List<User> findAllInSchema(String tenantSchema);
    
    /**
     * Save a user in a specific tenant schema.
     * @param tenantSchema The tenant schema name
     * @param user The user to save
     * @return The saved user
     */
    User saveInSchema(String tenantSchema, User user);
}
