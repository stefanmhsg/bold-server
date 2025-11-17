package org.maze.domain.model;

/**
 * Result of an access validation check.
 * Indicates whether an agent is allowed to access a resource and provides a message if denied.
 */
public class AccessResult {
    private final boolean allowed;
    private final String message;
    
    private AccessResult(boolean allowed, String message) {
        this.allowed = allowed;
        this.message = message;
    }
    
    public static AccessResult allowed() {
        return new AccessResult(true, null);
    }
    
    public static AccessResult denied(String message) {
        return new AccessResult(false, message);
    }
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public String getMessage() {
        return message;
    }
}
