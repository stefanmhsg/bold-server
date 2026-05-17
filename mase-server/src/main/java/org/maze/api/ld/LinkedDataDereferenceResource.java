package org.maze.api.ld;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.HttpHeaders;

import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.maze.application.AccessValidator;
import org.maze.application.PostHandler;
import org.maze.domain.model.AccessResult;
import org.maze.domain.model.PostResult;
import org.maze.domain.utils.AgentAuthUtil;
import org.maze.infrastructure.web.ResourceIriResolver;
import org.maze.infrastructure.web.WebServerFactory;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.api.ErrorResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dereference any request URI as an RDF named graph if present in the store.
 * Mounted at "/*" in Jetty.
 * 
 * <p>Location-based access control for maze navigation:</p>
 * <ul>
 *   <li><strong>Authentication:</strong> Agents must provide their name via the "Authorization" header 
 *       (e.g., "Agent agentname" or just "agentname")</li>
 *   <li><strong>Entry:</strong> Agents start at the entrance (determined by xhv:start in /maze graph) 
 *       by POSTing an entersFrom triple from /maze to the entrance cell</li>
 *   <li><strong>Movement:</strong> To move between cells, agent POSTs to target cell with 
 *       {@code <agentUri> dynmaze:entersFrom <sourceCell>} triple. Validated to ensure:
 *       <ul>
 *         <li>Agent is at the source cell (entersFrom value)</li>
 *         <li>Target cell is adjacent to source cell (maze:north/south/east/west/exit connection exists)</li>
 *         <li>Only one entersFrom statement per POST (enforced)</li>
 *       </ul>
 *   </li>
 *   <li><strong>Perception:</strong> GET requests are only allowed for the agent's current cell location</li>
 *   <li><strong>Interaction:</strong> POST without entersFrom (e.g., posting keys) is only allowed 
 *       to the agent's current cell</li>
 *   <li><strong>Locked doors:</strong> Connections not in the graph (not yet unlocked) deny movement access</li>
 * </ul>
 *
 * <p>POST handling deliberately keeps only core MASE/Web embodiment semantics in Java.
 * This resource parses RDF and delegates the transaction to {@link PostHandler}; inside that
 * transaction, requests are classified as either adjacent movement requests or local cell
 * interactions. Scenario-specific effects such as unlocking, switches, CCRS behavior, and UI
 * updates remain encoded in SPARQL rules.</p>
 */
@Path("/{id: .*}")
public class LinkedDataDereferenceResource {

    private static final Logger log = LoggerFactory.getLogger(LinkedDataDereferenceResource.class);

    @Context
    private ServletContext servletContext;

    @GET
    @Produces({ "text/turtle", "application/ld+json", "application/rdf+xml", "application/n-triples", "text/plain" })
    public Response getGraph(@Context UriInfo uriinfo,
                             @Context HttpHeaders headers,
                             @HeaderParam("Authorization") String authorization) {
        String requestedCellUri = canonicalResourceIri(uriinfo.getAbsolutePath().toString());
        
        // Extract agent name and validate access
        String agentName = AgentAuthUtil.extractAgentName(authorization, requestedCellUri);
        
        if (agentName != null && agentName.contains(" ")) {
            return errorResponse(headers, Response.Status.BAD_REQUEST.getStatusCode(),
                    requestedCellUri, "Agent name cannot contain whitespaces.");
        }

        AccessValidator accessValidator = getAccessValidator();
        AccessResult accessResult = accessValidator.validateAccess(agentName, requestedCellUri, "GET");
        
        if (!accessResult.isAllowed()) {
            return errorResponse(headers, Response.Status.FORBIDDEN.getStatusCode(),
                    requestedCellUri, accessResult.message());
        }
        
        // Use JAX-RS content negotiation to determine best format
        MediaType acceptedType = headers.getAcceptableMediaTypes().stream()
            .filter(mt -> mt.isCompatible(MediaType.valueOf("text/turtle")) ||
                         mt.isCompatible(MediaType.valueOf("application/ld+json")) ||
                         mt.isCompatible(MediaType.valueOf("application/rdf+xml")) ||
                         mt.isCompatible(MediaType.valueOf("application/n-triples")))
            .findFirst()
            .orElse(MediaType.valueOf("text/turtle"));
        
        RDFFormat fmt = mediaTypeToRDFFormat(acceptedType);
        log.info("LD GET request for graph ({}): {} -> {}", acceptedType, uriinfo.getAbsolutePath(), requestedCellUri);
        StreamingOutput out = streamGraph(requestedCellUri, headers, fmt);
        return Response.ok(out, acceptedType).build();
    }

    @OPTIONS
    public Response handleOptions(@Context UriInfo uriinfo) {
        return Response.noContent()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
                .header("Access-Control-Allow-Headers", "Accept, Authorization, Content-Type")
                .build();
    }

    @POST
    @Consumes({ "text/turtle", "application/n-triples", "application/ld+json", "application/rdf+xml", "text/plain", "*/*" })
    @Produces({ "text/plain", "text/turtle", "application/ld+json", "application/rdf+xml", "application/n-triples" })
    public Response postGraph(@HeaderParam("Authorization") String authorization,
                              @Context HttpHeaders headers,
                              @Context UriInfo uriinfo, 
                              String body) {
        String graphIRI = canonicalResourceIri(uriinfo.getAbsolutePath().toString());
        String agentName = AgentAuthUtil.extractAgentName(authorization, graphIRI);
        
        if (agentName != null && agentName.contains(" ")) {
            return errorResponse(headers, Response.Status.BAD_REQUEST.getStatusCode(),
                    graphIRI, "Agent name cannot contain whitespaces.");
        }
        
        log.info("LD POST to graph: {} by agent: {}", graphIRI, 
                 agentName != null ? agentName : "<anonymous>");

        // Parse RDF body before handing the request to transactional POST handling.
        MediaType contentType = headers.getMediaType();
        Model model = parseRdfBody(body, graphIRI, contentType);
        if (model == null) {
            return errorResponse(headers, Response.Status.BAD_REQUEST.getStatusCode(),
                    graphIRI, "Bad RDF payload");
        }

        // Execute POST operation
        PostHandler postHandler = getPostHandler();
        PostResult postResult = postHandler.performPost(agentName, graphIRI, model, body);
        
        if (!postResult.isSuccess()) {
            log.warn("POST to {} failed for agent {}: {}", graphIRI, agentName, postResult.errorMessage());
            return errorResponse(headers, postResult.statusCode(), graphIRI, postResult.errorMessage());
        }
        
        log.info("POST successful: {} triples merged into {}", postResult.triplesAdded(), graphIRI);
        return Response.created(URI.create(graphIRI))
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity("Graph updated: " + graphIRI)
                .build();
    }

    // ==================== Helper Methods ====================
    
    /**
     * Retrieve AccessValidator from ServletContext.
     */
    private AccessValidator getAccessValidator() {
        return (AccessValidator) servletContext.getAttribute(
            WebServerFactory.ACCESS_VALIDATOR_SERVLET_ATTRIBUTE);
    }
    
    /**
     * Retrieve PostHandler from ServletContext.
     */
    private PostHandler getPostHandler() {
        return (PostHandler) servletContext.getAttribute(
            WebServerFactory.POST_HANDLER_SERVLET_ATTRIBUTE);
    }
    
    /**
     * Retrieve SailRepository from ServletContext.
     */
    private SailRepository getRepository() {
        return (SailRepository) servletContext.getAttribute(
            WebServerFactory.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
    }

    private ResourceIriResolver getResourceIriResolver() {
        return (ResourceIriResolver) servletContext.getAttribute(
            WebServerFactory.RESOURCE_IRI_RESOLVER_SERVLET_ATTRIBUTE);
    }

    private String canonicalResourceIri(String requestUri) {
        ResourceIriResolver resolver = getResourceIriResolver();
        return resolver != null ? resolver.canonicalize(requestUri) : requestUri;
    }

    /**
     * Convert JAX-RS MediaType to RDF4J RDFFormat using Rio's built-in mapping.
     */
    private RDFFormat mediaTypeToRDFFormat(MediaType mediaType) {
        String mimeType = mediaType.getType() + "/" + mediaType.getSubtype();
        return Rio.getParserFormatForMIMEType(mimeType)
                  .orElse(RDFFormat.TURTLE);
    }

    /**
     * Create a StreamingOutput that exports an RDF graph.
     */
    private StreamingOutput streamGraph(String graphIri, HttpHeaders headers, RDFFormat outputFormat) {
        SailRepository repo = getRepository();
        SailRepositoryConnection connection = repo.getConnection();

        try {
            ValueFactory vf = connection.getValueFactory();
            IRI graphName = vf.createIRI(graphIri);

            log.info("LD resolved graph IRI: {}", graphName);

            boolean exists = connection.hasStatement(null, null, null, false, graphName);
            if (!exists) {
                connection.close();
                log.info("LD graph not found, returning 404 for {}", graphName);
                throw new WebApplicationException(
                    errorResponse(headers, Response.Status.NOT_FOUND.getStatusCode(),
                            graphName.stringValue(), "Graph not found in RDF dataset: " + graphName));
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

        } catch (WebApplicationException e) {
            // Don't log 404s as errors - they're expected for non-existent graphs
            try { connection.close(); } catch (Exception ignore) {}
            throw e;
        } catch (RuntimeException e) {
            try { connection.close(); } catch (Exception ignore) {}
            log.error("LD error while dereferencing {}", graphIri, e);
            throw e;
        }
    }

    private Response errorResponse(HttpHeaders headers, int statusCode, String targetResourceUri, String message) {
        return ErrorResponseBuilder.build(headers.getAcceptableMediaTypes(), statusCode, targetResourceUri, message);
    }

    /**
     * Parse RDF body into a Model.
     * Supports all RDF formats including literals.
     * Returns null if parsing fails.
     * 
     * @param body the RDF body content
     * @param graphIRI the target graph IRI (used as base URI)
     * @param contentType the Content-Type from request (may be null)
     */
    private Model parseRdfBody(String body, String graphIRI, MediaType contentType) {
        try {
            ValueFactory vf = getRepository().getValueFactory();
            IRI graphName = vf.createIRI(graphIRI);
            
            // Determine format from Content-Type or detect from body
            RDFFormat format;
            if (contentType != null && 
                !contentType.isWildcardType() && 
                !MediaType.TEXT_PLAIN_TYPE.isCompatible(contentType)) {
                // Use Content-Type if it's a specific RDF format
                String mimeType = contentType.getType() + "/" + contentType.getSubtype();
                format = Rio.getParserFormatForMIMEType(mimeType)
                           .orElse(RDFFormat.TURTLE);
            } else {
                // Fallback: detect from content or default to Turtle
                format = detectFormatFromContent(body);
            }
            
            log.debug("Parsing RDF with format: {} for graph: {}", format.getName(), graphIRI);
            
            // Parse with explicit UTF-8 encoding and graph context
            return Rio.parse(
                new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                graphIRI,      // base URI
                format,        // RDF format
                graphName      // target graph (context for quads)
            );
        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            log.error("Failed to parse RDF body for {} (contentType: {}, error: {})", 
                     graphIRI, contentType, e.getMessage());
            return null;
        }
    }
    
    /**
     * Detect RDF format from content using simple heuristics.
     * Returns TURTLE as default fallback.
     */
    private RDFFormat detectFormatFromContent(String body) {
        String trimmed = body.trim().toLowerCase();
        
        // Turtle/N3 typically starts with @prefix or angle brackets
        if (trimmed.startsWith("@prefix") || trimmed.startsWith("@base") || trimmed.startsWith("<")) {
            return RDFFormat.TURTLE;
        }
        
        // JSON-LD starts with { or [
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return RDFFormat.JSONLD;
        }
        
        // RDF/XML starts with <?xml or <rdf
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<rdf")) {
            return RDFFormat.RDFXML;
        }
        
        // Default to Turtle (most common for plain text)
        return RDFFormat.TURTLE;
    }

}
