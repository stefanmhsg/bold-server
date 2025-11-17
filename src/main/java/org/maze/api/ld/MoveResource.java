package org.maze.api.ld;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.maze.application.MazeGameEngine;
import org.maze.domain.model.MoveResult;
import org.maze.domain.utils.AgentAuthUtil;
import org.maze.infrastructure.web.WebServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * REST endpoint for explicit agent movement operations.
 * Handles POST /move requests where agents specify their target cell.
 * 
 * <p>Movement semantics:</p>
 * <ul>
 *   <li>Agents must authenticate via Authorization header</li>
 *   <li>Target cell URI provided in request body (plain text)</li>
 *   <li>Validates movement is legal (adjacent cell with connection in RDF graph)</li>
 *   <li>Updates RDF: removes maze:contains from old cell, adds to new cell</li>
 *   <li>Returns 201 Created with Location header pointing to new cell</li>
 *   <li>Synchronized on repository for thread safety</li>
 * </ul>
 */
@Path("/move")
public class MoveResource {
    
    private static final Logger log = LoggerFactory.getLogger(MoveResource.class);
    
    @Context
    ServletContext _ctx;
    
    /**
     * Handle explicit movement request from an agent.
     * 
     * @param authorization the Authorization header containing agent name
     * @param uriinfo the request URI information
     * @param requestBody the target cell URI as plain text
     * @return 201 Created if movement succeeds, 403 Forbidden if not allowed, 400/500 on errors
     */
    @POST
    @Consumes("text/plain")
    @Produces("text/plain")
    public Response move(@HeaderParam("Authorization") String authorization,
                        @Context UriInfo uriinfo,
                        InputStream requestBody) {
        
        // Extract agent name from Authorization header
        String agentName = AgentAuthUtil.extractAgentName(authorization);
        if (agentName == null || agentName.isEmpty()) {
            log.warn("Move request rejected: no agent name in Authorization header");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Missing or invalid Authorization header. Provide agent name.")
                    .build();
        }
        
        // Read target cell URI from request body
        String targetCellUri;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody))) {
            targetCellUri = reader.readLine();
            if (targetCellUri == null || targetCellUri.trim().isEmpty()) {
                log.warn("Move request rejected: empty target cell URI");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Target cell URI required in request body")
                        .build();
            }
            targetCellUri = targetCellUri.trim();
        } catch (Exception e) {
            log.error("Error reading request body", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Failed to read target cell URI from request body")
                    .build();
        }
        
        log.info("Move request: agent={}, target={}", agentName, targetCellUri);
        
        // Delegate to game engine for all move logic
        MazeGameEngine gameEngine = getGameEngine();
        MoveResult moveResult = gameEngine.performMove(agentName, targetCellUri);
        
        if (!moveResult.isSuccess()) {
            log.info("Move denied for agent {}: {}", agentName, moveResult.errorMessage());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(moveResult.errorMessage())
                    .build();
        }
        
        // Move successful
        log.info("Move successful: agent {} from {} to {}", 
                agentName, moveResult.fromCell(), moveResult.toCell());
        
        // Return 201 Created with Location header
        return Response.created(URI.create(targetCellUri))
                .entity("Agent moved to " + targetCellUri)
                .build();
    }
    
    /**
     * Get the maze game engine singleton from ServletContext.
     */
    private MazeGameEngine getGameEngine() {
        return (MazeGameEngine) _ctx.getAttribute(WebServerFactory.MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE);
    }
}
