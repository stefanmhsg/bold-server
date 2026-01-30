package org.maze.infrastructure.rdf;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.NotifyingSail;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.maze.infrastructure.config.ServerConfiguration;

/**
 * Factory for creating RDF repositories with appropriate Sail implementations.
 */
public class RepositoryFactory { 
    
    /**
     * Create a SailRepository based on configuration.
     * 
     * @param config Server configuration
     * @return Configured SailRepository
     */
    public SailRepository createRepository(ServerConfiguration config) {

        MemoryStore baseStore = new MemoryStore();

        // Wrap with MazeNotifyingSail to enable update notifications
        NotifyingSail notifyingSail = new MazeNotifyingSail(baseStore);

        SailRepository repository = new SailRepository((Sail) notifyingSail);

        return repository;
    }
    
}
