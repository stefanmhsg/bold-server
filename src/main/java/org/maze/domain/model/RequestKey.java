package org.maze.domain.model;

import java.util.Objects;

/**
 * Key for tracking repeated requests in the maze system.
 * Uniquely identifies a request by agent name, cell URI, and operation type.
 */
public class RequestKey {
    private final String agentName;
    private final String cellUri;
    private final String operation;
    
    public RequestKey(String agentName, String cellUri, String operation) {
        this.agentName = agentName;
        this.cellUri = cellUri;
        this.operation = operation;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public String getCellUri() {
        return cellUri;
    }
    
    public String getOperation() {
        return operation;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestKey that = (RequestKey) o;
        return Objects.equals(agentName, that.agentName) && 
               Objects.equals(cellUri, that.cellUri) && 
               Objects.equals(operation, that.operation);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(agentName, cellUri, operation);
    }
}
