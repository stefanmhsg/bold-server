package org.maze.domain.model;

/**
 * Result of an access validation check.
 * Indicates whether an agent is allowed to access a resource and provides a message if denied.
 */
public record AccessResult(boolean allowed, String message) {
    
    public static AccessResult allow() {
        return new AccessResult(true, null);
    }
    
    public static AccessResult deny(String message) {
        return new AccessResult(false, message);
    }
    
    public boolean isAllowed() {
        return allowed;
    }
}
