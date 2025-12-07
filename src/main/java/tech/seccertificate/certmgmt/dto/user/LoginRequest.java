package tech.seccertificate.certmgmt.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.entity.User;

@Schema(description = "Request to create a new user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 75, message = "Email must not exceed 75 characters")
    @Schema(description = "User email address (must be unique within tenant)",
        example = "john.doe@example.com", required = true, maxLength = 75)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    @Schema(description = "User password (will be encoded)",
        example = "SecurePassword123!", required = true, minLength = 8, maxLength = 255)
    private String password;

    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(description = "User first name", example = "John", maxLength = 100)
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(description = "User last name", example = "Doe", maxLength = 100)
    private String lastName;

    @Schema(description = "User role (defaults to VIEWER)",
        example = "ADMIN", allowableValues = {"ADMIN", "EDITOR", "VIEWER", "API_CLIENT"})
    private User.UserRole role;

    @Schema(description = "Keycloak ID (optional, will be auto-generated if not provided)",
        example = "550e8400-e29b-41d4-a716-446655440000", maxLength = 75)
    private String keycloakId;

    @Schema(description = "Whether the user is active (defaults to true)", example = "true")
    private Boolean active;
}
