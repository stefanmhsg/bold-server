package org.maze.domain.utils;

/**
 * Utility class for handling agent authorization in the maze game.
 */
public class AgentAuthUtil {
    
    /**
     * Extract agent name from Authorization header.
     * Supports formats: "Agent agentname" or just "agentname"
     * 
     * @param authorization the Authorization header value
     * @return the extracted agent name, or null if no valid name found
     */
    public static String extractAgentName(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        
        String auth = authorization.trim();
        
        // Support "Agent agentname" format
        if (auth.toLowerCase().startsWith("agent ")) {
            return auth.substring(6).trim();
        }
        
        // Support plain agent name
        return auth;
    }
}
