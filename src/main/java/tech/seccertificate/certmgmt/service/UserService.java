package tech.seccertificate.certmgmt.service;

import org.springframework.stereotype.Service;
import tech.seccertificate.certmgmt.model.User;
import tech.seccertificate.certmgmt.repository.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing user operations within customer accounts.
 */
@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * Create a new user for a customer.
     */
    public User createUser(Long customerId, String username, String email, User.UserRole role) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        
        // Check if username already exists
        Optional<User> existingUsername = userRepository.findByUsername(username);
        if (existingUsername.isPresent()) {
            throw new IllegalStateException("Username " + username + " already exists");
        }
        
        // Check if email already exists
        Optional<User> existingEmail = userRepository.findByEmail(email);
        if (existingEmail.isPresent()) {
            throw new IllegalStateException("Email " + email + " already exists");
        }
        
        User user = new User(customerId, username, email, role);
        return userRepository.save(user);
    }
    
    /**
     * Get user by ID.
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    /**
     * Get all users for a customer.
     */
    public List<User> getUsersByCustomerId(Long customerId) {
        return userRepository.findByCustomerId(customerId);
    }
    
    /**
     * Update user role.
     */
    public User updateUserRole(Long userId, User.UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setRole(newRole);
        return userRepository.save(user);
    }
    
    /**
     * Deactivate a user.
     */
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setActive(false);
        userRepository.save(user);
    }
}
