package org.maze.application;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.maze.domain.model.AccessResult;
import org.maze.domain.vocab.MazeVocab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates agent access to maze cells for GET, POST, and MOVE operations.
 * Handles location-based access control by querying the RDF graph.
 * Consolidated from AccessValidator and MazeAccessControl for cleaner architecture.
 */
public class AccessValidator {
    
    private static final Logger log = LoggerFactory.getLogger(AccessValidator.class);
    
    private final SailRepository repository;
    private final SparqlService sparqlService;

    // Entrance cell never changes during runtime
    private volatile String cachedEntranceCell = null;
    
    public AccessValidator(SailRepository repository, SparqlService sparqlService) {
        this.repository = repository;
        this.sparqlService = sparqlService;
    }
    
    /**
     * Validates if an agent can access a requested cell.
     * For GET requests (perception), validates agent can perceive the cell from current location.
     * For POST requests (interaction), validates agent is at the target cell.
     * 
     * @param agentName the agent attempting access
     * @param requestedCellUri the URI of the cell being requested
     * @param operation the operation type: "GET" or "POST"
     * @return AccessResult containing whether access is allowed and a message
     */
    public AccessResult validateAccess(String agentName, String requestedCellUri, 
                                       String operation) {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            return validateAccess(agentName, requestedCellUri, operation, connection);
        }
    }

    /**
     * Validates cell access against the caller's transaction snapshot.
     */
    public AccessResult validateAccess(String agentName, String requestedCellUri,
                                       String operation, SailRepositoryConnection connection) {
        // Non-cell resources are always accessible
        if (!isCellResource(requestedCellUri)) {
            return AccessResult.allow();
        }
        
        // If no agent name provided, allow access (no tracking)
        if (agentName == null || agentName.trim().isEmpty()) {
            return AccessResult.allow();
        }

        String agentUri = buildAgentUri(requestedCellUri, agentName);
        
        // Find current location from RDF - null on first entrance
        String currentLocation = findAgentLocation(agentUri, connection);
        
        // First time access - deny GET/POST if agent has no location
        if (currentLocation == null) {
            log.warn("Agent {} has no location, cannot {} {}", agentName, operation, requestedCellUri);
            return AccessResult.deny(String.format("Access denied. Agent has no location. POST to entrance cell with %s to enter the maze.", MazeVocab.ENTERS_FROM));
        }
        
        // Route to specific validation based on operation type
        return switch (operation) {
            case "GET", "POST" -> validateInteraction(agentName, requestedCellUri, currentLocation);
            default -> {
                log.warn("Unknown operation type: {}", operation);
                yield AccessResult.deny("Unknown operation type: " + operation);
            }
        };
    }
    
    /**
     * Validates movement POST with entersFrom triple.
     * Requires the parsed RDF model to extract source cell.
     * 
     * @param agentName the agent attempting to move
     * @param requestedCellUri the target cell URI
     * @param rdfModel the parsed RDF model from POST body
     * @return AccessResult containing whether access is allowed and a message
     */
    public AccessResult validateMove(String agentName, String requestedCellUri, Model rdfModel) {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            return validateMove(agentName, requestedCellUri, rdfModel, connection).access();
        }
    }

    /**
     * Validates an agent POST inside the same transaction that will apply it.
     * In the MASE/Web analogy, Java only enforces embodiment and locality:
     * movement may target an adjacent cell, all other cell interactions must be local.
     */
    public PostAccessDecision validatePost(String agentName, String requestedCellUri, Model rdfModel,
                                           SailRepositoryConnection connection) {
        if (isMovementRequest(rdfModel)) {
            return validateMove(agentName, requestedCellUri, rdfModel, connection);
        }

        AccessResult access = validateAccess(agentName, requestedCellUri, "POST", connection);
        return new PostAccessDecision(PostRequestType.LOCAL_INTERACTION, access, null, null, null);
    }

    /**
     * Validates movement POST with an existing transaction/connection.
     */
    public PostAccessDecision validateMove(String agentName, String requestedCellUri, Model rdfModel,
                                           SailRepositoryConnection connection) {
        // Non-cell resources are always accessible
        if (!isCellResource(requestedCellUri)) {
            return new PostAccessDecision(PostRequestType.LOCAL_INTERACTION, AccessResult.allow(), null, null, null);
        }
        
        // If no agent name provided, allow access (no tracking)
        if (agentName == null || agentName.trim().isEmpty()) {
            return new PostAccessDecision(PostRequestType.MOVEMENT, AccessResult.allow(), null, null, null);
        }
        
        // Extract entersFrom cell - must have exactly one
        MovementIntent movementIntent = extractMovementIntent(rdfModel);
        if (!movementIntent.valid()) {
            return new PostAccessDecision(PostRequestType.MOVEMENT,
                    AccessResult.deny(movementIntent.errorMessage()), null, null, null);
        }
        
        String agentUri = buildAgentUri(requestedCellUri, agentName);
        String entersFromCell = movementIntent.sourceCell();
        if (!movementIntent.agentUri().equals(agentUri)) {
            return new PostAccessDecision(PostRequestType.MOVEMENT,
                    AccessResult.deny("Invalid movement request: " + MazeVocab.ENTERS_FROM
                            + " subject must be the authenticated agent " + agentUri),
                    agentUri, entersFromCell, requestedCellUri);
        }

        String currentLocation = findAgentLocation(agentUri, connection);
        
        // First-time entrance: entering from /maze to entrance cell
        if (currentLocation == null) {
            // Verify entering from /maze graph
            String mazeGraphUri = extractBaseUri(requestedCellUri) + "/maze";
            if (!entersFromCell.equals(mazeGraphUri)) {
                log.warn("Agent {} has no location, attempting to enter from {} instead of /maze", 
                         agentName, entersFromCell);
                return new PostAccessDecision(PostRequestType.MOVEMENT,
                        AccessResult.deny(String.format("Access denied. First entry must be from /maze graph. Found: %s", entersFromCell)),
                        agentUri, entersFromCell, requestedCellUri);
            }
            
            // Verify target is entrance cell
            if (!isEntranceCell(requestedCellUri, connection)) {
                log.warn("Agent {} attempting first entry to {} which is not the entrance", 
                         agentName, requestedCellUri);
                return new PostAccessDecision(PostRequestType.MOVEMENT,
                        AccessResult.deny(String.format("Access denied. First entry must be to entrance cell (check %s in /maze).", MazeVocab.START)),
                        agentUri, entersFromCell, requestedCellUri);
            }
            
            log.info("Agent {} starting at entrance: {}", agentName, requestedCellUri);

            // Create Graph for Agent IRI when entering the maze
            createAgentGraph(agentUri, connection);

            return new PostAccessDecision(PostRequestType.MOVEMENT, AccessResult.allow(), agentUri, entersFromCell, requestedCellUri);
        }
        
        // Agent has location - validate movement from current cell
        if (!currentLocation.equals(entersFromCell)) {
            log.warn("Agent {} at {} attempting to move from {} - denied (not at source)",
                     agentName, currentLocation, entersFromCell);
            return new PostAccessDecision(PostRequestType.MOVEMENT,
                    AccessResult.deny(String.format("Access denied. You claim to enter from %s but you are at %s",
                            entersFromCell, currentLocation)),
                    agentUri, entersFromCell, requestedCellUri);
        }
        
        // Validate adjacency from source to target
        if (!isAdjacent(entersFromCell, requestedCellUri, connection)) {
            log.warn("Agent {} attempting to move from {} to {} - denied (not adjacent)",
                     agentName, entersFromCell, requestedCellUri);
            return new PostAccessDecision(PostRequestType.MOVEMENT,
                    AccessResult.deny(String.format("Access denied. Cell %s is not accessible from %s (no connection in graph)",
                            requestedCellUri, entersFromCell)),
                    agentUri, entersFromCell, requestedCellUri);
        }
        
        log.info("Agent {} moving from {} to {}", agentName, entersFromCell, requestedCellUri);
        return new PostAccessDecision(PostRequestType.MOVEMENT, AccessResult.allow(), agentUri, entersFromCell, requestedCellUri);
    }
    
    /**
     * Validate GET and POST request - agent must be at the target cell.
     */
    private AccessResult validateInteraction(String agentName, String requestedCellUri, String currentLocation) {
        // Agent can only POST to and GET from cells they're currently in
        if (!currentLocation.equals(requestedCellUri)) {
            log.warn("Agent {} at {} attempted to POST or GET to {} - denied (not at location)",
                     agentName, currentLocation, requestedCellUri);
            return AccessResult.deny(
                String.format("Access denied. You can only POST or GET from your current cell. You are at %s, not %s",
                             currentLocation, requestedCellUri));
        }
        
        // Allow interaction with current cell
        log.info("Agent {} at {} interacting with current cell", agentName, currentLocation);
        return AccessResult.allow();
    }

    // ==================== Helper Methods ====================
    
    /**
     * Extract the entersFrom cell URI from the RDF model.
     * Enforces exactly one entersFrom statement for movement validation.
     * 
     * @param model the RDF model from POST body
     * @return the source cell URI, or null if not found or multiple values
     */
    private boolean isMovementRequest(Model model) {
        ValueFactory vf = repository.getValueFactory();
        IRI entersFromPredicate = vf.createIRI(MazeVocab.ENTERS_FROM);
        return !model.filter(null, entersFromPredicate, null).isEmpty();
    }

    private MovementIntent extractMovementIntent(Model model) {
        try {
            ValueFactory vf = repository.getValueFactory();
            IRI entersFromPredicate = vf.createIRI(MazeVocab.ENTERS_FROM);
            
            Set<String> subjects = model.filter(null, entersFromPredicate, null)
                .stream()
                .map(statement -> statement.getSubject().stringValue())
                .collect(Collectors.toSet());

            Set<Value> objects = model.filter(null, entersFromPredicate, null)
                    .stream()
                    .map(statement -> statement.getObject())
                    .collect(Collectors.toSet());
            
            if (objects.isEmpty()) {
                log.debug("No entersFrom statement found in model");
                return MovementIntent.invalid("Invalid movement request: must contain exactly one " + MazeVocab.ENTERS_FROM + " triple");
            }
            
            if (subjects.size() != 1 || objects.size() != 1) {
                log.warn("Invalid entersFrom statements found in model: subjects={}, objects={}", subjects, objects);
                return MovementIntent.invalid("Invalid movement request: must contain exactly one " + MazeVocab.ENTERS_FROM + " triple");
            }
            
            String agentUri = subjects.iterator().next();
            String sourceCell = objects.iterator().next().stringValue();
            log.debug("Extracted entersFrom cell: {}", sourceCell);
            return MovementIntent.valid(agentUri, sourceCell);
            
        } catch (Exception e) {
            log.error("Error extracting entersFrom from model", e);
            return MovementIntent.invalid("Invalid movement request: could not read " + MazeVocab.ENTERS_FROM + " triple");
        }
    }
    
    /**
     * Find the current location of an agent in the maze.
     * Queries all cell graphs to find which cell contains the specified agent.
     * 
     * @param agentUri the full URI of the agent to locate
     * @return the URI of the cell containing the agent, or null if not found
     */
    private String findAgentLocation(String agentUri) {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            return findAgentLocation(agentUri, connection);
        } catch (Exception e) {
            log.error("Error finding location for agent {}", agentUri, e);
            return null;
        }
    }

    private String findAgentLocation(String agentUri, SailRepositoryConnection connection) {
        String sparql =
            "PREFIX maze: <" + MazeVocab.MAZE_NS + "> \n" +
            "SELECT ?cell WHERE { \n" +
            "  GRAPH ?cell { \n" +
            "    ?cell maze:contains <" + agentUri + "> . \n" +
            "  } \n" +
            "} LIMIT 1";

        log.debug("Finding agent location with SPARQL: {}", sparql);

        var tupleQuery = connection.prepareTupleQuery(sparql);
        try (var result = tupleQuery.evaluate()) {
            if (result.hasNext()) {
                String cellUri = result.next().getValue("cell").stringValue();
                log.info("Agent {} found in cell {}", agentUri, cellUri);
                return cellUri;
            } else {
                log.info("Agent {} not found in any cell", agentUri);
                return null;
            }
        }
    }
    
    /**
     * Check if the target cell is adjacent to the source cell.
     * Queries the RDF graph for directional connections (north, south, east, west, exit).
     * 
     * @param sourceCellUri the URI of the source cell
     * @param targetCellUri the URI of the target cell
     * @return true if cells are adjacent, false otherwise
     */
    private boolean isAdjacent(String sourceCellUri, String targetCellUri) {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            return isAdjacent(sourceCellUri, targetCellUri, connection);
        } catch (Exception e) {
            log.error("Error checking adjacency from {} to {}", sourceCellUri, targetCellUri, e);
            return false;
        }
    }

    private boolean isAdjacent(String sourceCellUri, String targetCellUri, SailRepositoryConnection connection) {
        String sparql =
            "PREFIX maze: <" + MazeVocab.MAZE_NS + "> \n" +
            "ASK { \n" +
            "  GRAPH <" + sourceCellUri + "> { \n" +
            "    <" + sourceCellUri + "> ?direction <" + targetCellUri + "> . \n" +
            "    FILTER(?direction IN (maze:north, maze:south, maze:east, maze:west, maze:exit)) \n" +
            "  } \n" +
            "}";

        log.debug("Checking adjacency with SPARQL: {}", sparql);

        boolean adjacent = connection.prepareBooleanQuery(sparql).evaluate();

        if (adjacent) {
            log.info("Cells are adjacent: {} -> {}", sourceCellUri, targetCellUri);
        } else {
            log.info("Cells are NOT adjacent: {} -> {}", sourceCellUri, targetCellUri);
        }

        return adjacent;
    }
    
    /**
     * Check if the given cell URI is the entrance cell defined in the maze.
     * Queries the /maze graph for xhv:start predicate.
     * Uses caching to avoid repeated queries.
     * 
     * @param cellUri the URI of the cell to check
     * @return true if this is the entrance cell, false otherwise
     */
    private boolean isEntranceCell(String cellUri) {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            return isEntranceCell(cellUri, connection);
        } catch (Exception e) {
            log.error("Error checking if {} is entrance", cellUri, e);
            return false;
        }
    }

    private boolean isEntranceCell(String cellUri, SailRepositoryConnection connection) {
        // Check cache first
        if (cachedEntranceCell != null) {
            return cachedEntranceCell.equals(cellUri);
        }

        String baseUri = extractBaseUri(cellUri);
        String mazeGraphUri = baseUri + "/maze";

        String sparql =
            "PREFIX xhv: <" + MazeVocab.XHV_NS + "> \n" +
            "ASK { \n" +
            "  GRAPH <" + mazeGraphUri + "> { \n" +
            "    ?maze xhv:start <" + cellUri + "> . \n" +
            "  } \n" +
            "}";

        log.debug("Checking entrance with SPARQL: {}", sparql);

        boolean isEntrance = connection.prepareBooleanQuery(sparql).evaluate();

        if (isEntrance) {
            log.info("Cell {} is the entrance (caching)", cellUri);
            cachedEntranceCell = cellUri;
        } else {
            log.info("Cell {} is NOT the entrance", cellUri);
        }

        return isEntrance;
    }

    /**
     * Create an empty named graph for the agent's IRI.
     * This graph can be used to store agent-specific data and metadata.
     * 
     * @param agentUri the full URI of the agent
     */
    private void createAgentGraph(String agentUri) {
        try {
            try (SailRepositoryConnection connection = repository.getConnection()) {
                connection.begin();
                createAgentGraph(agentUri, connection);
                connection.commit();
            }
        } catch (Exception e) {
            log.error("Exception while creating agent graph for {}", agentUri, e);
        }
    }

    private void createAgentGraph(String agentUri, SailRepositoryConnection connection) {
        ValueFactory vf = connection.getValueFactory();
        IRI agent = vf.createIRI(agentUri);
        IRI agentGraph = vf.createIRI(agentUri);
        IRI agentType = vf.createIRI(MazeVocab.MAZE_NS + "Agent");

        // Agent graphs model the embodied web agent as a dereferenceable resource.
        connection.add(agent, RDF.TYPE, agentType, agentGraph);
        log.info("Ensured named graph for agent: {}", agentUri);
    }
    
    /**
     * Check if a URI represents a cell resource (as opposed to maze metadata).
     */
    private boolean isCellResource(String uri) {
        return uri.contains("/cells/");
    }
    
    /**
     * Build agent URI from cell URI and agent name.
     */
    private String buildAgentUri(String cellUri, String agentName) {
        String baseUri = extractBaseUri(cellUri);
        String agentUri = baseUri + "/agents/" ;
        if (agentName.startsWith(agentUri)) {
            return agentName; // Already full URI
        }
        return agentUri + agentName;
    }
    
    /**
     * Extract base URI from a cell URI (removes /cells/... suffix).
     */
    private String extractBaseUri(String cellUri) {
        int cellsIndex = cellUri.lastIndexOf("/cells");
        if (cellsIndex == -1) {
            // Fallback: use up to last slash
            int lastSlash = cellUri.lastIndexOf("/");
            return cellUri.substring(0, lastSlash);
        }
        return cellUri.substring(0, cellsIndex);
    }

    public enum PostRequestType {
        MOVEMENT,
        LOCAL_INTERACTION
    }

    public record PostAccessDecision(PostRequestType type, AccessResult access,
                                     String agentUri, String sourceCell, String targetCell) {
    }

    private record MovementIntent(boolean valid, String agentUri, String sourceCell, String errorMessage) {
        private static MovementIntent valid(String agentUri, String sourceCell) {
            return new MovementIntent(true, agentUri, sourceCell, null);
        }

        private static MovementIntent invalid(String errorMessage) {
            return new MovementIntent(false, null, null, errorMessage);
        }
    }
}
