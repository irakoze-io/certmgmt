package tech.seccertificate.certmgmt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tech.seccertificate.certmgmt.dto.Response;

import java.io.IOException;

/**
 * Filter to validate that the tenant in the JWT token matches the tenant from the request header.
 *
 * <p>This filter prevents tenant confusion attacks where an attacker:
 * <ul>
 *   <li>Logs into Tenant A and receives a JWT token</li>
 *   <li>Uses that token but changes X-Tenant-Id header to Tenant B</li>
 *   <li>Attempts to access Tenant B's data</li>
 * </ul>
 *
 * <p>This filter runs AFTER authentication (JWT validation) and validates:
 * <ul>
 *   <li>If request is authenticated, extract tenant from JWT token</li>
 *   <li>Compare with tenant from TenantContext (set by TenantResolutionFilter)</li>
 *   <li>Reject request if tenants don't match</li>
 * </ul>
 *
 * <p>Order: 2 (runs after TenantResolutionFilter which is Order 1, and after JWT authentication)
 *
 * @author Ivan-Beaudry Irakoze
 * @since Dec 7, 2024
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class TenantValidationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (shouldSkipValidation(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        var jwt = jwtAuth.getToken();

        var jwtTenant = jwt.getClaimAsString("tenant");
        var headerTenant = TenantContext.getTenantSchema();

        if (headerTenant == null || headerTenant.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (jwtTenant == null || jwtTenant.isEmpty()) {
            log.warn("JWT token does not contain tenant claim for request: {}", request.getRequestURI());
            sendErrorResponse(response,
                    "Token does not contain tenant information");
            return;
        }

        if (!jwtTenant.equals(headerTenant)) {
            log.warn("Tenant mismatch detected - JWT tenant: {}, Header tenant: {}, User: {}, Request: {}",
                    jwtTenant, headerTenant, jwt.getSubject(), request.getRequestURI());
            sendErrorResponse(response,
                    "Token tenant does not match requested tenant. Token is for tenant: " + jwtTenant);
            return;
        }

        log.debug("Tenant validation passed - Tenant: {}, User: {}", jwtTenant, jwt.getSubject());
        filterChain.doFilter(request, response);
    }

    /**
     * Check if tenant validation should be skipped for this request.
     *
     * @param request HTTP request
     * @return true if validation should be skipped
     */
    private boolean shouldSkipValidation(HttpServletRequest request) {
        var path = request.getRequestURI();

        return path.startsWith("/actuator/") ||
               path.equals("/error") ||
               path.equals("/favicon.ico") ||
               path.startsWith("/v3/api-docs/") ||
               path.startsWith("/swagger-ui/") ||
               path.equals("/swagger-ui.html") ||
               path.equals("/scalar") ||
               path.startsWith("/scalar/") ||
               path.equals("/api/customers") && "POST".equals(request.getMethod()) ||
               path.equals("/auth/users") && "POST".equals(request.getMethod()) ||
               path.equals("/auth/login") && "POST".equals(request.getMethod());
    }

    /**
     * Send error response in consistent format.
     *
     * @param response HTTP response
     * @param message  Error message
     */
    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Response<?> errorResponse = Response.error(
                message,
                "TENANT_MISMATCH",
                "Tenant Validation Error",
                java.util.List.of(message)
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
