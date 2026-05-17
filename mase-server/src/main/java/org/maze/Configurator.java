package org.maze;

import java.net.URI;
import java.nio.file.Path;

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
    private static final Path DEFAULT_SCENARIO_ROOT = Path.of("scenarios/smallmaze");
    
    public static final String RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL = "gsp/";

    public static void main(String[] args) throws Exception {
        log.info("Starting MASE Maze Server...");
        
        Path scenarioRoot = parseScenarioRoot(args);
        
        // Load configuration
        ServerConfiguration config = ServerConfiguration.forScenarioPackage(scenarioRoot);
        
        // Create RDF repository
        RepositoryFactory repoFactory = new RepositoryFactory();
        SailRepository repository = repoFactory.createRepository(config);
        
        // Load RDF data
        URI baseUri = resolveBaseUri();
        URI rdfBaseURI = baseUri.resolve(RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL);
        DataLoader dataLoader = new DataLoader();
        dataLoader.loadData(repository, config.getInitDataset(), rdfBaseURI);
        
        ScenarioPackage scenarioPackage = config.getScenarioPackage()
                .orElseThrow(() -> new IllegalStateException("Scenario package configuration did not resolve a package"));
        MazeRuleService ruleService = new MazeRuleService(
                repository,
                scenarioPackage.ruleFiles(),
                scenarioPackage.root(),
                config.getRuleExecutionOrder());
        log.info("MazeRuleService initialized for scenario package: {}", scenarioPackage.id());

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
    
    private static Path parseScenarioRoot(String[] args) {
        if (args.length > 0 && "--scenario".equals(args[0])) {
            if (args.length < 2 || args[1].isBlank()) {
                throw new IllegalArgumentException("Missing scenario directory after --scenario");
            }
            if (args.length > 2) {
                throw new IllegalArgumentException("Unexpected arguments after scenario directory");
            }
            return Path.of(args[1]);
        }

        if (args.length == 0 || args[0].isBlank()) {
            String scenarioDir = System.getenv("MASE_SCENARIO_DIR");
            if (scenarioDir != null && !scenarioDir.isBlank()) {
                return Path.of(scenarioDir.trim());
            }

            return DEFAULT_SCENARIO_ROOT;
        }

        if (args.length == 1) {
            throw new IllegalArgumentException("Legacy task names are no longer supported. Use --scenario <scenario-directory>.");
        }

        throw new IllegalArgumentException("Use --scenario <scenario-directory> or set MASE_SCENARIO_DIR.");
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
    
}
