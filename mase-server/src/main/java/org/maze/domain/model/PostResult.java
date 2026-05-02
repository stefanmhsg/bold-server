package org.maze.domain.model;

/**
 * Result of a POST operation.
 * Contains information about the operation's success, graph modifications, and triggered rules.
 */
public record PostResult(boolean success, String graphUri, int triplesAdded, 
                         String errorMessage, int statusCode) {
    
    public static PostResult success(String graphUri, int triplesAdded, String responseMessage) {
        return new PostResult(true, graphUri, triplesAdded, null, 201);
    }
    
    public static PostResult denied(String errorMessage) {
        return new PostResult(false, null, 0, errorMessage, 403);
    }

    public static PostResult conflict(String errorMessage) {
        return new PostResult(false, null, 0, errorMessage, 409);
    }

    public static PostResult invalid(String errorMessage) {
        return new PostResult(false, null, 0, errorMessage, 422);
    }
    
    public static PostResult notFound(String errorMessage) {
        return new PostResult(false, null, 0, errorMessage, 404);
    }
    
    public static PostResult failed(String errorMessage) {
        return new PostResult(false, null, 0, errorMessage, 500);
    }
    
    public boolean isSuccess() {
        return success;
    }
}
