package org.maze.infrastructure.web;

import java.net.URI;

import jakarta.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.maze.api.ld.CorsFilter;
import org.maze.api.ld.LinkedDataDereferenceResource;
import org.maze.api.sparql.SparqlResource;
import org.maze.api.admin.MazeAdminResource;
import org.maze.application.AccessValidator;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.maze.application.MazeRuleService;
import org.maze.application.PostHandler;
import org.maze.application.SparqlService;
import org.maze.infrastructure.config.ServerConfiguration;
import org.eclipse.jetty.ee11.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.maze.api.websocket.MazeBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating and configuring the Jetty web server.
 */
public class WebServerFactory {
    
    private static final Logger log = LoggerFactory.getLogger(WebServerFactory.class);
    
    public static final String SAIL_REPOSITORY_SERVLET_ATTRIBUTE = "SAIL_REPOSITORY_SERVLET_ATTRIBUTE";
    public static final String MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE = "MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE";
    public static final String ACCESS_VALIDATOR_SERVLET_ATTRIBUTE = "ACCESS_VALIDATOR_SERVLET_ATTRIBUTE";
    public static final String POST_HANDLER_SERVLET_ATTRIBUTE = "POST_HANDLER_SERVLET_ATTRIBUTE";
    public static final String SPARQL_SERVICE_SERVLET_ATTRIBUTE = "SPARQL_SERVICE_SERVLET_ATTRIBUTE";
    
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
                               MazeRuleService gameEngine) throws Exception {
        int port = config.getPort();
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler("/");
        server.setHandler(context);
        
        configureRestEndpoints(context, config, repository, gameEngine);
        
        // Configure WebSocket
        JakartaWebSocketServletContainerInitializer.configure(context, (servletContext, container) -> {
            container.addEndpoint(MazeBroadcaster.class);
        });

        server.start();
        
        URI serverBaseURI = resolveBaseUri(server);
        log.info("Server base URI: {}", serverBaseURI);
        
        return server;
    }
    
    private URI resolveBaseUri(Server server) throws Exception {
        URI serverBaseURI = server.getURI();
        log.info("Jetty Server reported base URI: {}", serverBaseURI);
        
        String envUri = System.getenv("MASE_SERVER_BASE_URI");
        if (envUri != null) {
            serverBaseURI = new URI(envUri);
            log.info("Using MASE_SERVER_BASE_URI from environment: {}", serverBaseURI);
        }
        
        return serverBaseURI;
    }
    
    private void configureRestEndpoints(ServletContextHandler context,
                                       ServerConfiguration config,
                                       SailRepository repository, 
                                       MazeRuleService gameEngine) {
        // Initialize services - SparqlService must be created first
        SparqlService sparqlService = new SparqlService(repository);
        AccessValidator accessValidator = new AccessValidator(repository, sparqlService);
        PostHandler postHandler = new PostHandler(
            repository,
            gameEngine,
            accessValidator,
            config.isTransactionTraceEnabled());
        
        // Share repository, game engine, and services via ServletContext
        context.setAttribute(SAIL_REPOSITORY_SERVLET_ATTRIBUTE, repository);
        context.setAttribute(MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE, gameEngine);
        context.setAttribute(ACCESS_VALIDATOR_SERVLET_ATTRIBUTE, accessValidator);
        context.setAttribute(POST_HANDLER_SERVLET_ATTRIBUTE, postHandler);
        context.setAttribute(SPARQL_SERVICE_SERVLET_ATTRIBUTE, sparqlService);
        
        // Configure JAX-RS resources
        ResourceConfig ldConfig = new ResourceConfig();
        ldConfig.register(LinkedDataDereferenceResource.class);
        ldConfig.register(SparqlResource.class);
        ldConfig.register(MazeAdminResource.class);
        ldConfig.register(CorsFilter.class);
        ldConfig.register(JacksonFeature.class);
        ldConfig.register(JacksonFeature.class);
        
        Servlet ldContainer = new ServletContainer(ldConfig);
        ServletHolder ldHolder = new ServletHolder("MASE LD dereferencing servlet", ldContainer);
        
        // Mount on /* for linked data dereferencing
        context.addServlet(ldHolder, "/*");
        
        log.info("REST endpoints configured");
    }
}
