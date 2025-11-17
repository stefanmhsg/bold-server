package org.maze.application.services;

import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.application.MazeRuleEngine;
import org.maze.domain.model.AccessResult;
import org.maze.domain.model.PostResult;
import org.maze.domain.model.RuleExecutionResult;
import org.maze.infrastructure.concurrency.GraphLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for handling POST operations to RDF graphs.
 * Manages the merge of RDF triples, transaction handling, and rule execution.
 * 
 * POST workflow:
 * 1. Validate agent can POST to target graph
 * 2. Merge RDF triples into graph (transaction)
 * 3. Execute rules to handle consequences
 * 4. Return result with statistics
 */
public class PostHandler {
    
    private static final Logger log = LoggerFactory.getLogger(PostHandler.class);
    
    private final SailRepository repository;
    private final MazeRuleEngine ruleEngine;
    private final GraphLockManager lockManager;
    private final AccessValidator accessValidator;
    
    /**
     * Create a new POST handler.
     * 
     * @param repository the RDF repository
     * @param ruleEngine the rule engine for executing post-merge rules
     * @param lockManager the lock manager for graph-level locking
     * @param accessValidator the access validator
     */
    public PostHandler(SailRepository repository,
                       MazeRuleEngine ruleEngine,
                       GraphLockManager lockManager,
                       AccessValidator accessValidator) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
        this.lockManager = lockManager;
        this.accessValidator = accessValidator;
    }
    
    /**
     * Perform a POST operation to merge RDF triples into a graph.
     * Handles validation, merge transaction, and rule execution.
     * Rules are executed AFTER the merge commits to ensure they see the new state.
     * 
     * @param agentName the agent name (may be null for anonymous posts)
     * @param graphIRI the target graph URI
     * @param rdfModel the RDF model to merge
     * @param agentUri the full agent URI
     * @return PostResult containing success status and any error message
     */
    public PostResult performPost(String agentName, String graphIRI, Model rdfModel, String agentUri) {
        // Validate access first (outside transaction)
        AccessResult validation = accessValidator.validateAccess(agentName, graphIRI, "POST", agentUri);
        if (!validation.isAllowed()) {
            return PostResult.denied(validation.message());
        }
        
        int triplesAdded = rdfModel.size();
        
        // Step 1: Merge triples with fine-grained locking on the target graph
        try {
            mergeTriples(graphIRI, rdfModel, triplesAdded);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Graph not found")) {
                return PostResult.notFound(e.getMessage());
            }
            return PostResult.failed(e.getMessage());
        }
        
        // Step 2: Execute rules AFTER merge has committed (rules see new state)
        RuleExecutionResult ruleResult = executePostMergeRules(graphIRI, triplesAdded);
        
        log.info("POST successful: merged {} triples into {}, {} rules triggered",
                triplesAdded, graphIRI, ruleResult.rulesTriggered());
        
        return PostResult.success(graphIRI, triplesAdded, ruleResult.rulesTriggered());
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
     * Execute rules after POST merge to handle consequences.
     */
    private RuleExecutionResult executePostMergeRules(String graphIRI, int triplesAdded) {
        try {
            log.debug("Executing maze rules after POST to {}", graphIRI);
            RuleExecutionResult ruleResult = ruleEngine.executeRules();
            
            if (ruleResult.hasChanges()) {
                log.info("Rules execution result: {}", ruleResult);
            }
            
            return ruleResult;
        } catch (Exception e) {
            log.error("Error executing rules after POST to {}", graphIRI, e);
            // Merge succeeded but rules failed - return partial success with no rules triggered
            return new RuleExecutionResult(0, triplesAdded, List.of());
        }
    }
}
