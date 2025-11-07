package org.bold.ld;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.POST;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Consumes;

import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;

import org.bold.Configurator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dereference any request URI as an RDF named graph if present in the store.
 * Mounted at "/*" in Jetty, while /gsp/* still maps to the GSP resource.
 * 
 * <p>Location-based access control for maze navigation:</p>
 * <ul>
 *   <li>Agents must provide their name via the "Authorization" header (e.g., "Agent agentname" or just "agentname")</li>
 *   <li>Agents start at the entrance (determined by xhv:start in /maze graph)</li>
 *   <li>GET requests are only allowed for cells reachable from the agent's current position</li>
 *   <li>Valid transitions are determined by querying the RDF graph for maze:north, maze:south, 
 *       maze:east, maze:west, and maze:exit predicates</li>
 *   <li>Locked doors are respected - if a connection is not in the graph (e.g., not yet unlocked 
 *       by posting a key), access is denied</li>
 * </ul>
 */
@Path("/{id: .*}")
public class LinkedDataDereferenceResource {

    private static final Logger log = LoggerFactory.getLogger(LinkedDataDereferenceResource.class);

    @Context
    ServletContext _ctx;

    // Track agent locations: agent name -> current cell URI
    private static final Map<String, String> agentLocations = new ConcurrentHashMap<>();
    
    // Track agent movement paths
    private static final AgentPathTracker pathTracker = new AgentPathTracker("agent-paths");
    
    // Track detailed request information
    private static final AgentRequestTracker requestTracker = new AgentRequestTracker("agent-requests");
    
    // Namespace constants for the maze vocabulary
    private static final String MAZE_NS = "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#";
    private static final String XHV_NS = "http://www.w3.org/1999/xhtml/vocab#";

    @GET
    @Produces({ "text/turtle", "application/ld+json", "application/rdf+xml", "application/n-triples" })
    public Response getGraph(@Context UriInfo uriinfo,
                             @HeaderParam("Accept") String accept,
                             @HeaderParam("Authorization") String authorization) {
        String requestedCellUri = uriinfo.getAbsolutePath().toString();
        
        // Extract agent name from Authorization header
        String agentName = extractAgentName(authorization);
        
        // If agent name is provided, enforce location-based access control for cells only
        // Non-cell resources (like /maze, /map, /counter, etc.) are always accessible
        if (agentName != null && !agentName.trim().isEmpty() && requestedCellUri.contains("/cells/")) {
            String currentLocation = agentLocations.get(agentName);
            
            // If agent has no location yet, check if they're requesting the entrance
            if (currentLocation == null) {
                // Validate that the requested cell is the entrance
                if (!isEntranceCell(requestedCellUri)) {
                    log.warn("Agent {} has no location, attempting to access {} - denied (not entrance)", 
                             agentName, requestedCellUri);
                    // Record denied request
                    requestTracker.recordRequest(agentName, requestedCellUri, "GET", false);
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Access denied. Agent must start at the entrance cell (check xhv:start in /maze).")
                            .build();
                }
                // Allow first access to entrance
                log.info("Agent {} starting at entrance: {}", agentName, requestedCellUri);
                agentLocations.put(agentName, requestedCellUri);
                // Record the first movement (entering the maze)
                pathTracker.recordMovement(agentName, requestedCellUri);
                // Record allowed request
                requestTracker.recordRequest(agentName, requestedCellUri, "GET", true);
            } else {
                // Validate that requested cell is accessible from current location
                if (!isAccessAllowed(currentLocation, requestedCellUri)) {
                    log.warn("Agent {} at {} attempted unauthorized access to {} - denied",
                             agentName, currentLocation, requestedCellUri);
                    // Record denied request
                    requestTracker.recordRequest(agentName, requestedCellUri, "GET", false);
                    // Don't record denied movements in path tracker
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Access denied. Cell " + requestedCellUri + 
                                   " is not accessible from your current location " + currentLocation)
                            .build();
                }
                // Update agent location
                log.info("Agent {} moved from {} to {}", agentName, currentLocation, requestedCellUri);
                agentLocations.put(agentName, requestedCellUri);
                // Record the movement (only if it's a different cell)
                pathTracker.recordMovement(agentName, requestedCellUri);
                // Record allowed request
                requestTracker.recordRequest(agentName, requestedCellUri, "GET", true);
            }
        }
        
        String ct = chooseContentType(accept);
        RDFFormat fmt = toRDFFormat(ct);
        log.info("LD GET request for graph ({}): {}", ct, uriinfo.getAbsolutePath());
        StreamingOutput out = streamGraph(uriinfo, fmt);
        return Response.ok(out, ct).build();
    }

    private String chooseContentType(String accept) {
        if (accept == null) return "text/turtle";
        String a = accept.trim().toLowerCase();
        if (a.isEmpty() || "*/*".equals(a)) return "text/turtle";
        if (a.contains("text/turtle")) return "text/turtle";
        if (a.contains("application/ld+json")) return "application/ld+json";
        if (a.contains("application/rdf+xml")) return "application/rdf+xml";
        if (a.contains("application/n-triples")) return "application/n-triples";
        return "text/turtle";
    }

    /**
     * Extract agent name from Authorization header.
     * Supports formats: "Agent agentname" or just "agentname"
     */
    private String extractAgentName(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        
        String auth = authorization.trim();
        
        // Support "Agent agentname" format
        if (auth.toLowerCase().startsWith("agent ")) {
            return auth.substring(6).trim();
        }
        
        // Support plain agent name
        return auth;
    }

    /**
     * Check if the requested cell is accessible from the current cell.
     * This queries the RDF graph to find valid outgoing connections (north, south, east, west, exit)
     * that are not walls and match the requested cell URI.
     */
    private boolean isAccessAllowed(String currentCellUri, String requestedCellUri) {
        // Same cell - always allowed (re-reading current position)
        if (currentCellUri.equals(requestedCellUri)) {
            return true;
        }
        
        SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
        try (SailRepositoryConnection connection = repo.getConnection()) {
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
     */
    private boolean isEntranceCell(String cellUri) {
        SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
        try (SailRepositoryConnection connection = repo.getConnection()) {
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

    private RDFFormat toRDFFormat(String ct) {
        switch (ct) {
            case "application/ld+json": return RDFFormat.JSONLD;
            case "application/rdf+xml": return RDFFormat.RDFXML;
            case "application/n-triples": return RDFFormat.NTRIPLES;
            default: return RDFFormat.TURTLE;
        }
    }

    private StreamingOutput streamGraph(UriInfo uriinfo, RDFFormat outputFormat) {
        SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
        SailRepositoryConnection connection = repo.getConnection();

        try {
            ValueFactory vf = connection.getValueFactory();
            IRI graphName = vf.createIRI(uriinfo.getAbsolutePath().toString());

            log.info("LD resolved graph IRI: {}", graphName);

            boolean exists = connection.hasStatement(null, null, null, false, graphName);
            if (!exists) {
                connection.close();
                log.info("LD graph not found, returning 404 for {}", graphName);
                throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Graph not found in RDF dataset: " + graphName)
                            .build());
            }

            StreamingOutput output = new StreamingOutput() {
                @Override
                public void write(OutputStream os) throws IOException, WebApplicationException {
                    try {
                        RDFWriter writer = Rio.createWriter(outputFormat, os);
                        connection.export(writer, graphName);
                        log.info("LD exported graph {} as {}", graphName, outputFormat.getDefaultMIMEType());
                    } finally {
                        connection.close();
                    }
                }
            };
            return output;

        } catch (RuntimeException e) {
            try { connection.close(); } catch (Exception ignore) {}
            log.error("LD error while dereferencing {}", uriinfo.getAbsolutePath(), e);
            throw e;
        }
    }

    // -------------------------------------------
    // Additional methods (e.g., POST, OPTIONS)
    // -------------------------------------------
    
    @OPTIONS
    public Response handleOptions(@Context UriInfo uriinfo) {
        return Response.noContent()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type")
                .build();
    }

    @POST
    @Consumes({ "text/turtle", "application/n-triples", "application/ld+json", "application/rdf+xml" })
    public Response postGraph(@Context UriInfo uriinfo, String body) {
        String graphIRI = uriinfo.getAbsolutePath().toString();
        log.info("LD POST merge into graph: {}", graphIRI);

        SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
        try (SailRepositoryConnection connection = repo.getConnection()) {

            ValueFactory vf = connection.getValueFactory();
            IRI graphName = vf.createIRI(graphIRI);

            // check existence (same semantics as GET)
            boolean exists = connection.hasStatement(null, null, null, false, graphName);
            if (!exists) {
                log.info("LD POST received but graph does not exist: {}", graphIRI);
                return Response.status(Response.Status.NOT_FOUND).entity("Graph not found: " + graphIRI).build();
            }

            // Parse body into this graph context
            java.util.Optional<RDFFormat> fmtOpt = Rio.getParserFormatForMIMEType(detectContentType(body));
            RDFFormat fmt = fmtOpt.orElse(RDFFormat.TURTLE); // fallback

            Model model = Rio.parse(new java.io.ByteArrayInputStream(body.getBytes()),
                      graphIRI,      // base URI → resolves relative URIs against the graph itself
                      fmt,
                      graphName);    // <-- merge into this same named graph

            connection.begin();
            connection.add(model);
            connection.commit();

            log.info("LD POST merged triples: \n {} \n into: {}", body, graphIRI);
            // verify body
            try {
                org.eclipse.rdf4j.query.GraphQuery query = connection.prepareGraphQuery(
                    "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <" + graphIRI + "> { ?s ?p ?o } }");

                java.io.StringWriter sw = new java.io.StringWriter();
                RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, sw);
                query.evaluate(writer);
                log.info("\n Graph content: \n{}", sw.toString());
            } catch (Exception e) {
                log.error("LD POST error verifying graph {}", graphIRI, e);
            }


            return Response.noContent()
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
                    .build();

        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            log.error("LD POST failed parsing body for {}", graphIRI, e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Bad RDF payload").build();
        } catch (Exception e) {
            log.error("LD POST error while writing graph {}", graphIRI, e);
            return Response.serverError().build();
        }
    }

    // Helper to guess MIME if no Content-Type was set by LDFu (which it often doesn't)
    private String detectContentType(String body) {
        // ultra lightweight heuristic: N3/Turtle starts with @prefix or <> or <http...
        String t = body.trim().toLowerCase();
        if (t.startsWith("@prefix") || t.startsWith("<")) return "text/turtle";
        return "text/turtle"; // fallback ok for ldfu
    }

}
