package org.maze;

import java.net.URI;
import java.nio.file.Path;
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
import org.maze.infrastructure.scenario.ScenarioPackage;
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
        
        StartupSelection startup = parseStartupSelection(args);
        
        // Load configuration
        ServerConfiguration config = startup.scenarioRoot() != null
                ? ServerConfiguration.forScenarioPackage(startup.scenarioRoot())
                : new ServerConfiguration(startup.taskName());
        
        // Create RDF repository
        RepositoryFactory repoFactory = new RepositoryFactory();
        SailRepository repository = repoFactory.createRepository(config);
        
        // Load RDF data
        URI baseUri = resolveBaseUri();
        URI rdfBaseURI = baseUri.resolve(RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL);
        DataLoader dataLoader = new DataLoader();
        dataLoader.loadData(repository, config.getInitDataset(), rdfBaseURI);
        
        List<String> ruleExecutionOrder = config.getRuleExecutionOrder();
        MazeRuleService ruleService;
        if (config.isScenarioPackageMode()) {
            ScenarioPackage scenarioPackage = config.getScenarioPackage()
                    .orElseThrow(() -> new IllegalStateException("Scenario package mode has no package"));
            ruleService = new MazeRuleService(
                    repository,
                    scenarioPackage.ruleFiles(),
                    scenarioPackage.root(),
                    ruleExecutionOrder);
            log.info("MazeRuleService initialized for scenario package: {}", scenarioPackage.id());
        } else {
            String mazeName = extractMazeName(startup.taskName());
            List<String> rulesetPaths = buildRulesetPaths(startup.additionalRulesets());
            ruleService = new MazeRuleService(repository, mazeName, rulesetPaths, ruleExecutionOrder);
            log.info("MazeRuleService initialized for maze: {}", mazeName != null ? mazeName : "generic");
        }

        // Run rules once at startup to ensure initial consistency
        SailRepositoryConnection conn = null;
        TransactionTraceContext startupTrace = TransactionTraceContext.forStartup(config.getTransactionTraceMode());
        try {
            conn = repository.getConnection();
            conn.begin();

            log.info("Running initial maze rules on startup...");
            ruleService.executeRules(conn, startupTrace);
            conn.commit();
            if (startupTrace != null) {
                startupTrace.markCommitted();
                MazeBroadcaster.broadcast(mapper.writeValueAsString(startupTrace.getEvent()));
            }

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
            if (startupTrace != null) {
                startupTrace.markFailed(e.getMessage());
                try {
                    MazeBroadcaster.broadcast(mapper.writeValueAsString(startupTrace.getEvent()));
                } catch (Exception broadcastError) {
                    log.error("Failed to broadcast startup transaction event", broadcastError);
                }
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
        Server server = webFactory.createServer(config, repository, ruleService, rdfBaseURI);
        
        log.info("Maze Server started successfully");
        server.join();
    }
    
    private static StartupSelection parseStartupSelection(String[] args) {
        if (args.length > 0 && "--scenario".equals(args[0])) {
            if (args.length < 2 || args[1].isBlank()) {
                throw new IllegalArgumentException("Missing scenario directory after --scenario");
            }
            if (args.length > 2) {
                throw new IllegalArgumentException("Additional ruleset arguments are not supported in package mode");
            }
            return StartupSelection.scenario(Path.of(args[1]));
        }

        if (args.length == 0 || args[0].isBlank()) {
            String scenarioDir = System.getenv("MASE_SCENARIO_DIR");
            if (scenarioDir != null && !scenarioDir.isBlank()) {
                return StartupSelection.scenario(Path.of(scenarioDir.trim()));
            }

            String taskName = System.getenv("TASKNAME");
            if (taskName != null && !taskName.isBlank()) {
                return StartupSelection.legacy(taskName.trim(), List.of());
            }

            return StartupSelection.legacy("sim-SmallMaze", List.of());
        }

        return StartupSelection.legacy(args[0], parseAdditionalRulesets(args));
    }

    private static List<String> parseAdditionalRulesets(String[] args) {
        List<String> rulesets = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            rulesets.add(args[i]);
        }
        return rulesets;
    }

    private record StartupSelection(String taskName, Path scenarioRoot, List<String> additionalRulesets) {

        private static StartupSelection legacy(String taskName, List<String> additionalRulesets) {
            return new StartupSelection(taskName, null, List.copyOf(additionalRulesets));
        }

        private static StartupSelection scenario(Path scenarioRoot) {
            return new StartupSelection(null, scenarioRoot, List.of());
        }
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
