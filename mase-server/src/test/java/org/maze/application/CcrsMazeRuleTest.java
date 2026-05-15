package org.maze.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.junit.jupiter.api.Test;
import org.maze.domain.model.SparqlResult;
import org.maze.domain.rules.MazeRule;
import org.maze.infrastructure.rdf.RepositoryFactory;
import org.maze.infrastructure.storage.MazeRuleLoader;

class CcrsMazeRuleTest {

    @Test
    void combinedUnlockRuleIsDiscoveredAndExecutable() throws Exception {
        MazeRuleLoader loader = new MazeRuleLoader();

        assertTrue(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-keys.rq"));
        assertFalse(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-redkey.rq"));
        assertFalse(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-bluekey.rq"));
        assertFalse(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-greenkey.rq"));

        MazeRule rule = loader.loadRule("CcrsMaze/unlock-keys.rq");
        SailRepository repository = new RepositoryFactory().createRepository(null);
        SparqlResult result = new SparqlService(repository).executeQuery(rule.getSparqlQuery(), "text/plain");

        assertTrue(result.success(), result.errorMessage());
    }
}
