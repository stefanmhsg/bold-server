package org.maze.domain.utils;

/**
 * Utility class for handling agent authorization in the maze game.
 */
public class AgentAuthUtil {
    
    /**
     * Extract agent name from Authorization header.
     * Supports formats: "Agent agentname" or "agentname".
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

    /**
     * Extract agent name from Authorization header.
     * Supports formats: "Agent agentname" or "agentname" or "baseuri/agents/agentname".
     * 
     * @param authorization the Authorization header value
     * @param requestedCellUri the URI of the requested cell
     * @return the extracted agent name, or null if no valid name found
     */
    public static String extractAgentName(String authorization, String requestedCellUri) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        
        String auth = authorization.trim();
        String baseUri = extractBaseUri(requestedCellUri).toLowerCase();
        String agentBaseUri = baseUri + "/agents/";

        if (auth.toLowerCase().startsWith(agentBaseUri)) {
            return auth.substring(agentBaseUri.length()).trim();
        }
        
        // Support "Agent agentname" format
        if (auth.toLowerCase().startsWith("agent ")) {
            return auth.substring(6).trim();
        }
        
        // Support plain agent name
        return auth;
    }

    /**
     * Extract base URI from a cell URI (removes /cells/... suffix).
     */
    private static String extractBaseUri(String cellUri) {
        int cellsIndex = cellUri.lastIndexOf("/cells");
        if (cellsIndex == -1) {
            // Fallback: use up to last slash
            int lastSlash = cellUri.lastIndexOf("/");
            return cellUri.substring(0, lastSlash);
        }
        return cellUri.substring(0, cellsIndex);
    }    
}
