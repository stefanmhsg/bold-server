package org.maze.application;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
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
        String currentLocation = findAgentLocation(agentUri);
        
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
        // Non-cell resources are always accessible
        if (!isCellResource(requestedCellUri)) {
            return AccessResult.allow();
        }
        
        // If no agent name provided, allow access (no tracking)
        if (agentName == null || agentName.trim().isEmpty()) {
            return AccessResult.allow();
        }
        
        // Extract entersFrom cell - must have exactly one
        String entersFromCell = extractEntersFromCell(rdfModel);
        if (entersFromCell == null) {
            return AccessResult.deny("Invalid movement request: must contain exactly one " + MazeVocab.ENTERS_FROM + " triple");
        }
        
        String agentUri = buildAgentUri(requestedCellUri, agentName);
        String currentLocation = findAgentLocation(agentUri);
        
        // First-time entrance: entering from /maze to entrance cell
        if (currentLocation == null) {
            // Verify entering from /maze graph
            String mazeGraphUri = extractBaseUri(requestedCellUri) + "/maze";
            if (!entersFromCell.equals(mazeGraphUri)) {
                log.warn("Agent {} has no location, attempting to enter from {} instead of /maze", 
                         agentName, entersFromCell);
                return AccessResult.deny(String.format("Access denied. First entry must be from /maze graph. Found: %s", entersFromCell));
            }
            
            // Verify target is entrance cell
            if (!isEntranceCell(requestedCellUri)) {
                log.warn("Agent {} attempting first entry to {} which is not the entrance", 
                         agentName, requestedCellUri);
                return AccessResult.deny(String.format("Access denied. First entry must be to entrance cell (check %s in /maze).", MazeVocab.START));
            }
            
            log.info("Agent {} starting at entrance: {}", agentName, requestedCellUri);

            // Create Graph for Agent IRI when entering the maze
            createAgentGraph(agentUri);

            return AccessResult.allow();
        }
        
        // Agent has location - validate movement from current cell
        if (!currentLocation.equals(entersFromCell)) {
            log.warn("Agent {} at {} attempting to move from {} - denied (not at source)",
                     agentName, currentLocation, entersFromCell);
            return AccessResult.deny(
                String.format("Access denied. You claim to enter from %s but you are at %s",
                             entersFromCell, currentLocation));
        }
        
        // Validate adjacency from source to target
        if (!isAdjacent(entersFromCell, requestedCellUri)) {
            log.warn("Agent {} attempting to move from {} to {} - denied (not adjacent)",
                     agentName, entersFromCell, requestedCellUri);
            return AccessResult.deny(
                String.format("Access denied. Cell %s is not accessible from %s (no connection in graph)",
                             requestedCellUri, entersFromCell));
        }
        
        log.info("Agent {} moving from {} to {}", agentName, entersFromCell, requestedCellUri);
        return AccessResult.allow();
    }
    
    /**
     * Validate GET and POST request - agent must be at the target cell.
     */
    private AccessResult validateInteraction(String agentName, String requestedCellUri, String currentLocation) {
        // Agent can only POST to and GET from cells they're currently in
        if (!currentLocation.equals(requestedCellUri)) {
            log.warn("Agent {} at {} attempted to POST to {} - denied (not at location)",
                     agentName, currentLocation, requestedCellUri);
            return AccessResult.deny(
                String.format("Access denied. You can only POST to your current cell. You are at %s, not %s",
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
    private String extractEntersFromCell(Model model) {
        try {
            ValueFactory vf = repository.getValueFactory();
            IRI entersFromPredicate = vf.createIRI(MazeVocab.ENTERS_FROM);
            
            Set<Value> objects = model.filter(null, entersFromPredicate, null)
                .stream()
                .map(statement -> statement.getObject())
                .collect(Collectors.toSet());
            
            if (objects.isEmpty()) {
                log.debug("No entersFrom statement found in model");
                return null;
            }
            
            if (objects.size() > 1) {
                log.warn("Multiple entersFrom statements found in model: {}", objects);
                return null;
            }
            
            String sourceCell = objects.iterator().next().stringValue();
            log.debug("Extracted entersFrom cell: {}", sourceCell);
            return sourceCell;
            
        } catch (Exception e) {
            log.error("Error extracting entersFrom from model", e);
            return null;
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
            
        } catch (Exception e) {
            log.error("Error finding location for agent {}", agentUri, e);
            return null;
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
            
        } catch (Exception e) {
            log.error("Error checking adjacency from {} to {}", sourceCellUri, targetCellUri, e);
            return false;
        }
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
        // Check cache first
        if (cachedEntranceCell != null) {
            return cachedEntranceCell.equals(cellUri);
        }
        
        try (SailRepositoryConnection connection = repository.getConnection()) {
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
            
        } catch (Exception e) {
            log.error("Error checking if {} is entrance", cellUri, e);
            return false;
        }
    }

    /**
     * Create an empty named graph for the agent's IRI.
     * This graph can be used to store agent-specific data and metadata.
     * 
     * @param agentUri the full URI of the agent
     */
    private void createAgentGraph(String agentUri) {
        try {
            // SPARQL UPDATE to create agent graph with metadata triple
            String sparqlUpdate = 
                "PREFIX maze: <" + MazeVocab.MAZE_NS + "> \n" +
                "INSERT DATA { \n" +
                "  GRAPH <" + agentUri + "> { \n" +
                "    <" + agentUri + "> a maze:Agent . \n" +
                "  } \n" +
                "}";
            
            log.debug("Creating agent graph with SPARQL: {}", sparqlUpdate);
            
            var result = sparqlService.executeQuery(sparqlUpdate, null);
            
            if (result.success()) {
                log.info("Successfully created named graph for agent: {}", agentUri);
            } else {
                log.error("Failed to create agent graph for {}: {}", agentUri, result.errorMessage());
            }
            
        } catch (Exception e) {
            log.error("Exception while creating agent graph for {}", agentUri, e);
        }
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
}
