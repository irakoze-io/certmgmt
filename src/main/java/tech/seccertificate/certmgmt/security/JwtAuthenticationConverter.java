package tech.seccertificate.certmgmt.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom JWT authentication converter that extracts authorities from JWT claims.
 * 
 * <p>This converter:
 * <ul>
 *   <li>Extracts the "authorities" claim from the JWT token</li>
 *   <li>Converts role claim to Spring Security authorities</li>
 *   <li>Creates a JwtAuthenticationToken with the extracted authorities</li>
 * </ul>
 */
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultAuthoritiesConverter.convert(jwt).stream(),
                extractAuthorities(jwt).stream()
        ).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Extracts authorities from the JWT token.
     * 
     * <p>Looks for:
     * <ul>
     *   <li>"authorities" claim (array or single string)</li>
     *   <li>"role" claim (converted to ROLE_* format)</li>
     * </ul>
     * 
     * @param jwt JWT token
     * @return Collection of granted authorities
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Extract from "authorities" claim
        Object authoritiesClaim = jwt.getClaim("authorities");
        if (authoritiesClaim != null) {
            if (authoritiesClaim instanceof String) {
                return Collections.singletonList(new SimpleGrantedAuthority((String) authoritiesClaim));
            } else if (authoritiesClaim instanceof List) {
                return ((List<String>) authoritiesClaim).stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }
        }

        // Extract from "role" claim and convert to ROLE_* format
        String role = jwt.getClaimAsString("role");
        if (role != null && !role.isEmpty()) {
            String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            return Collections.singletonList(new SimpleGrantedAuthority(authority));
        }

        return Collections.emptyList();
    }
}
