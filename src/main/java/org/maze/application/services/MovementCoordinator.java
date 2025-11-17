package org.maze.application.services;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.application.tracking.MazeAccessControl;
import org.maze.application.tracking.MazePathTracker;
import org.maze.domain.model.AccessResult;
import org.maze.domain.model.MoveResult;
import org.maze.domain.vocab.MazeVocab;
import org.maze.infrastructure.concurrency.GraphLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for coordinating agent movement in the maze.
 * Handles the RDF graph updates and transaction management for moves.
 * Rules are executed by MazeGameEngine after successful move.
 * 
 * Movement workflow:
 * 1. Remove agent from current cell (if any)
 * 2. Add agent to target cell
 * 3. Clean up temporary event triples
 * 4. Record movement in tracker
 */
public class MovementCoordinator {
    
    private static final Logger log = LoggerFactory.getLogger(MovementCoordinator.class);
    
    private final SailRepository repository;
    private final MazeAccessControl accessControl;
    private final MazePathTracker pathTracker;
    private final GraphLockManager lockManager;
    private final AccessValidator accessValidator;
    
    /**
     * Create a new movement coordinator.
     * 
     * @param repository the RDF repository
     * @param accessControl the access control component
     * @param pathTracker the path tracking component
     * @param lockManager the lock manager for graph-level locking
     * @param accessValidator the access validator
     */
    public MovementCoordinator(SailRepository repository,
                               MazeAccessControl accessControl,
                               MazePathTracker pathTracker,
                               GraphLockManager lockManager,
                               AccessValidator accessValidator) {
        this.repository = repository;
        this.accessControl = accessControl;
        this.pathTracker = pathTracker;
        this.lockManager = lockManager;
        this.accessValidator = accessValidator;
    }
    
    /**
     * Perform an agent movement, updating RDF graphs and executing rules.
     * 
     * @param agentName the agent name
     * @param targetCellUri the target cell URI
     * @param agentUri the full agent URI
     * @return MoveResult containing success status and any error message
     */
    public MoveResult performMove(String agentName, String targetCellUri, String agentUri) {
        // First validate the move
        AccessResult validation = accessValidator.validateAccess(agentName, targetCellUri, "MOVE", agentUri);
        if (!validation.isAllowed()) {
            return MoveResult.failed(validation.message());
        }
        
        // Find current location (may be null for first move)
        String currentLocation = accessControl.findAgentLocation(agentUri);
        
        // Step 1: Update agent location with fine-grained locking
        try {
            updateAgentLocation(agentName, agentUri, currentLocation, targetCellUri);
        } catch (RuntimeException e) {
            return MoveResult.failed(e.getMessage());
        }

        // Record movement in tracker
        pathTracker.recordMovement(agentName, targetCellUri);
        
        // NOTE: Temporary move event triples remain for rule execution
        // MazeGameEngine will clean them up AFTER rules execute
        return MoveResult.success(currentLocation, targetCellUri);
    }
    
    /**
     * Update agent location in RDF graphs using fine-grained locking.
     */
    private void updateAgentLocation(String agentName, String agentUri, 
                                     String currentLocation, String targetCellUri) {
        // Lock the graphs involved (current and target cell graphs)
        String[] graphsToLock = currentLocation != null 
            ? new String[]{currentLocation, targetCellUri}
            : new String[]{targetCellUri};
        
        lockManager.withLocks(graphsToLock, () -> {
            try (SailRepositoryConnection conn = repository.getConnection()) {
                conn.begin();
                
                ValueFactory vf = conn.getValueFactory();
                IRI agent = vf.createIRI(agentUri);
                IRI containsPredicate = vf.createIRI(MazeVocab.MAZE_NS + "contains");
                
                IRI targetCell = vf.createIRI(targetCellUri);
                IRI targetCellGraph = vf.createIRI(targetCellUri);

                // Remove agent from current cell (if any) - add move requested triple for stigmergy rules
                if (currentLocation != null) {
                    IRI currentCell = vf.createIRI(currentLocation);
                    IRI currentCellGraph = vf.createIRI(currentLocation);
                    IRI outgoingPredicate = vf.createIRI(MazeVocab.MAZE_NS + "outgoingAgent");
                    IRI targetPredicate = vf.createIRI(MazeVocab.MAZE_NS + "targetCell");

                    // Temporary triples for move event (for rules to detect movement)
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
                throw new RuntimeException("Internal error during move: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Clean up temporary move event triples after rules have executed.
     * Must be called AFTER rules execute so they can see the move event.
     * 
     * @param agentName the agent name
     * @param agentUri the full agent URI
     * @param currentLocation the previous cell location (not null)
     * @param targetCellUri the new cell location
     */
    public void cleanupMoveEventTriples(String agentName, String agentUri, 
                                         String currentLocation, String targetCellUri) {
        lockManager.withLock(currentLocation, () -> {
            try (SailRepositoryConnection conn = repository.getConnection()) {
                conn.begin();
                
                ValueFactory vf = conn.getValueFactory();
                IRI agent = vf.createIRI(agentUri);
                IRI currentCell = vf.createIRI(currentLocation);
                IRI outgoingPredicate = vf.createIRI(MazeVocab.MAZE_NS + "outgoingAgent");
                IRI targetPredicate = vf.createIRI(MazeVocab.MAZE_NS + "targetCell");
                
                // Remove temporary triples
                conn.remove(currentCell, outgoingPredicate, agent);
                conn.remove(agent, targetPredicate, vf.createIRI(targetCellUri));
                
                conn.commit();
                log.debug("Cleaned up temporary move event triples for agent {}", agentName);
                
            } catch (Exception e) {
                log.error("Error cleaning up move event triples for agent {}", agentName, e);
                // Not critical - continue
            }
        });
    }
}
