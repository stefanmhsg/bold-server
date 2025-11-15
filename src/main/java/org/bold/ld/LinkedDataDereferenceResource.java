package org.bold.ld;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

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
import org.bold.maze.AgentAuthUtil;
import org.bold.maze.MazeGameEngine;
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
 * Mounted at "/*" in Jetty.
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

    @GET
    @Produces({ "text/turtle", "application/ld+json", "application/rdf+xml", "application/n-triples" })
    public Response getGraph(@Context UriInfo uriinfo,
                             @HeaderParam("Accept") String accept,
                             @HeaderParam("Authorization") String authorization) {
        String requestedCellUri = uriinfo.getAbsolutePath().toString();
        
        // Extract agent name from Authorization header
        String agentName = AgentAuthUtil.extractAgentName(authorization);
        
        // Validate access through the maze game engine (singleton from ServletContext)
        MazeGameEngine.AccessResult accessResult = getGameEngine().validateAccess(
            agentName, requestedCellUri, "GET");
        
        if (!accessResult.isAllowed()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(accessResult.getMessage())
                    .build();
        }
        
        String ct = chooseContentType(accept);
        RDFFormat fmt = toRDFFormat(ct);
        log.info("LD GET request for graph ({}): {}", ct, uriinfo.getAbsolutePath());
        StreamingOutput out = streamGraph(uriinfo, fmt);
        return Response.ok(out, ct).build();
    }
    
    /**
     * Get the maze game engine singleton from ServletContext.
     * This ensures the same instance is used across all requests, preserving agent state.
     */
    private MazeGameEngine getGameEngine() {
        return (MazeGameEngine) _ctx.getAttribute(Configurator.MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE);
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
    @Produces("text/plain")
    public Response postGraph(@HeaderParam("Authorization") String authorization,
                                @Context UriInfo uriinfo, String body) {
                                    
        // Extract agent name from Authorization header (optional)
        String agentName = AgentAuthUtil.extractAgentName(authorization);
        
        String graphIRI = uriinfo.getAbsolutePath().toString();
        log.info("LD POST attempting merge into graph: {} by agent: {}", graphIRI, 
                 agentName != null ? agentName : "<anonymous>");
        
        // Parse RDF body
        Model model;
        try {
            java.util.Optional<RDFFormat> fmtOpt = Rio.getParserFormatForMIMEType(detectContentType(body));
            RDFFormat fmt = fmtOpt.orElse(RDFFormat.TURTLE);
            
            ValueFactory vf = ((SailRepository) _ctx.getAttribute(
                Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE)).getValueFactory();
            IRI graphName = vf.createIRI(graphIRI);
            
            model = Rio.parse(new java.io.ByteArrayInputStream(body.getBytes()),
                            graphIRI,
                            fmt,
                            graphName);
                            
        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            log.error("LD POST failed parsing body for {}", graphIRI, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Bad RDF payload")
                    .build();
        }
        
        // Delegate to game engine for all POST logic
        MazeGameEngine.PostResult postResult = getGameEngine().performPost(agentName, graphIRI, model);
        
        if (!postResult.isSuccess()) {
            log.warn("POST to {} failed for agent {}: {}", graphIRI, agentName, postResult.getErrorMessage());
            return Response.status(postResult.getStatusCode())
                    .entity(postResult.getErrorMessage())
                    .build();
        }
        
        // Success - return 201 Created
        log.info("POST successful: {} triples merged into {}, {} rules triggered",
                postResult.getTriplesAdded(), graphIRI, postResult.getRulesTriggered());
        
        return Response.created(URI.create(graphIRI))
                .entity("Graph updated: " + graphIRI)
                .build(); 
    }

    // Helper to guess MIME if no Content-Type was set by LDFu (which it often doesn't)
    private String detectContentType(String body) {
        // ultra lightweight heuristic: N3/Turtle starts with @prefix or <> or <http...
        String t = body.trim().toLowerCase();
        if (t.startsWith("@prefix") || t.startsWith("<")) return "text/turtle";
        return "text/turtle"; // fallback ok for ldfu
    }

}
