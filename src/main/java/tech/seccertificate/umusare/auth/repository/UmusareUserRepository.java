package tech.seccertificate.umusare.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.seccertificate.umusare.auth.entity.UmusareUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UmusareUser entity operations.
 */
@Repository
public interface UmusareUserRepository extends JpaRepository<UmusareUser, UUID> {
    
    /**
     * Find user by email address.
     *
     * @param email the email address
     * @return the user if found
     */
    Optional<UmusareUser> findByEmail(String email);
    
    /**
     * Find user by phone number.
     *
     * @param phoneNumber the phone number
     * @return the user if found
     */
    Optional<UmusareUser> findByPhoneNumber(String phoneNumber);
    
    /**
     * Find user by email or phone number.
     *
     * @param email the email address
     * @param phoneNumber the phone number
     * @return the user if found
     */
    Optional<UmusareUser> findByEmailOrPhoneNumber(String email, String phoneNumber);
    
    /**
     * Check if email exists.
     *
     * @param email the email address
     * @return true if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if phone number exists.
     *
     * @param phoneNumber the phone number
     * @return true if phone number exists
     */
    boolean existsByPhoneNumber(String phoneNumber);
    
    /**
     * Find user with roles eagerly loaded.
     * This query resolves the user_roles join per INV-I14.
     *
     * @param userId the user ID
     * @return the user with roles loaded
     */
    @Query("SELECT u FROM UmusareUser u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.id = :userId")
    Optional<UmusareUser> findByIdWithRolesAndPermissions(@Param("userId") UUID userId);
    
    /**
     * Find user by email with roles eagerly loaded.
     *
     * @param email the email address
     * @return the user with roles loaded
     */
    @Query("SELECT u FROM UmusareUser u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.email = :email")
    Optional<UmusareUser> findByEmailWithRolesAndPermissions(@Param("email") String email);
}
