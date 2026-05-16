package org.maze.api.sparql;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import org.maze.api.ErrorResponseBuilder;
import org.maze.application.SparqlService;
import org.maze.domain.model.SparqlResult;
import org.maze.domain.utils.AgentAuthUtil;
import org.maze.infrastructure.web.WebServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SPARQL endpoint for executing queries against the RDF repository.
 * Supports SELECT, CONSTRUCT, ASK, DESCRIBE, and UPDATE operations.
 * 
 * <p>Endpoint: POST /sparql</p>
 * 
 * <p>Query can be provided via:</p>
 * <ul>
 *   <li>Request body with Content-Type: application/sparql-query</li>
 *   <li>Query parameter: ?query=...</li>
 * </ul>
 * 
 * <p>Content negotiation via Accept header:</p>
 * <ul>
 *   <li>SELECT/ASK: application/sparql-results+json (default), application/sparql-results+xml, text/csv, text/tab-separated-values</li>
 *   <li>CONSTRUCT/DESCRIBE: application/ld+json (default), text/turtle, application/rdf+xml, application/n-triples</li>
 *   <li>UPDATE: application/json</li>
 * </ul>
 * 
 * <p>Authentication: Optional. If "Authorization" header is present, agent name will be logged for audit.</p>
 */
@Path("/sparql")
public class SparqlResource {
    
    private static final Logger log = LoggerFactory.getLogger(SparqlResource.class);
    
    @Context
    private ServletContext servletContext;
    
    /**
     * Execute a SPARQL query (POST with body).
     * 
     * @param query the SPARQL query string from request body
     * @param acceptHeader the Accept header for content negotiation
     * @param authorizationHeader optional Authorization header for agent identification
     * @return Response with query results in requested format
     */
    @POST
    @Consumes("application/sparql-query")
    @Produces({ 
        "application/sparql-results+json", 
        "application/sparql-results+xml",
        "text/csv",
        "text/tab-separated-values",
        "application/ld+json",
        "text/turtle",
        "application/rdf+xml",
        "application/n-triples",
        "application/json"
    })
    public Response executeSparqlQueryBody(
            String query,
            @HeaderParam("Accept") String acceptHeader,
            @HeaderParam("Authorization") String authorizationHeader,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo) {
        
        return executeSparql(query, acceptHeader, authorizationHeader, headers, uriInfo);
    }
    
    /**
     * Execute a SPARQL query (POST with query parameter).
     * Allows form-encoded queries via ?query=...
     * 
     * @param query the SPARQL query string from query parameter
     * @param acceptHeader the Accept header for content negotiation
     * @param authorizationHeader optional Authorization header for agent identification
     * @return Response with query results in requested format
     */
    @POST
    @Produces({ 
        "application/sparql-results+json", 
        "application/sparql-results+xml",
        "text/csv",
        "text/tab-separated-values",
        "application/ld+json",
        "text/turtle",
        "application/rdf+xml",
        "application/n-triples",
        "application/json"
    })
    public Response executeSparqlQueryParam(
            @QueryParam("query") String query,
            @HeaderParam("Accept") String acceptHeader,
            @HeaderParam("Authorization") String authorizationHeader,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo) {
        
        if (query == null || query.trim().isEmpty()) {
            log.warn("SPARQL endpoint called with empty query parameter");
            return errorResponse(headers, uriInfo, Response.Status.BAD_REQUEST.getStatusCode(),
                    "Query parameter is required",
                    "{\"error\":\"Query parameter is required\"}");
        }
        
        return executeSparql(query, acceptHeader, authorizationHeader, headers, uriInfo);
    }
    
    /**
     * Core SPARQL execution logic.
     */
    private Response executeSparql(String query,
                                   String acceptHeader,
                                   String authorizationHeader,
                                   HttpHeaders headers,
                                   UriInfo uriInfo) {
        // Extract agent name for audit logging (optional)
        String agentName = null;
        if (authorizationHeader != null && !authorizationHeader.trim().isEmpty()) {
            agentName = AgentAuthUtil.extractAgentName(authorizationHeader);
        }
        
        if (agentName != null) {
            log.info("SPARQL query from agent: {}", agentName);
        } else {
            log.info("SPARQL query (anonymous)");
        }
        
        // Validate query
        if (query == null || query.trim().isEmpty()) {
            log.warn("SPARQL endpoint called with empty query");
            return errorResponse(headers, uriInfo, Response.Status.BAD_REQUEST.getStatusCode(),
                    "Query is required",
                    "{\"error\":\"Query is required\"}");
        }
        
        // Get SPARQL service from servlet context
        SparqlService sparqlService = (SparqlService) servletContext.getAttribute(
                WebServerFactory.SPARQL_SERVICE_SERVLET_ATTRIBUTE);
        
        if (sparqlService == null) {
            log.error("SparqlService not found in servlet context");
            return errorResponse(headers, uriInfo, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    "Game engine not initialized",
                    "{\"error\":\"Game engine not initialized\"}");
        }
        
        // Execute query
        SparqlResult result = sparqlService.executeQuery(query, acceptHeader);
        
        // Build response
        if (result.success()) {
            log.info("SPARQL query executed successfully, type: {}", result.queryType());
            return Response.ok(result.content(), result.contentType()).build();
        } else {
            log.error("SPARQL query failed: {}", result.errorMessage());
            
            // Determine if it's a client error (bad syntax) or server error
            String errorJson = String.format("{\"error\":\"%s\"}", 
                    result.errorMessage().replace("\"", "\\\""));
            
            // Check if error message indicates syntax/parsing error
            if (result.errorMessage().toLowerCase().contains("parse") ||
                result.errorMessage().toLowerCase().contains("syntax")) {
                return errorResponse(headers, uriInfo, Response.Status.BAD_REQUEST.getStatusCode(),
                        result.errorMessage(), errorJson);
            } else {
                return errorResponse(headers, uriInfo, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        result.errorMessage(), errorJson);
            }
        }
    }

    private Response errorResponse(HttpHeaders headers,
                                   UriInfo uriInfo,
                                   int statusCode,
                                   String message,
                                   String fallbackJson) {
        return ErrorResponseBuilder.build(
                headers.getAcceptableMediaTypes(),
                statusCode,
                uriInfo.getAbsolutePath().toString(),
                message,
                fallbackJson,
                MediaType.APPLICATION_JSON_TYPE);
    }
}
