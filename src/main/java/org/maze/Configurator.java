package org.maze;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.maze.application.MazeRuleService;
import org.maze.infrastructure.config.ServerConfiguration;
import org.maze.infrastructure.rdf.DataLoader;
import org.maze.infrastructure.rdf.RepositoryFactory;
import org.maze.infrastructure.web.WebServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the maze server.
 * Orchestrates server startup by delegating to specialized factory classes.
 */
public class Configurator {

    private static final Logger log = LoggerFactory.getLogger(Configurator.class);
    
    public static final String RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL = "gsp/";

    public static void main(String[] args) throws Exception {
        log.info("Starting BOLD Maze Server...");
        
        // Parse command line arguments
        String task = args.length > 0 ? args[0] : "sim-UnsafeMaze";
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
        
        MazeRuleService gameEngine = new MazeRuleService(repository, mazeName, rulesetPaths);
        log.info("MazeGameEngine initialized for maze: {}", mazeName != null ? mazeName : "generic");
        
        // Create and start web server
        WebServerFactory webFactory = new WebServerFactory();
        Server server = webFactory.createServer(config, repository, gameEngine);
        
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
        String envUri = System.getenv("BOLD_SERVER_BASE_URI");
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
     * Examples: "sim-UnsafeMaze" -> "UnsafeMaze", "sim-BigMaze" -> "BigMaze"
     * 
     * @param task The task string (e.g., "sim-UnsafeMaze")
     * @return The maze name (e.g., "UnsafeMaze"), or null if no maze detected
     */
    private static String extractMazeName(String task) {
        if (task == null || task.isEmpty()) {
            return null;
        }
        
        // Check for known maze patterns
        if (task.contains("UnsafeMaze")) {
            return "UnsafeMaze";
        } else if (task.contains("BigMaze")) {
            return "BigMaze";
        } else if (task.contains("MidMaze")) {
            return "MidMaze";
        }
        
        // If no known maze found, return null (will load generic rules)
        return null;
    }
}
