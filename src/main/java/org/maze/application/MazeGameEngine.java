package org.maze.application;

import java.util.List;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.maze.application.services.AccessValidator;
import org.maze.application.services.MovementCoordinator;
import org.maze.application.services.PostHandler;
import org.maze.application.services.SparqlService;
import org.maze.application.tracking.MazeAccessControl;
import org.maze.application.tracking.MazePathTracker;
import org.maze.application.tracking.MazeRequestTracker;
import org.maze.domain.model.AccessResult;
import org.maze.domain.model.MoveResult;
import org.maze.domain.model.PostResult;
import org.maze.domain.model.RuleExecutionResult;
import org.maze.domain.model.SparqlResult;
import org.maze.domain.rules.MazeRule;
import org.maze.infrastructure.concurrency.GraphLockManager;
import org.maze.infrastructure.storage.MazeRuleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator for the maze navigation system.
 * Delegates to specialized services for access validation, movement, and POST operations.
 * 
 * <p>This class acts as a facade, providing a simple API while delegating to:
 * <ul>
 *   <li>{@link AccessValidator} - validates agent access to cells</li>
 *   <li>{@link MovementCoordinator} - handles agent movement</li>
 *   <li>{@link PostHandler} - handles RDF triple merging</li>
 *   <li>{@link SparqlService} - executes SPARQL queries</li>
 * </ul>
 * 
 * <p>This orchestrator keeps the public API stable while the internal service architecture
 * provides better separation of concerns and testability.</p>
 */
public class MazeGameEngine {
    
    private static final Logger log = LoggerFactory.getLogger(MazeGameEngine.class);
    
    private final MazeRuleEngine ruleEngine;
    private final AccessValidator accessValidator;
    private final MovementCoordinator movementCoordinator;
    private final PostHandler postHandler;
    private final SparqlService sparqlService;
    
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
        // Initialize components
        MazeAccessControl accessControl = new MazeAccessControl(repository);
        MazePathTracker pathTracker = new MazePathTracker("agent-paths");
        MazeRequestTracker requestTracker = new MazeRequestTracker("agent-requests");
        GraphLockManager lockManager = new GraphLockManager();
        
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
        
        // Initialize services that handle core operations
        this.accessValidator = new AccessValidator(accessControl, requestTracker, pathTracker);
        this.movementCoordinator = new MovementCoordinator(repository, accessControl, pathTracker, 
                                                           ruleEngine, lockManager, accessValidator);
        this.postHandler = new PostHandler(repository, ruleEngine, lockManager, accessValidator);
        this.sparqlService = new SparqlService(repository);
        
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
     * Delegates to {@link AccessValidator} for the actual validation logic.
     * 
     * @param agentName the agent attempting access
     * @param requestedCellUri the URI of the cell being requested
     * @param operation the operation type: "GET", "MOVE", or "POST"
     * @return AccessResult containing whether access is allowed and a message
     */
    public AccessResult validateAccess(String agentName, String requestedCellUri, String operation) {
        String agentUri = buildAgentUri(requestedCellUri, agentName);
        return accessValidator.validateAccess(agentName, requestedCellUri, operation, agentUri);
    }

    /**
     * Perform an agent movement, updating RDF graphs and executing rules.
     * Delegates to {@link MovementCoordinator} for the actual movement logic.
     * 
     * @param agentName the agent name
     * @param targetCellUri the target cell URI
     * @return MoveResult containing success status and any error message
     */
    public MoveResult performMove(String agentName, String targetCellUri) {
        String agentUri = buildAgentUri(targetCellUri, agentName);
        return movementCoordinator.performMove(agentName, targetCellUri, agentUri);
    }
    
    /**
     * Perform a POST operation to merge RDF triples into a graph.
     * Delegates to {@link PostHandler} for the actual POST logic.
     * 
     * @param agentName the agent name (may be null for anonymous posts)
     * @param graphIRI the target graph URI
     * @param rdfModel the RDF model to merge
     * @return PostResult containing success status and any error message
     */
    public PostResult performPost(String agentName, String graphIRI, org.eclipse.rdf4j.model.Model rdfModel) {
        String agentUri = buildAgentUri(graphIRI, agentName);
        return postHandler.performPost(agentName, graphIRI, rdfModel, agentUri);
    }
    
    /**
     * Execute maze rules after a state change.
     * This checks all rules and applies any that are triggered.
     * 
     * @return Result of rule execution including number of rules triggered
     */
    public RuleExecutionResult executeRules() {
        log.debug("Executing maze rules after state change");
        return ruleEngine.executeRules();
    }
    
    /**
     * Execute a SPARQL query against the repository.
     * Supports SELECT, CONSTRUCT, ASK, DESCRIBE, and UPDATE queries.
     * 
     * @param queryString the SPARQL query to execute
     * @param acceptHeader the Accept header for content negotiation (may be null)
     * @return SparqlResult containing the query results or error
     */
    public SparqlResult executeSparqlQuery(String queryString, String acceptHeader) {
        log.debug("Executing SPARQL query via game engine");
        return sparqlService.executeQuery(queryString, acceptHeader);
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
     * Build the full URI for an agent based on a cell's base URI.
     * Extracts the base URI from the cell path and appends /agents/{agentName}.
     * 
     * @param cellUri the cell URI
     * @param agentName the agent name
     * @return the full agent URI
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
}
