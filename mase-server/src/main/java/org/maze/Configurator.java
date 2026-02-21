package org.maze;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.websocket.MazeBroadcaster;
import org.maze.application.MazeRuleService;
import org.maze.application.tx.TransactionTraceContext;
import org.maze.infrastructure.config.ServerConfiguration;
import org.maze.infrastructure.rdf.DataLoader;
import org.maze.infrastructure.rdf.RepositoryFactory;
import org.maze.infrastructure.web.WebServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main entry point for the maze server.
 * Orchestrates server startup by delegating to specialized factory classes.
 */
public class Configurator {

    private static final Logger log = LoggerFactory.getLogger(Configurator.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    public static final String RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL = "gsp/";

    public static void main(String[] args) throws Exception {
        log.info("Starting MASE Maze Server...");
        
        // Parse command line arguments
        String task = args.length > 0 ? args[0] : "sim-SmallMaze";
        List<String> additionalRulesets = parseAdditionalRulesets(args);
        
        // Load configuration
        ServerConfiguration config = new ServerConfiguration(task);
        
        // Create RDF repository
        RepositoryFactory repoFactory = new RepositoryFactory();
        SailRepository repository = repoFactory.createRepository(config);
        
        // Load RDF data
        URI baseUri = resolveBaseUri();
        URI rdfBaseURI = baseUri.resolve(RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL);
        DataLoader dataLoader = new DataLoader();
        dataLoader.loadData(repository, config.getInitDataset(), rdfBaseURI);
        
        // Initialize Rules
        String mazeName = extractMazeName(task);
        List<String> rulesetPaths = buildRulesetPaths(additionalRulesets);
        List<String> ruleExecutionOrder = config.getRuleExecutionOrder();
        
        MazeRuleService ruleService = new MazeRuleService(repository, mazeName, rulesetPaths, ruleExecutionOrder);
        log.info("MazeRuleService initialized for maze: {}", mazeName != null ? mazeName : "generic");

        // Run rules once at startup to ensure initial consistency
        SailRepositoryConnection conn = null;
        TransactionTraceContext startupTrace = TransactionTraceContext.forStartup();
        try {
            conn = repository.getConnection();
            conn.begin();

            log.info("Running initial maze rules on startup...");
            ruleService.executeRules(conn, startupTrace);
            conn.commit();
            startupTrace.markCommitted();
            MazeBroadcaster.broadcast(mapper.writeValueAsString(startupTrace.getEvent()));

            log.info("Initial rule execution finished");
        } catch (Exception e) {
            if (conn != null) {
                try {
                    log.warn("Rolling back initial rule execution due to error");
                    conn.rollback();
                } catch (Exception rollbackError) {
                    log.error("Rollback during startup failed: ", rollbackError);
                }
            }
            startupTrace.markFailed(e.getMessage());
            try {
                MazeBroadcaster.broadcast(mapper.writeValueAsString(startupTrace.getEvent()));
            } catch (Exception broadcastError) {
                log.error("Failed to broadcast startup transaction event", broadcastError);
            }
            log.error("Initial rule execution failed: ", e);
            throw e;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }

        
        // Create and start web server
        WebServerFactory webFactory = new WebServerFactory();
        Server server = webFactory.createServer(config, repository, ruleService);
        
        log.info("Maze Server started successfully");
        server.join();
    }
    
    /**
     * Parse additional ruleset arguments from command line.
     */
    private static List<String> parseAdditionalRulesets(String[] args) {
        List<String> rulesets = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            rulesets.add(args[i]);
        }
        return rulesets;
    }
    
    /**
     * Resolve base URI from environment variable or default.
     */
    private static URI resolveBaseUri() throws Exception {
        String envUri = System.getenv("MASE_SERVER_BASE_URI");
        if (envUri != null) {
            return new URI(envUri);
        }
        return new URI("http://127.0.1.1:8080/");
    }
    
    /**
     * Build full ruleset paths, always including Global as base.
     */
    private static List<String> buildRulesetPaths(List<String> additionalRulesets) {
        List<String> paths = new ArrayList<>();
        paths.add("Global");
        
        for (String ruleset : additionalRulesets) {
            paths.add("Global/" + ruleset);
        }
        
        if (!additionalRulesets.isEmpty()) {
            log.info("Additional ruleset paths: {}", paths);
        }
        
        return paths;
    }
    
    /**
     * Extracts the maze name from a task string.
     * Examples: "sim-SmallMaze" -> "SmallMaze", "sim-BigMaze" -> "BigMaze"
     * 
     * @param task The task string (e.g., "sim-SmallMaze")
     * @return The maze name (e.g., "SmallMaze"), or null if no maze detected
     */
    private static String extractMazeName(String task) {
        if (task == null || task.isEmpty()) {
            return null;
        }
        
        // Remove "sim-" prefix if present
        if (task.startsWith("sim-")) {
            return task.substring(4); // Skip "sim-" (4 characters)
        }
        
        log.warn("Unrecognized task format: {}. Using as-is. Check if rules loaded properly.", task);
        
        // If no prefix, return the task as-is
        return task;
    }
}
