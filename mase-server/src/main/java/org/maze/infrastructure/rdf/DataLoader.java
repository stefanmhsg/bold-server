package org.maze.infrastructure.rdf;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.infrastructure.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads RDF data into the repository from files.
 */
public class DataLoader {
    
    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    
    /**
     * Load RDF data from files matching a pattern into the repository.
     * 
     * @param repository The target repository
     * @param datasetPattern File pattern (e.g., "data/*.trig")
     * @param baseUri Base URI for resolving relative URIs
     * @throws IOException if file loading fails
     */
    public void loadData(SailRepository repository, String datasetPattern, URI baseUri) throws IOException {
        log.info("Loading data from pattern: {}", datasetPattern);
        
        try (RepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            try {
                loadData(conn, datasetPattern, baseUri);
                conn.commit();
            } catch (IOException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
        
        logGraphStatistics(repository);
    }

    /**
     * Load RDF data into an existing transaction.
     *
     * @param conn active repository connection owned by the caller
     * @param datasetPattern file pattern (e.g., "data/*.trig")
     * @param baseUri base URI for resolving relative URIs
     * @throws IOException if file loading fails
     */
    public void loadData(RepositoryConnection conn, String datasetPattern, URI baseUri) throws IOException {
        List<String> filenames = new ArrayList<>(FileUtils.listFiles(datasetPattern));
        Collections.sort(filenames);
        if (filenames.isEmpty()) {
            throw new IOException("No RDF files matched dataset pattern: " + datasetPattern);
        }

        for (String filename : filenames) {
            loadFile(conn, filename, baseUri);
        }
    }
    
    private void loadFile(RepositoryConnection conn, String filename, URI baseUri) throws IOException {
        log.info("Discovered RDF file: {}", filename);
        RDFFormat format = Rio.getParserFormatForFileName(filename)
                .orElseThrow(() -> new IOException("Unknown RDF format for file: " + filename));
        log.info("Parsing file '{}' with format '{}'", filename, format.getName());
        
        Model ds = Rio.parse(FileUtils.getFileOrResource(filename, DataLoader.class.getClassLoader()), 
                            baseUri.toString(), format);
        log.info("Parsed model has {} statements", ds.size());
        
        conn.add(ds);
    }
    
    private void logGraphStatistics(SailRepository repository) {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            log.info("=== Named Graph Inspection ===");
            
            List<Resource> allContexts = new ArrayList<>();
            try (RepositoryResult<? extends Resource> ctxIt = conn.getContextIDs()) {
                while (ctxIt.hasNext()) {
                    allContexts.add(ctxIt.next());
                }
            }
            
            int total = allContexts.size();
            int mazeLike = 0;
            int hostPrefixMismatch = 0;
            String expectedHost = "http://127.0.1.1:8080";
            
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
                        long count = conn.size(c);
                        log.info("  {} ({} triples)", c, count);
                    }
                }
            } else {
                log.warn("No /maze context found. Root maze graph not present as named graph.");
            }
            
            log.info("=== End Named Graph Inspection ===");
        }
    }
}
