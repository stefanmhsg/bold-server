package org.maze.infrastructure.web;

import java.net.URI;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.maze.api.ld.CorsFilter;
import org.maze.api.ld.LinkedDataDereferenceResource;
import org.maze.api.ld.MoveResource;
import org.maze.application.MazeGameEngine;
import org.maze.infrastructure.config.ServerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating and configuring the Jetty web server.
 */
public class WebServerFactory {
    
    private static final Logger log = LoggerFactory.getLogger(WebServerFactory.class);
    
    public static final String SAIL_REPOSITORY_SERVLET_ATTRIBUTE = "SAIL_REPOSITORY_SERVLET_ATTRIBUTE";
    public static final String MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE = "MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE";
    
    /**
     * Create and configure a web server.
     * 
     * @param config Server configuration
     * @param repository RDF repository
     * @param gameEngine Maze game engine
     * @return Configured Jetty server
     * @throws Exception if server creation fails
     */
    public Server createServer(ServerConfiguration config, SailRepository repository, 
                               MazeGameEngine gameEngine) throws Exception {
        int port = config.getPort();
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        server.start();
        
        URI serverBaseURI = resolveBaseUri(server);
        log.info("Server base URI: {}", serverBaseURI);
        
        configureRestEndpoints(context, repository, gameEngine);
        
        return server;
    }
    
    private URI resolveBaseUri(Server server) throws Exception {
        URI serverBaseURI = server.getURI();
        log.info("Jetty Server reported base URI: {}", serverBaseURI);
        
        String envUri = System.getenv("BOLD_SERVER_BASE_URI");
        if (envUri != null) {
            serverBaseURI = new URI(envUri);
            log.info("Using BOLD_SERVER_BASE_URI from environment: {}", serverBaseURI);
        }
        
        return serverBaseURI;
    }
    
    private void configureRestEndpoints(ServletContextHandler context, 
                                       SailRepository repository, 
                                       MazeGameEngine gameEngine) {
        // Configure JAX-RS resources
        ResourceConfig ldConfig = new ResourceConfig();
        ldConfig.register(LinkedDataDereferenceResource.class);
        ldConfig.register(MoveResource.class);
        ldConfig.register(CorsFilter.class);
        
        Servlet ldContainer = new ServletContainer(ldConfig);
        ServletHolder ldHolder = new ServletHolder("BOLD LD dereferencing servlet", ldContainer);
        
        // Mount on /* for linked data dereferencing
        context.addServlet(ldHolder, "/*");
        
        // Share repository and game engine via ServletContext
        HttpServlet servlet = (HttpServlet) ldContainer;
        servlet.getServletContext().setAttribute(SAIL_REPOSITORY_SERVLET_ATTRIBUTE, repository);
        servlet.getServletContext().setAttribute(MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE, gameEngine);
        
        log.info("REST endpoints configured");
    }
}
