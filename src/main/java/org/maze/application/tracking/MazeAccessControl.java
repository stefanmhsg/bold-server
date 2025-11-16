package org.maze.application.tracking;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles access control logic for the maze navigation system.
 * Determines whether agents can move between cells based on RDF graph connections.
 */
public class MazeAccessControl {
    
    private static final Logger log = LoggerFactory.getLogger(MazeAccessControl.class);
    
    // Namespace constants for the maze vocabulary
    private static final String MAZE_NS = "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#";
    private static final String XHV_NS = "http://www.w3.org/1999/xhtml/vocab#";
    
    private final SailRepository repository;
    
    public MazeAccessControl(SailRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Find the current location of an agent in the maze.
     * Queries all cell graphs to find which cell contains the specified agent.
     * 
     * @param agentUri the full URI of the agent to locate
     * @return the URI of the cell containing the agent, or null if not found
     */
    public String findAgentLocation(String agentUri) {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            // Query all graphs to find which cell contains this agent
            String sparql = 
                "PREFIX maze: <" + MAZE_NS + "> \n" +
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
     * Check if the requested cell is accessible from the current cell.
     * This queries the RDF graph to find valid outgoing connections (north, south, east, west, exit)
     * that are not walls and match the requested cell URI.
     * 
     * @param currentCellUri the URI of the agent's current cell
     * @param requestedCellUri the URI of the cell being requested
     * @return true if access is allowed, false otherwise
     */
    public boolean isAccessAllowed(String currentCellUri, String requestedCellUri) {
        // Same cell - always allowed (re-reading current position)
        if (currentCellUri.equals(requestedCellUri)) {
            return true;
        }
        
        try (SailRepositoryConnection connection = repository.getConnection()) {
            // Build SPARQL query to check if requested cell is accessible from current cell
            String sparql = 
                "PREFIX maze: <" + MAZE_NS + "> \n" +
                "ASK { \n" +
                "  GRAPH <" + currentCellUri + "> { \n" +
                "    <" + currentCellUri + "> ?direction <" + requestedCellUri + "> . \n" +
                "    FILTER(?direction IN (maze:north, maze:south, maze:east, maze:west, maze:exit)) \n" +
                "  } \n" +
                "}";
            
            log.debug("Checking access with SPARQL: {}", sparql);
            
            boolean isAccessible = connection.prepareBooleanQuery(sparql).evaluate();
            
            if (isAccessible) {
                log.info("Access allowed: {} -> {} (connection exists in graph)", 
                        currentCellUri, requestedCellUri);
            } else {
                log.info("Access denied: {} -> {} (no connection in graph)", 
                        currentCellUri, requestedCellUri);
            }
            
            return isAccessible;
            
        } catch (Exception e) {
            log.error("Error checking access from {} to {}", currentCellUri, requestedCellUri, e);
            // Fail closed - deny access on error
            return false;
        }
    }
    
    /**
     * Check if the given cell URI is the entrance cell defined in the maze.
     * Queries the /maze graph for xhv:start predicate.
     * 
     * @param cellUri the URI of the cell to check
     * @return true if this is the entrance cell, false otherwise
     */
    public boolean isEntranceCell(String cellUri) {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            // Query the /maze graph to find the entrance cell
            int cellsIndex = cellUri.lastIndexOf("/cells");
            if (cellsIndex == -1) {
                // Not a cell URI, return false
                log.debug("URI {} does not contain /cells, not checking as entrance", cellUri);
                return false;
            }
            
            String baseUri = cellUri.substring(0, cellsIndex);
            String mazeGraphUri = baseUri + "/maze";
            
            String sparql = 
                "PREFIX xhv: <" + XHV_NS + "> \n" +
                "ASK { \n" +
                "  GRAPH <" + mazeGraphUri + "> { \n" +
                "    ?maze xhv:start <" + cellUri + "> . \n" +
                "  } \n" +
                "}";
            
            log.debug("Checking entrance with SPARQL: {}", sparql);
            
            boolean isEntrance = connection.prepareBooleanQuery(sparql).evaluate();
            
            if (isEntrance) {
                log.info("Cell {} is the entrance", cellUri);
            } else {
                log.info("Cell {} is NOT the entrance", cellUri);
            }
            
            return isEntrance;
            
        } catch (Exception e) {
            log.error("Error checking if {} is entrance", cellUri, e);
            // Fail closed - deny access on error
            return false;
        }
    }
}
