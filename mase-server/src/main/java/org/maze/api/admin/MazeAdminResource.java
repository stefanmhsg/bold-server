package org.maze.api.admin;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.maze.api.dto.MazeAdminSnapshotDto;
import org.maze.application.MazeLayoutService;
import org.maze.application.MazeResetService;
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
        snapshot.scenario = layoutService.getMazeScenarioName();

        log.info("Admin requested maze snapshot: scenario={}, {}x{}, ui elements: {}",
            snapshot.scenario,
            snapshot.layout.width,
            snapshot.layout.height,
            snapshot.ui.size()
        );

        return Response.ok(snapshot).build();
    }

    @POST
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetMaze() {
        MazeResetService resetService = (MazeResetService) servletContext.getAttribute(
            WebServerFactory.MAZE_RESET_SERVICE_SERVLET_ATTRIBUTE);

        if (resetService == null) {
            return Response.serverError()
                    .entity(Map.of("error", "Reset service not initialized"))
                    .build();
        }

        try {
            MazeAdminSnapshotDto snapshot = resetService.resetToInitialDataset();
            log.info("Admin reset maze snapshot: scenario={}, {}x{}, ui elements: {}",
                snapshot.scenario,
                snapshot.layout.width,
                snapshot.layout.height,
                snapshot.ui.size()
            );
            return Response.ok(snapshot).build();
        } catch (Exception e) {
            log.error("Admin reset failed", e);
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString()))
                    .build();
        }
    }
}
