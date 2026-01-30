package org.maze.api.dto;

public class LockDto {
    public boolean isLocked;
    public String keyNeeded;
    
    public LockDto() {}
    
    public LockDto(boolean isLocked, String keyNeeded) {
        this.isLocked = isLocked;
        this.keyNeeded = keyNeeded;
    }
}
