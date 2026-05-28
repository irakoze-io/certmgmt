package tech.seccertificate.umusare.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.seccertificate.umusare.auth.domain.Role;
import tech.seccertificate.umusare.auth.entity.UmusareRole;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UmusareRole entity operations.
 */
@Repository
public interface UmusareRoleRepository extends JpaRepository<UmusareRole, UUID> {
    
    /**
     * Find role by name.
     *
     * @param name the role name
     * @return the role if found
     */
    Optional<UmusareRole> findByName(Role name);
    
    /**
     * Find role with permissions eagerly loaded.
     *
     * @param name the role name
     * @return the role with permissions loaded
     */
    @Query("SELECT r FROM UmusareRole r LEFT JOIN FETCH r.permissions WHERE r.name = :name")
    Optional<UmusareRole> findByNameWithPermissions(@Param("name") Role name);
    
    /**
     * Check if role exists.
     *
     * @param name the role name
     * @return true if role exists
     */
    boolean existsByName(Role name);
}
