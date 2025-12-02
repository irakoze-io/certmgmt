package tech.seccertificate.certmgmt.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.seccertificate.certmgmt.config.TenantContext;
import tech.seccertificate.certmgmt.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRepositoryImpl Unit Tests")
class UserRepositoryImplTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private TypedQuery<User> typedQuery;

    @InjectMocks
    private UserRepositoryImpl userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setKeycloakId(UUID.randomUUID().toString());
        testUser.setFirstName("Test");
        testUser.setLastName("User");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== setTenantSchema Tests ====================

    @Test
    @DisplayName("Should set tenant schema and execute SQL")
    void setTenantSchema_ValidSchema_SetsContext() {
        // Arrange
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));

        // Act
        userRepository.setTenantSchema("test_schema");

        // Assert
        assertThat(TenantContext.getTenantSchema()).isEqualTo("test_schema");
        verify(entityManager).unwrap(Session.class);
        verify(session).doWork(any(Work.class));
    }

    // ==================== findByEmailInSchema Tests ====================

    @Test
    @DisplayName("Should find user by email in schema")
    void findByEmailInSchema_ExistingUser_ReturnsUser() {
        // Arrange
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        when(entityManager.createQuery(anyString(), eq(User.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getSingleResult()).thenReturn(testUser);

        // Act
        Optional<User> result = userRepository.findByEmailInSchema("test_schema", "test@example.com");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser);
        verify(entityManager).createQuery("SELECT u FROM User u WHERE u.email = :email", User.class);
        verify(typedQuery).setParameter("email", "test@example.com");
    }

    @Test
    @DisplayName("Should return empty when user not found by email")
    void findByEmailInSchema_NonExistingUser_ReturnsEmpty() {
        // Arrange
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        when(entityManager.createQuery(anyString(), eq(User.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getSingleResult()).thenThrow(new NoResultException());

        // Act
        Optional<User> result = userRepository.findByEmailInSchema("test_schema", "notfound@example.com");

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== findByKeycloakIdInSchema Tests ====================

    @Test
    @DisplayName("Should find user by Keycloak ID in schema")
    void findByKeycloakIdInSchema_ExistingUser_ReturnsUser() {
        // Arrange
        String keycloakId = UUID.randomUUID().toString();
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        when(entityManager.createQuery(anyString(), eq(User.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getSingleResult()).thenReturn(testUser);

        // Act
        Optional<User> result = userRepository.findByKeycloakIdInSchema("test_schema", keycloakId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser);
        verify(entityManager).createQuery("SELECT u FROM User u WHERE u.keycloakId = :keycloakId", User.class);
        verify(typedQuery).setParameter("keycloakId", keycloakId);
    }

    @Test
    @DisplayName("Should return empty when user not found by Keycloak ID")
    void findByKeycloakIdInSchema_NonExistingUser_ReturnsEmpty() {
        // Arrange
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        when(entityManager.createQuery(anyString(), eq(User.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getSingleResult()).thenThrow(new NoResultException());

        // Act
        Optional<User> result = userRepository.findByKeycloakIdInSchema("test_schema", "nonexistent-id");

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== findAllInSchema Tests ====================

    @Test
    @DisplayName("Should find all users in schema")
    void findAllInSchema_ReturnsUsers() {
        // Arrange
        List<User> users = List.of(testUser);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        when(entityManager.createQuery(anyString(), eq(User.class))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(users);

        // Act
        List<User> result = userRepository.findAllInSchema("test_schema");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result).contains(testUser);
        verify(entityManager).createQuery("SELECT u FROM User u", User.class);
    }

    @Test
    @DisplayName("Should return empty list when no users in schema")
    void findAllInSchema_NoUsers_ReturnsEmptyList() {
        // Arrange
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        when(entityManager.createQuery(anyString(), eq(User.class))).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        // Act
        List<User> result = userRepository.findAllInSchema("test_schema");

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== saveInSchema Tests ====================

    @Test
    @DisplayName("Should persist new user in schema")
    void saveInSchema_NewUser_Persists() {
        // Arrange
        User newUser = new User();
        newUser.setEmail("new@example.com");
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        doNothing().when(entityManager).persist(any(User.class));
        doNothing().when(entityManager).flush();

        // Act
        User result = userRepository.saveInSchema("test_schema", newUser);

        // Assert
        assertThat(result).isNotNull();
        verify(entityManager).persist(newUser);
        verify(entityManager).flush();
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("Should merge existing user in schema")
    void saveInSchema_ExistingUser_Merges() {
        // Arrange
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));
        when(entityManager.merge(any(User.class))).thenReturn(testUser);
        doNothing().when(entityManager).flush();

        // Act
        User result = userRepository.saveInSchema("test_schema", testUser);

        // Assert
        assertThat(result).isEqualTo(testUser);
        verify(entityManager).merge(testUser);
        verify(entityManager).flush();
        verify(entityManager, never()).persist(any());
    }

    // ==================== sanitizeSchemaName Tests ====================

    @Test
    @DisplayName("Should allow valid schema names with alphanumeric and underscores")
    void sanitizeSchemaName_ValidNames_Allowed() {
        // These tests indirectly test sanitization through setTenantSchema
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doNothing().when(session).doWork(any(Work.class));

        // Act & Assert - should not throw
        assertThatCode(() -> userRepository.setTenantSchema("schema_123"))
                .doesNotThrowAnyException();
        assertThatCode(() -> userRepository.setTenantSchema("test_schema_ABC"))
                .doesNotThrowAnyException();
    }
}
