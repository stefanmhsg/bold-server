package org.maze.domain.model;

/**
 * Result of a POST operation.
 * Contains information about the operation's success, graph modifications, and triggered rules.
 */
public class PostResult {
    private final boolean success;
    private final String graphUri;
    private final int triplesAdded;
    private final int rulesTriggered;
    private final String errorMessage;
    private final int statusCode;
    
    private PostResult(boolean success, String graphUri, int triplesAdded, 
                      int rulesTriggered, String errorMessage, int statusCode) {
        this.success = success;
        this.graphUri = graphUri;
        this.triplesAdded = triplesAdded;
        this.rulesTriggered = rulesTriggered;
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
    }
    
    public static PostResult success(String graphUri, int triplesAdded, int rulesTriggered) {
        return new PostResult(true, graphUri, triplesAdded, rulesTriggered, null, 201);
    }
    
    public static PostResult denied(String errorMessage) {
        return new PostResult(false, null, 0, 0, errorMessage, 403);
    }
    
    public static PostResult notFound(String errorMessage) {
        return new PostResult(false, null, 0, 0, errorMessage, 404);
    }
    
    public static PostResult failed(String errorMessage) {
        return new PostResult(false, null, 0, 0, errorMessage, 500);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getGraphUri() {
        return graphUri;
    }
    
    public int getTriplesAdded() {
        return triplesAdded;
    }
    
    public int getRulesTriggered() {
        return rulesTriggered;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
}
