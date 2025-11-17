package org.maze.domain.model;

/**
 * Result of a move operation.
 * Contains information about the move's success, source and destination cells, or error details.
 */
public record MoveResult(boolean success, String fromCell, String toCell, String errorMessage) {
    
    public static MoveResult success(String fromCell, String toCell) {
        return new MoveResult(true, fromCell, toCell, null);
    }
    
    public static MoveResult failed(String errorMessage) {
        return new MoveResult(false, null, null, errorMessage);
    }
    
    public boolean isSuccess() {
        return success;
    }
}
