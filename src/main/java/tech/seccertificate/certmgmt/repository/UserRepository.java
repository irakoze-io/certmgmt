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

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakId(String keycloakId);

    List<User> findByActiveTrue();

    List<User> findByRole(User.UserRole role);

    List<User> findByCustomerId(Long customerId);

    boolean existsByEmail(String email);

    boolean existsByKeycloakId(String keycloakId);

    long countByActiveTrue();
}
