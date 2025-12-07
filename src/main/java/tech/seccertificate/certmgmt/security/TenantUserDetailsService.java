package tech.seccertificate.certmgmt.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.Customer;
import tech.seccertificate.certmgmt.repository.CustomerRepository;
import tech.seccertificate.certmgmt.repository.UserRepository;

/**
 * Multi-tenant aware UserDetailsService implementation.
 * This service loads users from tenant-specific schemas based on the X-Tenant-Id header.
 * <p>
 * For the super user (cert_admin), it bypasses tenant context and uses a special handling.
 *
 * @author Ivan-Beaudry Irakoze
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    private static final String SUPER_USER_USERNAME = "cert_admin";

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        // Handle super user separately (bypasses tenant context)
        if (SUPER_USER_USERNAME.equals(username)) {
            return loadSuperUser();
        }

        var tenantSchema = TenantContext.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            log.error("No tenant schema set in context for user: {}. Tenant context must be set via X-Tenant-Id header or X-Tenant-Schema header.", username);
            throw new UsernameNotFoundException(
                "Tenant context not set. Cannot load user: " + username +
                ". Please provide X-Tenant-Id or X-Tenant-Schema header."
            );
        }

        log.debug("Loading user {} from tenant schema: {}", username, tenantSchema);

        // Load user from tenant schema
        var userEntity = userRepository.findByEmail(username)
            .orElseThrow(() -> {
                log.warn("User not found: {} in tenant schema: {}", username, tenantSchema);
                return new UsernameNotFoundException("User not found: " + username + " in tenant: " + tenantSchema);
            });

        if (!userEntity.getActive()) {
            log.warn("User {} is inactive", username);
            throw new UsernameNotFoundException("User is inactive: " + username);
        }

        return TenantUserDetails.from(userEntity, userEntity.getPassword(), tenantSchema);
    }

    /**
     * Loads a user by username and customer ID (for explicit tenant resolution).
     *
     * @param username The username (email)
     * @param customerId The customer ID
     * @return UserDetails
     * @throws UsernameNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsernameAndCustomer(String username, Long customerId) throws UsernameNotFoundException {
        log.debug("Loading user {} for customer {}", username, customerId);

        // Resolve tenant schema from customer
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> {
                log.error("Customer not found: {}", customerId);
                return new UsernameNotFoundException("Customer not found: " + customerId);
            });

        var tenantSchema = customer.getTenantSchema();
        if (tenantSchema == null || tenantSchema.isEmpty()) {
            log.error("Customer {} has no tenant schema", customerId);
            throw new UsernameNotFoundException("Customer has no tenant schema: " + customerId);
        }

        var previousSchema = TenantContext.getTenantSchema();
        try {
            TenantContext.setTenantSchema(tenantSchema);
            return loadUserByUsername(username);
        } finally {
            if (previousSchema != null) {
                TenantContext.setTenantSchema(previousSchema);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Loads the super user (cert_admin).
     * This user exists in the public schema and has full access.
     *
     * @return UserDetails for super user
     */
    private UserDetails loadSuperUser() {
        log.debug("Loading super user: {}", SUPER_USER_USERNAME);
        // Super user is handled separately - we'll create it in a data initialization component
        // For now, return a simple implementation
        return org.springframework.security.core.userdetails.User.withUsername(SUPER_USER_USERNAME)
            .password("{noop}runOAuth2N0w")
            .roles("ADMIN", "SUPER_ADMIN")
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(false)
            .build();
    }
}
