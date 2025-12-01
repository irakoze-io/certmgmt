package tech.seccertificate.certmgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.seccertificate.certmgmt.model.User;
import tech.seccertificate.certmgmt.repository.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests - Tenant Onboarding Milestone")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Should create user with valid data")
    void shouldCreateUserWithValidData() {
        // Arrange
        Long customerId = 1L;
        String username = "testuser";
        String email = "test@example.com";
        User.UserRole role = User.UserRole.ADMIN;
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        // Act
        User result = userService.createUser(customerId, username, email, role);

        // Assert
        assertNotNull(result);
        assertEquals(customerId, result.getCustomerId());
        assertEquals(username, result.getUsername());
        assertEquals(email, result.getEmail());
        assertEquals(role, result.getRole());
        assertTrue(result.isActive());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when creating user with null customer ID")
    void shouldThrowExceptionWhenCreatingUserWithNullCustomerId() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.createUser(null, "testuser", "test@example.com", User.UserRole.ADMIN)
        );
        assertEquals("Customer ID cannot be null", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when creating user with null username")
    void shouldThrowExceptionWhenCreatingUserWithNullUsername() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.createUser(1L, null, "test@example.com", User.UserRole.ADMIN)
        );
        assertEquals("Username cannot be empty", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when creating user with null role")
    void shouldThrowExceptionWhenCreatingUserWithNullRole() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.createUser(1L, "testuser", "test@example.com", null)
        );
        assertEquals("Role cannot be null", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when creating user with duplicate username")
    void shouldThrowExceptionWhenCreatingUserWithDuplicateUsername() {
        // Arrange
        String username = "existinguser";
        User existingUser = new User(1L, username, "existing@example.com", User.UserRole.ADMIN);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userService.createUser(1L, username, "new@example.com", User.UserRole.EDITOR)
        );
        assertTrue(exception.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when creating user with duplicate email")
    void shouldThrowExceptionWhenCreatingUserWithDuplicateEmail() {
        // Arrange
        String email = "existing@example.com";
        User existingUser = new User(1L, "existinguser", email, User.UserRole.ADMIN);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userService.createUser(1L, "newuser", email, User.UserRole.EDITOR)
        );
        assertTrue(exception.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should get user by ID")
    void shouldGetUserById() {
        // Arrange
        Long userId = 1L;
        User user = new User(1L, "testuser", "test@example.com", User.UserRole.ADMIN);
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        Optional<User> result = userService.getUserById(userId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("Should get all users for a customer")
    void shouldGetAllUsersForCustomer() {
        // Arrange
        Long customerId = 1L;
        User user1 = new User(customerId, "user1", "user1@example.com", User.UserRole.ADMIN);
        User user2 = new User(customerId, "user2", "user2@example.com", User.UserRole.EDITOR);
        List<User> users = Arrays.asList(user1, user2);
        when(userRepository.findByCustomerId(customerId)).thenReturn(users);

        // Act
        List<User> result = userService.getUsersByCustomerId(customerId);

        // Assert
        assertEquals(2, result.size());
        assertEquals(customerId, result.get(0).getCustomerId());
        assertEquals(customerId, result.get(1).getCustomerId());
        verify(userRepository, times(1)).findByCustomerId(customerId);
    }

    @Test
    @DisplayName("Should update user role")
    void shouldUpdateUserRole() {
        // Arrange
        Long userId = 1L;
        User user = new User(1L, "testuser", "test@example.com", User.UserRole.VIEWER);
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.updateUserRole(userId, User.UserRole.ADMIN);

        // Assert
        assertEquals(User.UserRole.ADMIN, result.getRole());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("Should deactivate user")
    void shouldDeactivateUser() {
        // Arrange
        Long userId = 1L;
        User user = new User(1L, "testuser", "test@example.com", User.UserRole.ADMIN);
        user.setId(userId);
        user.setActive(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        userService.deactivateUser(userId);

        // Assert
        assertFalse(user.isActive());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("Should throw exception when updating role of non-existent user")
    void shouldThrowExceptionWhenUpdatingRoleOfNonExistentUser() {
        // Arrange
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
            IllegalArgumentException.class,
            () -> userService.updateUserRole(userId, User.UserRole.ADMIN)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should support all three user roles - ADMIN, EDITOR, VIEWER")
    void shouldSupportAllThreeUserRoles() {
        // Arrange & Act & Assert
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User admin = userService.createUser(1L, "admin", "admin@example.com", User.UserRole.ADMIN);
        User editor = userService.createUser(1L, "editor", "editor@example.com", User.UserRole.EDITOR);
        User viewer = userService.createUser(1L, "viewer", "viewer@example.com", User.UserRole.VIEWER);

        assertEquals(User.UserRole.ADMIN, admin.getRole());
        assertEquals(User.UserRole.EDITOR, editor.getRole());
        assertEquals(User.UserRole.VIEWER, viewer.getRole());
    }
}
