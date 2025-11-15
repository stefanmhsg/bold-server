package org.bold.maze;

import java.util.List;

import org.bold.maze.rules.MazeRule;
import org.bold.maze.rules.MazeRuleEngine;
import org.bold.maze.rules.MazeRuleLoader;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main game engine for the maze navigation system.
 * Coordinates access control, agent tracking, movement validation, and dynamic rule execution.
 */
public class MazeGameEngine {
    
    private static final Logger log = LoggerFactory.getLogger(MazeGameEngine.class);
    
    // Namespace constant for the maze vocabulary
    private static final String MAZE_NS = "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#";
    
    private final SailRepository repository;
    private final MazeAccessControl accessControl;
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
        this(repository, mazeName, java.util.Collections.singletonList("Global"));
    }
    
    /**
     * Creates a MazeGameEngine with maze-specific rules and additional global rulesets.
     * 
     * @param repository The RDF repository
     * @param mazeName The maze name (e.g., "UnsafeMaze", "BigMaze"), or null for generic rules
     * @param additionalRulesets List of additional ruleset directory paths (e.g., ["Global", "Global/Stigmergy"])
     */
    public MazeGameEngine(SailRepository repository, String mazeName, List<String> additionalRulesets) {
        this.repository = repository;
        this.accessControl = new MazeAccessControl(repository);
        this.pathTracker = new MazePathTracker("agent-paths");
        this.requestTracker = new MazeRequestTracker("agent-requests");
        
        // Initialize rule engine with loaded rules
        MazeRuleLoader ruleLoader = new MazeRuleLoader();
        List<String> ruleFiles = ruleLoader.discoverRuleFiles(mazeName);
        
        // Load all additional global rulesets if specified
        if (additionalRulesets != null && !additionalRulesets.isEmpty()) {
            for (String ruleset : additionalRulesets) {
                List<String> additionalRuleFiles = ruleLoader.discoverRuleFiles(ruleset);
                ruleFiles.addAll(additionalRuleFiles);
                log.info("Added {} rules from additional ruleset: {}", additionalRuleFiles.size(), ruleset);
            }
        }
        
        List<MazeRule> rules = ruleLoader.loadRules(ruleFiles);
        this.ruleEngine = new MazeRuleEngine(repository, rules);
        
        String rulesetsInfo = additionalRulesets != null && !additionalRulesets.isEmpty() 
                ? " + " + String.join(", ", additionalRulesets) 
                : "";
        log.info("MazeGameEngine initialized{}{} with {} rules", 
                mazeName != null ? " for " + mazeName : "",
                rulesetsInfo,
                rules.size());
    }
    
    /**
     * Validates if an agent can access a requested cell.
     * For GET requests (perception), validates agent can perceive the cell from current location.
     * For MOVE requests (action), validates the movement and records it.
     * For POST requests (interaction), validates agent is at the target cell.
     * 
     * @param agentName the agent attempting access
     * @param requestedCellUri the URI of the cell being requested
     * @param operation the operation type: "GET" for perception, "MOVE" for movement, "POST" for interaction
     * @return AccessResult containing whether access is allowed and a message
     */
    public AccessResult validateAccess(String agentName, String requestedCellUri, String operation) {
        // Non-cell resources are always accessible
        if (!isCellResource(requestedCellUri)) {
            requestTracker.recordRequest(agentName, requestedCellUri, operation, true);
            return AccessResult.allowed();
        }
        
        // If no agent name provided, allow access (no tracking)
        if (agentName == null || agentName.trim().isEmpty()) {
            return AccessResult.allowed();
        }
        
        // Build agent URI from requested cell URI. 
        //TODO: use hMAS ontology
        String agentUri = buildAgentUri(requestedCellUri, agentName);
        
        // Find current location from RDF
        String currentLocation = accessControl.findAgentLocation(agentUri);
        
        // First time access - must be at entrance
        if (currentLocation == null) {
            // For MOVE operations, allow entrance cell as initial position
            if ("MOVE".equals(operation)) {
                if (!accessControl.isEntranceCell(requestedCellUri)) {
                    log.warn("Agent {} has no location, attempting to move to {} - denied (not entrance)", 
                             agentName, requestedCellUri);
                    requestTracker.recordRequest(agentName, requestedCellUri, operation, false);
                    return AccessResult.denied("Access denied. Agent must start at the entrance cell (check xhv:start in /maze).");
                }
                
                // Allow first move to entrance
                log.info("Agent {} starting at entrance: {}", agentName, requestedCellUri);
                requestTracker.recordRequest(agentName, requestedCellUri, operation, true);
                return AccessResult.allowed();
            } else {
                // For GET and POST, deny if agent has no location
                log.warn("Agent {} has no location, cannot perceive or modify {}", agentName, requestedCellUri);
                requestTracker.recordRequest(agentName, requestedCellUri, operation, false);
                return AccessResult.denied("Access denied. Agent has no location. Use /move to enter the maze.");
            }
        }
        
        // For GET requests (perception), allow viewing current cell or adjacent cells
        if ("GET".equals(operation)) {
            if (!accessControl.isAccessAllowed(currentLocation, requestedCellUri)) {
                log.warn("Agent {} at {} attempted to perceive {} - denied (not accessible)",
                         agentName, currentLocation, requestedCellUri);
                requestTracker.recordRequest(agentName, requestedCellUri, operation, false);
                return AccessResult.denied(
                    String.format("Access denied. Cell %s is not perceivable from your current location %s",
                                 requestedCellUri, currentLocation));
            }
            
            // Allow perception
            log.info("Agent {} at {} perceiving {}", agentName, currentLocation, requestedCellUri);
            requestTracker.recordRequest(agentName, requestedCellUri, operation, true);
            return AccessResult.allowed();
        }
        
        // For MOVE requests, validate movement
        if ("MOVE".equals(operation)) {
            if (!accessControl.isAccessAllowed(currentLocation, requestedCellUri)) {
                log.warn("Agent {} at {} attempted unauthorized move to {} - denied",
                         agentName, currentLocation, requestedCellUri);
                requestTracker.recordRequest(agentName, requestedCellUri, operation, false);
                return AccessResult.denied(
                    String.format("Access denied. Cell %s is not accessible from your current location %s",
                                 requestedCellUri, currentLocation));
            }
            
            // Allow movement (actual RDF update happens in MoveResource)
            log.info("Agent {} moving from {} to {}", agentName, currentLocation, requestedCellUri);
            pathTracker.recordMovement(agentName, requestedCellUri);
            requestTracker.recordRequest(agentName, requestedCellUri, operation, true);
            
            return AccessResult.allowed();
        }
        
        // For POST requests (interaction), agent must be at the target cell
        if ("POST".equals(operation)) {
            // Agent can only POST to cell they're currently in
            if (!currentLocation.equals(requestedCellUri)) {
                log.warn("Agent {} at {} attempted to POST to {} - denied (not at location)",
                         agentName, currentLocation, requestedCellUri);
                requestTracker.recordRequest(agentName, requestedCellUri, operation, false);
                return AccessResult.denied(
                    String.format("Access denied. You can only POST to your current cell. You are at %s, not %s",
                                 currentLocation, requestedCellUri));
            }
            
            // Allow interaction with current cell
            log.info("Agent {} at {} posting to current cell", agentName, currentLocation);
            requestTracker.recordRequest(agentName, requestedCellUri, operation, true);
            return AccessResult.allowed();
        }
        
        // Unknown operation
        log.warn("Unknown operation type: {}", operation);
        return AccessResult.denied("Unknown operation type: " + operation);
    }
    
    /**
     * Perform an agent movement, updating RDF graphs and executing rules.
     * This is the primary method for moving agents - it handles all game logic.
     * 
     * @param agentName the agent name
     * @param targetCellUri the target cell URI
     * @return MoveResult containing success status and any error message
     */
    public MoveResult performMove(String agentName, String targetCellUri) {
        // First validate the move
        AccessResult validation = validateAccess(agentName, targetCellUri, "MOVE");
        if (!validation.isAllowed()) {
            return MoveResult.failed(validation.getMessage());
        }
        
        // Build agent URI
        String agentUri = buildAgentUri(targetCellUri, agentName);
        
        // Find current location (may be null for first move)
        String currentLocation = accessControl.findAgentLocation(agentUri);
        
        // Step 1: Update agent location in a dedicated transaction
        synchronized (repository) {
            try (SailRepositoryConnection conn = repository.getConnection()) {
                conn.begin();
                
                ValueFactory vf = conn.getValueFactory();
                IRI agent = vf.createIRI(agentUri);
                IRI containsPredicate = vf.createIRI(MAZE_NS + "contains");
                
                IRI targetCell = vf.createIRI(targetCellUri);
                IRI targetCellGraph = vf.createIRI(targetCellUri);

                // Remove agent from current cell (if any) - add move requested triple to target cell for potential stigmergy rules to fire later
                if (currentLocation != null) {
                    IRI currentCell = vf.createIRI(currentLocation);
                    IRI currentCellGraph = vf.createIRI(currentLocation);
                    IRI outgoingPredicate = vf.createIRI(MAZE_NS + "outgoingAgent"); // TODO: use appropriate ontology predicate
                    IRI targetPredicate = vf.createIRI(MAZE_NS + "targetCell"); // TODO: use appropriate ontology predicate

                    // Temporary triples for move event
                    conn.add(currentCell, outgoingPredicate, agent, currentCellGraph);
                    conn.add(agent, targetPredicate, targetCell, currentCellGraph);

                    // Remove from current cell
                    conn.remove(currentCell, containsPredicate, agent, currentCellGraph);
                    log.info("Removed {} from cell graph {}", agentUri, currentLocation);
                }
                
                // Add agent to target cell
                conn.add(targetCell, containsPredicate, agent, targetCellGraph);
                log.info("Added {} to cell graph {}", agentUri, targetCellUri);
                
                conn.commit();
                log.info("Move committed: agent {} from {} to {}", agentName, currentLocation, targetCellUri);
                
            } catch (Exception e) {
                log.error("Error during move operation for agent {}", agentName, e);
                return MoveResult.failed("Internal error during move: " + e.getMessage());
            }
        }
        
        // Step 2: Execute rules AFTER move has committed (rules see new state)
        try {
            log.debug("Executing maze rules after move");
            MazeRuleEngine.RuleExecutionResult ruleResult = ruleEngine.executeRules();
            log.info("Rules executed: {} triggered, {} triples added",
                    ruleResult.getRulesTriggered(), 
                    ruleResult.getTriplesAdded());
        } catch (Exception e) {
            log.error("Error executing rules after move for agent {}", agentName, e);
            // Move succeeded but rules failed - continue
        }
        
        // Step 3: Remove temporary move event triples
        synchronized (repository) {
            try (SailRepositoryConnection conn = repository.getConnection()) {
                conn.begin();
                
                ValueFactory vf = conn.getValueFactory();
                IRI agent = vf.createIRI(agentUri);
                
                if (currentLocation != null) {
                    IRI currentCell = vf.createIRI(currentLocation);
                    IRI outgoingPredicate = vf.createIRI(MAZE_NS + "outgoingAgent");
                    IRI targetPredicate = vf.createIRI(MAZE_NS + "targetCell");
                    
                    // Remove temporary triples
                    conn.remove(currentCell, outgoingPredicate, agent);
                    conn.remove(agent, targetPredicate, vf.createIRI(targetCellUri));
                }
                
                conn.commit();
                log.debug("Cleaned up temporary move event triples for agent {}", agentName);
                
            } catch (Exception e) {
                log.error("Error cleaning up move event triples for agent {}", agentName, e);
                // Not critical - continue
            }
        }

        // Record movement in tracker
        pathTracker.recordMovement(agentName, targetCellUri);
        
        return MoveResult.success(currentLocation, targetCellUri);
    }
    
    /**
     * Build the full URI for an agent based on a cell's base URI.
     */
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
    
    /**
     * Perform a POST operation to merge RDF triples into a graph.
     * Handles validation, merge transaction, and rule execution.
     * Rules are executed AFTER the merge commits to ensure they see the new state.
     * 
     * @param agentName the agent name (may be null for anonymous posts)
     * @param graphIRI the target graph URI
     * @param rdfModel the RDF model to merge
     * @return PostResult containing success status and any error message
     */
    public PostResult performPost(String agentName, String graphIRI, org.eclipse.rdf4j.model.Model rdfModel) {
        // Validate access first (outside transaction)
        AccessResult validation = validateAccess(agentName, graphIRI, "POST");
        if (!validation.isAllowed()) {
            return PostResult.denied(validation.getMessage());
        }
        
        int triplesAdded = rdfModel.size();
        
        // Step 1: Merge triples in a dedicated transaction
        synchronized (repository) {
            try (SailRepositoryConnection conn = repository.getConnection()) {
                ValueFactory vf = conn.getValueFactory();
                IRI graphName = vf.createIRI(graphIRI);
                
                // Check graph exists
                boolean exists = conn.hasStatement(null, null, null, false, graphName);
                if (!exists) {
                    log.info("POST to non-existent graph: {}", graphIRI);
                    return PostResult.notFound("Graph not found: " + graphIRI);
                }
                
                conn.begin();
                
                // Add the model to the graph
                conn.add(rdfModel);
                log.debug("Added {} triples to graph {}", triplesAdded, graphIRI);
                
                conn.commit();
                log.info("Merge committed: {} triples added to {}", triplesAdded, graphIRI);
                
            } catch (Exception e) {
                log.error("Error during POST merge to {}", graphIRI, e);
                return PostResult.failed("Internal error during POST: " + e.getMessage());
            }
        }
        
        // Step 2: Execute rules AFTER merge has committed (rules see new state)
        MazeRuleEngine.RuleExecutionResult ruleResult;
        try {
            log.debug("Executing maze rules after POST to {}", graphIRI);
            ruleResult = ruleEngine.executeRules();
            
            if (ruleResult.hasChanges()) {
                log.info("Rules execution result: {}", ruleResult);
            }
        } catch (Exception e) {
            log.error("Error executing rules after POST to {}", graphIRI, e);
            // Merge succeeded but rules failed - return partial success
            return PostResult.success(graphIRI, triplesAdded, 0);
        }
        
        log.info("POST successful: merged {} triples into {}, {} rules triggered",
                triplesAdded, graphIRI, ruleResult.getRulesTriggered());
        
        return PostResult.success(graphIRI, triplesAdded, ruleResult.getRulesTriggered());
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
    
    /**
     * Result of a move operation.
     */
    public static class MoveResult {
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
    
    /**
     * Result of a POST operation.
     */
    public static class PostResult {
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
}
