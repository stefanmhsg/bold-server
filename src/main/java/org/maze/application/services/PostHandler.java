package org.maze.application.services;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.application.MazeRuleService;
import org.maze.domain.model.PostResult;
import org.maze.infrastructure.concurrency.GraphLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for handling POST operations to RDF graphs.
 * Manages the merge of RDF triples and transaction handling.
 * Rules are executed by MazeGameEngine after successful POST.
 * 
 * POST workflow:
 * 1. Validate agent can POST to target graph
 * 2. Merge RDF triples into graph (transaction)
 */
public class PostHandler {
    
    private static final Logger log = LoggerFactory.getLogger(PostHandler.class);
    
    private final SailRepository repository;
    private final GraphLockManager lockManager;
    private final MazeRuleService gameEngine;
    
    /**
     * Create a new POST handler.
     * 
     * @param repository the RDF repository
     * @param lockManager the lock manager for graph-level locking
     * @param gameEngine the game engine for executing rules
     */
    public PostHandler(SailRepository repository,
                       GraphLockManager lockManager,
                       MazeRuleService gameEngine) {
        this.repository = repository;
        this.lockManager = lockManager;
        this.gameEngine = gameEngine;
    }
    
    /**
     * Perform a POST operation to merge RDF triples into a graph.
     * Handles validation and merge transaction.
     * Rules are executed by MazeGameEngine after this completes successfully.
     * 
     * @param agentName the agent name (may be null for anonymous posts)
     * @param graphIRI the target graph URI
     * @param rdfModel the RDF model to merge
     * @return PostResult containing success status and any error message
     */
    public PostResult performPost(String agentName, String graphIRI, Model rdfModel) {
        
        int triplesAdded = rdfModel.size();
        String responseMessage = null;
        
        // Merge triples with fine-grained locking on the target graph
        try {
            mergeTriples(graphIRI, rdfModel, triplesAdded);
            gameEngine.executeRules();
            responseMessage = checkSuccessCondition(agentName);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Graph not found")) {
                return PostResult.notFound(e.getMessage());
            }
            return PostResult.failed(e.getMessage());
        }
        
        log.info("POST successful: merged {} triples into {}", triplesAdded, graphIRI);
        
        return PostResult.success(graphIRI, triplesAdded, responseMessage);
    }
    
    /**
     * Merge RDF triples into the target graph with transaction management.
     */
    private void mergeTriples(String graphIRI, Model rdfModel, int triplesAdded) {
        lockManager.withLock(graphIRI, () -> {
            try (SailRepositoryConnection conn = repository.getConnection()) {
                ValueFactory vf = conn.getValueFactory();
                IRI graphName = vf.createIRI(graphIRI);
                
                // Check graph exists
                boolean exists = conn.hasStatement(null, null, null, false, graphName);
                if (!exists) {
                    log.info("POST to non-existent graph: {}", graphIRI);
                    throw new RuntimeException("Graph not found: " + graphIRI);
                }
                
                conn.begin();
                
                // Add the model to the graph
                conn.add(rdfModel);
                log.debug("Added {} triples to graph {}", triplesAdded, graphIRI);
                
                conn.commit();
                log.info("Merge committed: {} triples added to {}", triplesAdded, graphIRI);
                
            } catch (Exception e) {
                log.error("Error during POST merge to {}", graphIRI, e);
                throw new RuntimeException("Internal error during POST: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Check if success condition has been met (e.g., agent reached exit).
     * 
     * TODO: Implement SPARQL ASK query to check for success conditions.
     * This should query for conditions like:
     * - Agent has reached the exit cell
     * - Agent has completed all objectives
     * - etc.
     * 
     * For now, this is a placeholder that always returns false.
     * 
     * @param agentName the agent to check
     * @return true if success condition met, false otherwise
     */
    public String checkSuccessCondition(String agentName) {
        // TODO: Implement success detection via SPARQL ASK query
        // Example: ASK { ?agent maze:reachedExit true }
        log.debug("Success condition check not yet implemented for agent: {}", agentName);
        return "Success condition check not implemented yet.";
    }

}
