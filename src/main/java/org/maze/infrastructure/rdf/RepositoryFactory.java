package org.maze.infrastructure.rdf;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.maze.infrastructure.config.ServerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fau.rw.ti.LDPInferencer;

/**
 * Factory for creating RDF repositories with appropriate Sail implementations.
 */
public class RepositoryFactory {
    
    private static final Logger log = LoggerFactory.getLogger(RepositoryFactory.class);
    
    /**
     * Create a SailRepository based on configuration.
     * 
     * @param config Server configuration
     * @return Configured SailRepository
     */
    public SailRepository createRepository(ServerConfiguration config) {
        Sail sail = createSail(config);
        return new SailRepository(sail);
    }
    
    /**
     * Instantiate Sail from configuration; falls back to MemoryStore on error/missing config.
     */
    private Sail createSail(ServerConfiguration config) {
        String protocol = config.getServerProtocol();
        if ("ldp".equalsIgnoreCase(protocol)) {
            log.info("Using LDPInferencer over MemoryStore.");
            return new LDPInferencer(new MemoryStore());
        }
        log.info("Using default MemoryStore Sail.");
        return new MemoryStore();
    }
}
