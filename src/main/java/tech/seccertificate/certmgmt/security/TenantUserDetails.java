package tech.seccertificate.certmgmt.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Getter
public class TenantUserDetails extends User {

    private final UUID userId;
    private final Long customerId;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final tech.seccertificate.certmgmt.entity.User.UserRole role;
    private final String tenantSchema;

    public TenantUserDetails(tech.seccertificate.certmgmt.entity.User userEntity, String password, String tenantSchema,
                            Collection<? extends GrantedAuthority> authorities) {
        super(
            userEntity.getEmail(),
            password,
            userEntity.getActive(),
            true, // accountNonExpired
            true, // credentialsNonExpired
            !userEntity.getActive(), // accountNonLocked (inverse of active)
            authorities
        );
        this.userId = userEntity.getId();
        this.customerId = userEntity.getCustomerId();
        this.email = userEntity.getEmail();
        this.firstName = userEntity.getFirstName();
        this.lastName = userEntity.getLastName();
        this.role = userEntity.getRole();
        this.tenantSchema = tenantSchema;
    }

    /**
     * Creates a TenantUserDetails from a User entity.
     *
     * @param userEntity The user entity
     * @param password The encoded password
     * @param tenantSchema The tenant schema name
     * @return TenantUserDetails instance
     */
    public static TenantUserDetails from(tech.seccertificate.certmgmt.entity.User userEntity, String password, String tenantSchema) {
        Collection<GrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + userEntity.getRole().name())
        );
        return new TenantUserDetails(userEntity, password, tenantSchema, authorities);
    }
}
