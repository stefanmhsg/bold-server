package org.maze.domain.model;

/**
 * Key for tracking repeated requests in the maze system.
 * Uniquely identifies a request by agent name, cell URI, and operation type.
 */
public record RequestKey(String agentName, String cellUri, String operation) {
}
