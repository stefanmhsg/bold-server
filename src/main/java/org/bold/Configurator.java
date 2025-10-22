package org.bold;

import org.bold.gsp.GraphStoreProtocolRESTResource;
import org.bold.gsp.IsPutOrNamedGraphInDatasetFilter;
import org.bold.gsp.SimStateFilter;
import org.bold.sim.SimulationResource;
import org.bold.gsp.StatisticsApplicationEventListener;
import org.bold.io.FileUtils;
import org.bold.ld.CorsFilter;
import org.bold.ld.LinkedDataDereferenceResource;
import org.bold.sim.InteractionHistory;
import org.bold.sim.SimulationEngine;
import org.bold.sim.UpdateHistory;
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

    private final static String INIT_UPDATE_KEY = "bold.init.update";

    private final static String RUNTIME_UPDATE_KEY = "bold.runtime.update";

    private final static String RUNTIME_QUERY_KEY = "bold.runtime.query";

    private final static String REPLAY_DUMP_KEY = "bold.replay.dump";

	public static final String SAIL_REPOSITORY_SERVLET_ATTRIBUTE = "SAIL_REPOSITORY_SERVLET_ATTRIBUTE";
	public static final String SIMULATION_ENGINE_SERVLET_ATTRIBUTE = "SIMULATION_SERVLET_ATTRIBUTE";
	public static final String INTERACTION_HISTORY_SERVLET_ATTRIBUTE = "INTERACTION_HISTORY_SERVLET_ATTRIBUTE";

	public static final String WELCOME_DIRECTORY_FILEPATH = "bold.welcome.directory.filepath";

	public static final String RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL = "gsp/";
	public static final String SIMULATION_RESOURCE_TARGET = "sim"; // TODO make configurable

    private static final String SERVER_PROTOCOL_KEY = "bold.server.protocol";

    public static void main(String[] args) throws Exception {
        // TODO more advanced CLI
        String task = args.length > 0 ? args[0] : "sim";

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

				// Copying the default graph to where we would look for it on the REST API.

				//Iterable<Statement> triplesFromDefaultGraph = ds.getStatements(null, null, null, (Resource) null);
				//for (Statement s : triplesFromDefaultGraph) {
				//	log.info("Adding triple to named graph '{}': {} {} {} -> Context {}", rdfBaseURI, s.getSubject(), s.getPredicate(), s.getObject(), s.getContext());
				//
				//	conn.add(s, new Resource[] { conn.getValueFactory().createIRI(rdfBaseURI.toString()) });
				//}
				conn.commit();

				// Verification log output
				try (SailRepositoryConnection conn2 = repo.getConnection()) {
				
				    log.info("=== Named Graph Inspection ===");
				
				    int total = 0;
				    int mazeLike = 0;
				    int gspPrefixed = 0;
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
				        if (iri.contains("/gsp/")) {
				            gspPrefixed++;
				        }
				        if (!iri.startsWith(expectedHost)) {
				            hostPrefixMismatch++;
				        }
				    }
				
				    log.info("Total contexts: {}", total);
				    log.info("Contexts containing '/maze': {}", mazeLike);
				    log.info("Contexts containing '/gsp/': {}", gspPrefixed);
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

					log.info("Checking a sample of gsp prefixed graphs:");
					printed = 0;
					for (Resource c : allContexts) {
						if (printed >= 5) break;
						String iri = c.stringValue();
						if (iri.contains("/gsp/") && iri.startsWith(expectedHost)) {
							long count = conn2.size(c);
							log.info("  GSP graph: {} ({} triples)", iri, count);
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
		// Setting up the Graph Store Protocol REST resource at /gsp/* as Jersey servlet
		// --------------------------------------------------

		ResourceConfig resConfig = new ResourceConfig();

		resConfig.register(GraphStoreProtocolRESTResource.class);
		resConfig.register(IsPutOrNamedGraphInDatasetFilter.class);
		resConfig.register(CorsFilter.class);

		if (config.containsKey(INIT_UPDATE_KEY) || config.containsKey(RUNTIME_UPDATE_KEY)
				|| config.containsKey(RUNTIME_QUERY_KEY) || config.containsKey(REPLAY_DUMP_KEY)) {
			// Simulation mode enabled (see also below).
			resConfig.register(StatisticsApplicationEventListener.class);
			resConfig.register(SimStateFilter.class);
		}

		Servlet container = new ServletContainer(resConfig);
		ServletHolder holder = new ServletHolder(
				"BOLD server servlet working on RDF dataset from " + config.getProperty(INIT_DATASET_KEY), container);
		context.addServlet(holder, "/" + RELATIVE_BASE_URI_WITH_TRAILING_SLASH_FOR_GRAPH_STORE_PROTOCOL + "*");

		((HttpServlet) container).getServletContext().setAttribute(SAIL_REPOSITORY_SERVLET_ATTRIBUTE, repo);

		if (config.containsKey(INIT_UPDATE_KEY) || config.containsKey(RUNTIME_UPDATE_KEY)
				|| config.containsKey(RUNTIME_QUERY_KEY) || config.containsKey(REPLAY_DUMP_KEY)) {
			// Simulation mode enabled.
			InteractionHistory interactionHistory = new InteractionHistory();
			((HttpServlet) container).getServletContext().setAttribute(INTERACTION_HISTORY_SERVLET_ATTRIBUTE,
					interactionHistory);

			UpdateHistory history = new UpdateHistory(); // TODO finer-grained reporting: distinct histories
			SailRepositoryConnection engineConnection = repo.getConnection();
			((NotifyingSailConnection) engineConnection.getSailConnection()).addConnectionListener(history);
			SailRepositoryConnection handlerConnection = repo.getConnection();
			((NotifyingSailConnection) handlerConnection.getSailConnection()).addConnectionListener(history);

			SimulationEngine engine = new SimulationEngine(rdfBaseURI.toString(), engineConnection, history,
					interactionHistory);
			for (String f : FileUtils.listFiles(config.getProperty(INIT_DATASET_KEY))) {
				engine.registerDataset(f);
			}

			if (config.getProperty(INIT_UPDATE_KEY) != null)
				for (String f : FileUtils.listFiles(config.getProperty(INIT_UPDATE_KEY))) {
					engine.registerSingleUpdate(f);
				}

			if (config.getProperty(RUNTIME_UPDATE_KEY) != null)
				for (String f : FileUtils.listFiles(config.getProperty(RUNTIME_UPDATE_KEY))) {
					engine.registerContinuousUpdate(f);
				}

			if (config.getProperty(RUNTIME_QUERY_KEY) != null)
				for (String f : FileUtils.listFiles(config.getProperty(RUNTIME_QUERY_KEY))) {
					engine.registerQuery(f);
				}

			String filenamePattern = config.getProperty(REPLAY_DUMP_KEY);
			engine.setDumpPattern(filenamePattern);
			engine.registrationDone();

			// The resource that starts the simulation upon an HTTP-POST request.
			resConfig = new ResourceConfig();
			resConfig.register(SimulationResource.class);
			container = new ServletContainer(resConfig);
			holder = new ServletHolder("BOLD simulation controller servlet", container);
			context.addServlet(holder, "/" + SIMULATION_RESOURCE_TARGET);
			((HttpServlet) container).getServletContext().setAttribute(SIMULATION_ENGINE_SERVLET_ATTRIBUTE, engine);
		}

		// --------------------------------------------------
		// Linked Data dereferencing servlet (must be after /gsp/* mapping and before DefaultServlet)
		// --------------------------------------------------
		ResourceConfig ldConfig = new ResourceConfig();
		ldConfig.register(LinkedDataDereferenceResource.class);
		ldConfig.register(CorsFilter.class);

		Servlet ldContainer = new ServletContainer(ldConfig);
		ServletHolder ldHolder = new ServletHolder("BOLD LD dereferencing servlet", ldContainer);

		// Mount on /* so any non GSP path is dereferenced as RDF if it exists as a named graph
		context.addServlet(ldHolder, "/*");

		// Share the same repository handle with the LD container
		((HttpServlet) ldContainer).getServletContext().setAttribute(SAIL_REPOSITORY_SERVLET_ATTRIBUTE, repo);

		// --------------------------------------------------
		// Default servlet for serving static content
		// --------------------------------------------------
		// Some welcoming documentation at the root resource, implemented using Jersey's DefaultServlet.
		String welcomeFilePath = (String) config.get(WELCOME_DIRECTORY_FILEPATH);
		if (welcomeFilePath != null) {
			holder = new ServletHolder("default", DefaultServlet.class);
			holder.setInitParameter("resourceBase", welcomeFilePath);
			context.addServlet(holder, "/");
		}

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
}
