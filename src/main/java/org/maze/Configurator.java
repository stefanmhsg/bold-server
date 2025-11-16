package org.maze;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import de.fau.rw.ti.LDPInferencer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.maze.api.ld.CorsFilter;
import org.maze.api.ld.LinkedDataDereferenceResource;
import org.maze.api.ld.MoveResource;
import org.maze.application.MazeGameEngine;
import org.maze.domain.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

public class Configurator {

    private static final Logger log = LoggerFactory.getLogger(Configurator.class);

    private final static String SERVER_HTTP_PORT_KEY = "bold.server.httpPort";

    private final static String SERVER_HTTP_PORT_DEFAULT = "8080";

    private final static String INIT_DATASET_KEY = "bold.init.dataset";

	public static final String SAIL_REPOSITORY_SERVLET_ATTRIBUTE = "SAIL_REPOSITORY_SERVLET_ATTRIBUTE";
	
	public static final String MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE = "MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE";

	public static final String RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL = "gsp/";

    private static final String SERVER_PROTOCOL_KEY = "bold.server.protocol";

    public static void main(String[] args) throws Exception {
        // TODO more advanced CLI
        String task = args.length > 0 ? args[0] : "sim-UnsafeMaze";
        
        // Parse all additional arguments as ruleset directories
        List<String> additionalRulesets = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            additionalRulesets.add(args[i]);
        }

        Properties config = new Properties();
        config.load(new FileInputStream((task + ".properties")));

        int port = Integer.parseInt(config.getProperty(SERVER_HTTP_PORT_KEY, SERVER_HTTP_PORT_DEFAULT));

		// --------------------------------------------------
		// static serving of dataset
		// --------------------------------------------------

		Server server = new Server(port);

		ServletContextHandler context = new ServletContextHandler(server, "/");
		server.start();

		URI serverBaseURI = server.getURI();
		log.info("Jetty Server reported base URI: " + serverBaseURI);
		serverBaseURI = System.getenv("BOLD_SERVER_BASE_URI") == null ? server.getURI()
				: new URI(System.getenv("BOLD_SERVER_BASE_URI"));
		log.info("Server base URI after considering environment variable: " + serverBaseURI.toString());
		URI rdfBaseURI = serverBaseURI.resolve(RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL);
		log.info("Base URI for RDF Graphs after considering environment variable: " + rdfBaseURI.toString());

		// Choose Sail implementation (defaults to MemoryStore)
		Sail sail = createSail(config);
		SailRepository repo = new SailRepository(sail);

		log.info("Opening repository connection...");
		try (RepositoryConnection conn = repo.getConnection()) {
			log.info("Repository connection opened: {}", conn);
			for (String filename : FileUtils.listFiles(config.getProperty(INIT_DATASET_KEY))) {
				log.info("Discovered RDF file {}", filename);
				RDFFormat format = Rio.getParserFormatForFileName(filename).orElseThrow(() -> new IOException());
				log.info("Parsing file '{}' with format '{}'", filename, format.getName());
				Model ds = Rio.parse(FileUtils.getFileOrResource(filename), rdfBaseURI.toString(), format);
				log.info("Parsed model has {} statements", ds.size());
				
				conn.begin();
				conn.add(ds);
				conn.commit();

				// Verification log output
				try (SailRepositoryConnection conn2 = repo.getConnection()) {
				
				    log.info("=== Named Graph Inspection ===");
				
				    int total = 0;
				    int mazeLike = 0;
				    int hostPrefixMismatch = 0;
				
				    String expectedHost = "http://127.0.1.1:8080";
				
				    List<Resource> allContexts = new ArrayList<>();
				    RepositoryResult<? extends Resource> ctxIt = conn2.getContextIDs();
				    try {
				        while (ctxIt.hasNext()) {
				            allContexts.add(ctxIt.next());
				        }
				    } finally {
				        ctxIt.close();
				    }
				    total = allContexts.size();
				
				    for (Resource c : allContexts) {
				        String iri = c.stringValue();
					
				        if (iri.contains("/maze")) {
				            mazeLike++;
				        }
				        if (!iri.startsWith(expectedHost)) {
				            hostPrefixMismatch++;
				        }
				    }
				
				    log.info("Total contexts: {}", total);
				    log.info("Contexts containing '/maze': {}", mazeLike);
				    log.info("Contexts with HOST mismatch (not starting with {}): {}", expectedHost, hostPrefixMismatch);
				
				    if (mazeLike > 0) {
				        log.info("Listing all graph contexts containing '/maze':");
				        for (Resource c : allContexts) {
				            if (c.stringValue().contains("/maze")) {
				                long count = conn2.size(c);
				                log.info("  {} ({} triples)", c, count);
				            }
				        }
				    } else {
				        log.warn("No /maze context found. Root maze graph not present as named graph.");
				    }
				
				    log.info("Checking a sample of non-gsp prefixed graphs:");
				    int printed = 0;
				    for (Resource c : allContexts) {
				        if (printed >= 5) break;
				        String iri = c.stringValue();
				        if (!iri.contains("/gsp/") && iri.startsWith(expectedHost)) {
				            long count = conn2.size(c);
				            log.info("  Non-gsp graph: {} ({} triples)", iri, count);
				            printed++;
				        }
				    }

					log.info("Checking a sample of host prefix mismatch graphs:");
					printed = 0;
					for (Resource c : allContexts) {
						if (printed >= 5) break;
						String iri = c.stringValue();
						if (!iri.startsWith(expectedHost)) {
							long count = conn2.size(c);
							log.info("  Host prefix mismatch graph: {} ({} triples)", iri, count);
							printed++;
						}
					}

				    log.info("=== End Named Graph Inspection ===");
				}

			}
		}
		log.info("init dataset loaded");

		// --------------------------------------------------
		// Linked Data dereferencing servlet (must be after /gsp/* mapping and before DefaultServlet)
		// --------------------------------------------------
		ResourceConfig ldConfig = new ResourceConfig();
		ldConfig.register(LinkedDataDereferenceResource.class);
		ldConfig.register(MoveResource.class);
		ldConfig.register(CorsFilter.class);

		Servlet ldContainer = new ServletContainer(ldConfig);
		ServletHolder ldHolder = new ServletHolder("BOLD LD dereferencing servlet", ldContainer);

		// Mount on /* so any path is dereferenced as RDF if it exists as a named graph
		context.addServlet(ldHolder, "/*");

		// Share the same repository handle with the LD container
		((HttpServlet) ldContainer).getServletContext().setAttribute(SAIL_REPOSITORY_SERVLET_ATTRIBUTE, repo);
		
		// Initialize and share the maze game engine (singleton)
		// Extract maze name from task (e.g., "sim-UnsafeMaze" -> "UnsafeMaze")
		String mazeName = extractMazeName(task);
		log.info("Initializing MazeGameEngine for maze: {}", mazeName != null ? mazeName : "generic");
		
		// Build full paths for all additional rulesets (e.g., "Stigmergy" -> "Global/Stigmergy")
		// ALWAYS include Global as the base
		List<String> additionalRulesetPaths = new ArrayList<>();
		additionalRulesetPaths.add("Global");
		
		// Add any additional rulesets on top of Global
		for (String ruleset : additionalRulesets) {
			additionalRulesetPaths.add("Global/" + ruleset);
		}
		log.info("Additional ruleset paths: {}", additionalRulesetPaths);
		
		MazeGameEngine gameEngine = new MazeGameEngine(repo, mazeName, additionalRulesetPaths);
		((HttpServlet) ldContainer).getServletContext().setAttribute(MAZE_GAME_ENGINE_SERVLET_ATTRIBUTE, gameEngine);
		log.info("MazeGameEngine initialized and stored in ServletContext");

		server.join();
		return;
    }

    // Instantiate Sail from configuration; falls back to MemoryStore on error/missing config.
    private static Sail createSail(Properties config) {
        String protocol = config.getProperty(SERVER_PROTOCOL_KEY);
        if ("ldp".equalsIgnoreCase(protocol)) {
            log.info("Using LDPInferencer over MemoryStore.");
            return new LDPInferencer(new MemoryStore());
        }
        log.info("Using default MemoryStore Sail.");
        return new MemoryStore();
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
