package tech.seccertificate.certmgmt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tech.seccertificate.certmgmt.dto.Response;

import java.io.IOException;
import java.util.List;

/**
 * Security exception handlers for authentication and authorization failures.
 * 
 * <p>These handlers provide consistent JSON error responses for:
 * <ul>
 *   <li>Authentication failures (401 Unauthorized) - via AuthenticationEntryPoint</li>
 *   <li>Authorization failures (403 Forbidden) - via AccessDeniedHandler</li>
 * </ul>
 * 
 * <p>All responses follow the unified {@link Response} envelope structure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * Handles authentication failures (401 Unauthorized).
     * 
     * <p>Invoked when:
     * <ul>
     *   <li>No authentication token is provided</li>
     *   <li>Invalid or expired JWT token</li>
     *   <li>Malformed authentication header</li>
     * </ul>
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param authException Authentication exception
     * @throws IOException if response writing fails
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Authentication failed for request {}: {}", 
                request.getRequestURI(), authException.getMessage());

        Response<Void> errorResponse = Response.<Void>error(
                "Authentication required. Please provide a valid JWT token.",
                "UNAUTHORIZED",
                "Authentication Error",
                List.of(authException.getMessage() != null ? authException.getMessage() : "Invalid or missing authentication token")
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    /**
     * Handles authorization failures (403 Forbidden).
     * 
     * <p>Invoked when:
     * <ul>
     *   <li>User is authenticated but lacks required permissions</li>
     *   <li>Access is denied based on role or authority checks</li>
     * </ul>
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param accessDeniedException Access denied exception
     * @throws IOException if response writing fails
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied for request {}: {}", 
                request.getRequestURI(), accessDeniedException.getMessage());

        Response<Void> errorResponse = Response.<Void>error(
                "Access denied. You do not have permission to access this resource.",
                "FORBIDDEN",
                "Authorization Error",
                List.of(accessDeniedException.getMessage() != null ? accessDeniedException.getMessage() : "Insufficient permissions")
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
