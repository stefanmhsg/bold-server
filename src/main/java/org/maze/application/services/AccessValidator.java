package org.maze.application.services;

import org.maze.domain.model.AccessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AccessValidator {
    
    private static final Logger log = LoggerFactory.getLogger(AccessValidator.class);
    
    private final MazeAccessControl accessControl;
    
    public AccessValidator(MazeAccessControl accessControl) {
        this.accessControl = accessControl;
    }
    
    /**
     * Validates if an agent can access a requested cell.
     * For GET requests (perception), validates agent can perceive the cell from current location.
     * For MOVE requests (action), validates the movement and records it.
     * For POST requests (interaction), validates agent is at the target cell.
     * 
     * @param agentName the agent attempting access
     * @param requestedCellUri the URI of the cell being requested
     * @param operation the operation type: "GET", "MOVE", or "POST"
     * @param agentUri the full URI of the agent
     * @return AccessResult containing whether access is allowed and a message
     */
    public AccessResult validateAccess(String agentName, String requestedCellUri, 
                                       String operation) {
        // Non-cell resources are always accessible
        if (!isCellResource(requestedCellUri)) {
            return AccessResult.allow();
        }
        
        // If no agent name provided, allow access (no tracking)
        if (agentName == null || agentName.trim().isEmpty()) {
            return AccessResult.allow();
        }

        String agentUri = buildAgentUri(requestedCellUri, agentName);
        
        // Find current location from RDF
        String currentLocation = accessControl.findAgentLocation(agentUri);
        
        // First time access - must be at entrance
        if (currentLocation == null) {
            return validateFirstAccess(agentName, requestedCellUri, operation);
        }
        
        // Route to specific validation based on operation type
        return switch (operation) {
            case "GET" -> validatePerception(agentName, requestedCellUri, currentLocation);
            case "POST" -> validateInteraction(agentName, requestedCellUri, currentLocation);
            default -> {
                log.warn("Unknown operation type: {}", operation);
                yield AccessResult.deny("Unknown operation type: " + operation);
            }
        };
    }
    
    /**
     * Validate first-time access when agent has no current location.
     */
    private AccessResult validateFirstAccess(String agentName, String requestedCellUri, String operation) {
        // For MOVE operations, allow entrance cell as initial position
        if ("MOVE".equals(operation)) {
            if (!accessControl.isEntranceCell(requestedCellUri)) {
                log.warn("Agent {} has no location, attempting to move to {} - denied (not entrance)", 
                         agentName, requestedCellUri);
                return AccessResult.deny("Access denied. Agent must start at the entrance cell (check xhv:start in /maze).");
            }
            
            // Allow first move to entrance
            log.info("Agent {} starting at entrance: {}", agentName, requestedCellUri);
            return AccessResult.allow();
        } else {
            // For GET and POST, deny if agent has no location
            log.warn("Agent {} has no location, cannot perceive or modify {}", agentName, requestedCellUri);
            return AccessResult.deny("Access denied. Agent has no location. Use /move to enter the maze.");
        }
    }
    
    /**
     * Validate GET request (perception) - agent can view current or adjacent cells.
     */
    private AccessResult validatePerception(String agentName, String requestedCellUri, String currentLocation) {
        if (!accessControl.isAccessAllowed(currentLocation, requestedCellUri)) {
            log.warn("Agent {} at {} attempted to perceive {} - denied (not accessible)",
                     agentName, currentLocation, requestedCellUri);
            return AccessResult.deny(
                String.format("Access denied. Cell %s is not perceivable from your current location %s",
                             requestedCellUri, currentLocation));
        }
        
        // Allow perception
        log.info("Agent {} at {} perceiving {}", agentName, currentLocation, requestedCellUri);
        return AccessResult.allow();
    }
    
    
    /**
     * Validate POST request (interaction) - agent must be at the target cell.
     */
    private AccessResult validateInteraction(String agentName, String requestedCellUri, String currentLocation) {
        // Agent can only POST to cell they're currently in
        if (!currentLocation.equals(requestedCellUri)) {
            log.warn("Agent {} at {} attempted to POST to {} - denied (not at location)",
                     agentName, currentLocation, requestedCellUri);
            return AccessResult.deny(
                String.format("Access denied. You can only POST to your current cell. You are at %s, not %s",
                             currentLocation, requestedCellUri));
        }
        
        // Allow interaction with current cell
        log.info("Agent {} at {} posting to current cell", agentName, currentLocation);
        return AccessResult.allow();
    }
    
    /**
     * Check if a URI represents a cell resource (as opposed to maze metadata).
     */
    private boolean isCellResource(String uri) {
        return uri.contains("/cells/");
    }
    
    private String buildAgentUri(String cellUri, String agentName) {
        int cellsIndex = cellUri.lastIndexOf("/cells");
        if (cellsIndex == -1) {
            // Fallback: use cell URI as base
            int lastSlash = cellUri.lastIndexOf("/");
            return cellUri.substring(0, lastSlash + 1) + "agents/" + agentName;
        }
        String baseUri = cellUri.substring(0, cellsIndex);
        return baseUri + "/agents/" + agentName;
    }
    
}
