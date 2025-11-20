package org.maze.application.services;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.domain.model.AccessResult;
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
    private final AccessValidator accessValidator;
    
    /**
     * Create a new POST handler.
     * 
     * @param repository the RDF repository
     * @param lockManager the lock manager for graph-level locking
     * @param accessValidator the access validator
     */
    public PostHandler(SailRepository repository,
                       GraphLockManager lockManager,
                       AccessValidator accessValidator) {
        this.repository = repository;
        this.lockManager = lockManager;
        this.accessValidator = accessValidator;
    }
    
    /**
     * Perform a POST operation to merge RDF triples into a graph.
     * Handles validation and merge transaction.
     * Rules are executed by MazeGameEngine after this completes successfully.
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
        
        // Merge triples with fine-grained locking on the target graph
        try {
            mergeTriples(graphIRI, rdfModel, triplesAdded);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Graph not found")) {
                return PostResult.notFound(e.getMessage());
            }
            return PostResult.failed(e.getMessage());
        }
        
        log.info("POST successful: merged {} triples into {}", triplesAdded, graphIRI);
        
        return PostResult.success(graphIRI, triplesAdded);
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
}
