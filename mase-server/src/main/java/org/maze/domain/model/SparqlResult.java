package org.maze.domain.model;

/**
 * Result of a SPARQL query execution.
 * Contains the serialized result in the requested format and metadata about the query type.
 */
public record SparqlResult(String content, String contentType, QueryType queryType, 
                           boolean success, String errorMessage) {
    
    public enum QueryType {
        SELECT,
        CONSTRUCT,
        ASK,
        DESCRIBE,
        UPDATE
    }
    
    /**
     * Create a successful query result.
     * 
     * @param content the serialized query result
     * @param contentType the MIME type of the result
     * @param queryType the type of SPARQL query
     * @return a successful SparqlResult
     */
    public static SparqlResult success(String content, String contentType, QueryType queryType) {
        return new SparqlResult(content, contentType, queryType, true, null);
    }
    
    /**
     * Create a failed query result.
     * 
     * @param errorMessage the error message
     * @param queryType the type of SPARQL query (if determinable)
     * @return a failed SparqlResult
     */
    public static SparqlResult failed(String errorMessage, QueryType queryType) {
        return new SparqlResult(null, null, queryType, false, errorMessage);
    }
}
