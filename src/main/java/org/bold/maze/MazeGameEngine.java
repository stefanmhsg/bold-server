package org.bold.maze;

import java.util.List;

import org.bold.maze.rules.MazeRule;
import org.bold.maze.rules.MazeRuleEngine;
import org.bold.maze.rules.MazeRuleLoader;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main game engine for the maze navigation system.
 * Coordinates access control, agent tracking, movement validation, and dynamic rule execution.
 */
public class MazeGameEngine {
    
    private static final Logger log = LoggerFactory.getLogger(MazeGameEngine.class);
    
    private final MazeAccessControl accessControl;
    private final AgentLocationTracker locationTracker;
    private final MazePathTracker pathTracker;
    private final MazeRequestTracker requestTracker;
    private final MazeRuleEngine ruleEngine;
    
    /**
     * Creates a MazeGameEngine with generic rules (root-level rules).
     * 
     * @param repository The RDF repository
     */
    public MazeGameEngine(SailRepository repository) {
        this(repository, null);
    }
    
    /**
     * Creates a MazeGameEngine with maze-specific rules.
     * 
     * @param repository The RDF repository
     * @param mazeName The maze name (e.g., "UnsafeMaze", "BigMaze"), or null for generic rules
     */
    public MazeGameEngine(SailRepository repository, String mazeName) {
        this.accessControl = new MazeAccessControl(repository);
        this.locationTracker = new AgentLocationTracker();
        this.pathTracker = new MazePathTracker("agent-paths");
        this.requestTracker = new MazeRequestTracker("agent-requests");
        
        // Initialize rule engine with loaded rules
        MazeRuleLoader ruleLoader = new MazeRuleLoader();
        List<String> ruleFiles = ruleLoader.discoverRuleFiles(mazeName);
        List<MazeRule> rules = ruleLoader.loadRules(ruleFiles);
        this.ruleEngine = new MazeRuleEngine(repository, rules);
        
        log.info("MazeGameEngine initialized{} with {} rules", 
                mazeName != null ? " for " + mazeName : "", rules.size());
    }
    
    /**
     * Validates if an agent can access a requested cell and updates their location if allowed.
     * 
     * @param agentName the agent attempting access
     * @param requestedCellUri the URI of the cell being requested
     * @param method the HTTP method (GET, POST, etc.)
     * @return AccessResult containing whether access is allowed and a message
     */
    public AccessResult validateAccess(String agentName, String requestedCellUri, String method) {
        // Non-cell resources are always accessible
        if (!isCellResource(requestedCellUri)) {
            requestTracker.recordRequest(agentName, requestedCellUri, method, true);
            return AccessResult.allowed();
        }
        
        // If no agent name provided, allow access (no tracking)
        if (agentName == null || agentName.trim().isEmpty()) {
            return AccessResult.allowed();
        }
        
        String currentLocation = locationTracker.getLocation(agentName);
        
        // First time access - must be at entrance
        if (currentLocation == null) {
            if (!accessControl.isEntranceCell(requestedCellUri)) {
                log.warn("Agent {} has no location, attempting to access {} - denied (not entrance)", 
                         agentName, requestedCellUri);
                requestTracker.recordRequest(agentName, requestedCellUri, method, false);
                return AccessResult.denied("Access denied. Agent must start at the entrance cell (check xhv:start in /maze).");
            }
            
            // Allow first access to entrance
            log.info("Agent {} starting at entrance: {}", agentName, requestedCellUri);
            locationTracker.updateLocation(agentName, requestedCellUri);
            pathTracker.recordMovement(agentName, requestedCellUri);
            requestTracker.recordRequest(agentName, requestedCellUri, method, true);
            return AccessResult.allowed();
        }
        
        // Validate access from current location
        if (!accessControl.isAccessAllowed(currentLocation, requestedCellUri)) {
            log.warn("Agent {} at {} attempted unauthorized access to {} - denied",
                     agentName, currentLocation, requestedCellUri);
            requestTracker.recordRequest(agentName, requestedCellUri, method, false);
            return AccessResult.denied(
                String.format("Access denied. Cell %s is not accessible from your current location %s",
                             requestedCellUri, currentLocation));
        }
        
        // Update agent location
        log.info("Agent {} moved from {} to {}", agentName, currentLocation, requestedCellUri);
        locationTracker.updateLocation(agentName, requestedCellUri);
        pathTracker.recordMovement(agentName, requestedCellUri);
        requestTracker.recordRequest(agentName, requestedCellUri, method, true);
        
        return AccessResult.allowed();
    }
    
    /**
     * Execute maze rules after a state change (e.g., POST request).
     * This checks all rules and applies any that are triggered.
     * 
     * @return Result of rule execution including number of rules triggered
     */
    public MazeRuleEngine.RuleExecutionResult executeRules() {
        log.debug("Executing maze rules after state change");
        return ruleEngine.executeRules();
    }
    
    /**
     * Get the rule engine for advanced operations.
     * 
     * @return The maze rule engine
     */
    public MazeRuleEngine getRuleEngine() {
        return ruleEngine;
    }
    
    /**
     * Checks if the URI represents a cell resource (contains "/cells/").
     */
    private boolean isCellResource(String uri) {
        return uri != null && uri.contains("/cells/");
    }
    
    /**
     * Result of an access validation check.
     */
    public static class AccessResult {
        private final boolean allowed;
        private final String message;
        
        private AccessResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }
        
        public static AccessResult allowed() {
            return new AccessResult(true, null);
        }
        
        public static AccessResult denied(String message) {
            return new AccessResult(false, message);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
