package org.maze.api.admin;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import org.maze.api.dto.DemoAgentResetRequest;
import org.maze.api.dto.DemoAgentStepRequest;
import org.maze.application.demo.DemoAgentService;
import org.maze.infrastructure.web.WebServerFactory;

@Path("/admin/demo-agent")
@Produces(MediaType.APPLICATION_JSON)
public class DemoAgentResource {

    @Context
    private ServletContext servletContext;

    @GET
    @Path("/state")
    public Response state() {
        DemoAgentService service = demoAgentService();
        if (service == null) {
            return missingService();
        }
        return Response.ok(service.state()).build();
    }

    @POST
    @Path("/reset")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response reset(DemoAgentResetRequest request) {
        DemoAgentService service = demoAgentService();
        if (service == null) {
            return missingService();
        }

        String agentName = request != null ? request.agentName() : null;
        boolean preferGreenSignifiers = request == null || request.preferGreenSignifiers();
        return Response.ok(service.reset(agentName, preferGreenSignifiers)).build();
    }

    @POST
    @Path("/next")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response next(DemoAgentStepRequest request) {
        DemoAgentService service = demoAgentService();
        if (service == null) {
            return missingService();
        }
        if (request != null && request.preferGreenSignifiers() != null) {
            service.setPreferGreenSignifiers(request.preferGreenSignifiers());
        }
        return Response.ok(service.next()).build();
    }

    private DemoAgentService demoAgentService() {
        return (DemoAgentService) servletContext.getAttribute(
                WebServerFactory.DEMO_AGENT_SERVICE_SERVLET_ATTRIBUTE);
    }

    private Response missingService() {
        return Response.serverError()
                .entity(Map.of("error", "Demo agent service not initialized"))
                .build();
    }
}
