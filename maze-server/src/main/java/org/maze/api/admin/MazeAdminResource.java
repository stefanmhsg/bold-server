package org.maze.api.admin;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.maze.api.dto.MazeAdminSnapshotDto;
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

        // Instantiate layout service
        MazeLayoutService layoutService = new MazeLayoutService(repository);

        MazeAdminSnapshotDto snapshot = new MazeAdminSnapshotDto();
        snapshot.layout = layoutService.getMazeLayout();
        snapshot.ui = layoutService.getUiSnapshot();

        log.info("Admin requested maze snapshot: {}x{}, ui elements: {}",
            snapshot.layout.width,
            snapshot.layout.height,
            snapshot.ui.size()
        );

        return Response.ok(snapshot).build();
    }
}
