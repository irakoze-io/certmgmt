package tech.seccertificate.umusare.auth.domain;

/**
 * User roles in the Umusare platform with defined precedence.
 * 
 * <p>Role precedence (lowest to highest): PASSENGER < DRIVER < COORDINATOR < ADMIN
 * 
 * <p>Per governance §4.1 (C7), when a user has multiple roles, the primary role
 * is determined by this precedence order.
 */
public enum Role {
    
    PASSENGER(1),
    DRIVER(2),
    COORDINATOR(3),
    ADMIN(4);
    
    private final int precedence;
    
    Role(int precedence) {
        this.precedence = precedence;
    }
    
    public int getPrecedence() {
        return precedence;
    }
    
    /**
     * Determines the primary role from a collection of roles based on precedence.
     * Returns the role with the highest precedence value.
     *
     * @param roles the roles to evaluate
     * @return the primary role with highest precedence, or null if empty
     */
    public static Role getPrimaryRole(Iterable<Role> roles) {
        Role primary = null;
        for (Role role : roles) {
            if (primary == null || role.getPrecedence() > primary.getPrecedence()) {
                primary = role;
            }
        }
        return primary;
    }
}
