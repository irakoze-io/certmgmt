package tech.seccertificate.umusare.auth.domain;

/**
 * Permissions available in the Umusare platform.
 * 
 * <p>Permissions are resolved from the database join: user_roles → roles → permissions
 * per INV-I14.
 */
public enum Permission {
    
    // Passenger permissions
    REQUEST_RIDE,
    VIEW_RIDE_HISTORY,
    RATE_DRIVER,
    MANAGE_PAYMENT_METHODS,
    
    // Driver permissions
    ACCEPT_RIDE,
    UPDATE_AVAILABILITY,
    VIEW_EARNINGS,
    COMPLETE_RIDE,
    
    // Coordinator permissions
    MANAGE_DRIVERS,
    VIEW_VENUE_STATS,
    ASSIGN_DRIVERS,
    MANAGE_VENUE_SETTINGS,
    
    // Admin permissions
    MANAGE_USERS,
    MANAGE_VENUES,
    VIEW_SYSTEM_STATS,
    MANAGE_COORDINATORS,
    SYSTEM_CONFIG
}
