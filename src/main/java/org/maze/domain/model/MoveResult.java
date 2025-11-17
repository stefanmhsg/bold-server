package org.maze.domain.model;

/**
 * Result of a move operation.
 * Contains information about the move's success, source and destination cells, or error details.
 */
public class MoveResult {
    private final boolean success;
    private final String fromCell;
    private final String toCell;
    private final String errorMessage;
    
    private MoveResult(boolean success, String fromCell, String toCell, String errorMessage) {
        this.success = success;
        this.fromCell = fromCell;
        this.toCell = toCell;
        this.errorMessage = errorMessage;
    }
    
    public static MoveResult success(String fromCell, String toCell) {
        return new MoveResult(true, fromCell, toCell, null);
    }
    
    public static MoveResult failed(String errorMessage) {
        return new MoveResult(false, null, null, errorMessage);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getFromCell() {
        return fromCell;
    }
    
    public String getToCell() {
        return toCell;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}
