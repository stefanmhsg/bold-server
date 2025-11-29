package org.maze.domain.model;

/**
 * Tracks request count and access status for a specific request.
 * Used to monitor repeated requests and their outcomes.
 */
public class RequestInfo {
    private int count = 0;
    private boolean lastAccessAllowed = false;
    
    public void increment(boolean allowed) {
        count++;
        lastAccessAllowed = allowed;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    public boolean isLastAccessAllowed() {
        return lastAccessAllowed;
    }
    
    public void setLastAccessAllowed(boolean lastAccessAllowed) {
        this.lastAccessAllowed = lastAccessAllowed;
    }
}
