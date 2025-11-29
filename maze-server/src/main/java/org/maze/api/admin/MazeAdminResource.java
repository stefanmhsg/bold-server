package org.maze.api.admin;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.maze.api.dto.MazeLayoutDto;
import org.maze.application.MazeLayoutService;
import org.maze.infrastructure.web.WebServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/admin/maze")
public class MazeAdminResource {

    private static final Logger log = LoggerFactory.getLogger(MazeAdminResource.class);


    @Context
    private ServletContext servletContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMazeLayout() {
        SailRepository repository = (SailRepository) servletContext.getAttribute(
            WebServerFactory.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
            
        if (repository == null) {
            return Response.serverError().entity("Repository not initialized").build();
        }

        MazeLayoutService layoutService = new MazeLayoutService(repository);
        MazeLayoutDto layout = layoutService.getMazeLayout();

        log.info("Admin requested maze layout: {}x{}, start: {}, exit: {}", 
                 layout.width, layout.height, layout.startCell, layout.exitCell);

        return Response.ok(layout).build();
    }
}
