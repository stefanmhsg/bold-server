package org.maze.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.junit.jupiter.api.Test;
import org.maze.domain.model.SparqlResult;
import org.maze.domain.rules.MazeRule;
import org.maze.domain.vocab.MazeVocab;
import org.maze.infrastructure.rdf.RepositoryFactory;
import org.maze.infrastructure.storage.MazeRuleLoader;

class CcrsMazeRuleTest {

    @Test
    void ccrsMaintenanceRulesAreDiscoveredAndExecutable() throws Exception {
        MazeRuleLoader loader = new MazeRuleLoader();

        assertTrue(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-keys.rq"));
        assertTrue(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/cleanup-move-success-events.rq"));
        assertFalse(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-redkey.rq"));
        assertFalse(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-bluekey.rq"));
        assertFalse(loader.discoverRuleFiles("CcrsMaze").contains("CcrsMaze/unlock-greenkey.rq"));

        SailRepository repository = new RepositoryFactory().createRepository(null);
        SparqlService sparqlService = new SparqlService(repository);

        MazeRule unlockRule = loader.loadRule("CcrsMaze/unlock-keys.rq");
        SparqlResult unlockResult = sparqlService.executeQuery(unlockRule.getSparqlQuery(), "text/plain");
        assertTrue(unlockResult.success(), unlockResult.errorMessage());

        MazeRule cleanupRule = loader.loadRule("CcrsMaze/cleanup-move-success-events.rq");
        SparqlResult cleanupResult = sparqlService.executeQuery(cleanupRule.getSparqlQuery(), "text/plain");
        assertTrue(cleanupResult.success(), cleanupResult.errorMessage());
    }

    @Test
    void cleanupRemovesMoveSuccessEventsAfterTheyCanTriggerRelock() throws Exception {
        MazeRuleLoader loader = new MazeRuleLoader();
        MazeRule unlockRule = loader.loadRule("CcrsMaze/unlock-keys.rq");
        MazeRule cleanupRule = loader.loadRule("CcrsMaze/cleanup-move-success-events.rq");
        SailRepository repository = new RepositoryFactory().createRepository(null);
        ValueFactory vf = repository.getValueFactory();

        IRI graph = vf.createIRI("http://127.0.1.1:8080/cells/44/45");
        IRI target = vf.createIRI("http://127.0.1.1:8080/cells/45/45");
        IRI action = vf.createIRI("http://127.0.1.1:8080/cells/44/45#unlock");
        IRI event = vf.createIRI("http://127.0.1.1:8080/cells/44/45#move-event");
        IRI otherGraph = vf.createIRI("http://127.0.1.1:8080/cells/1/1");
        IRI otherEvent = vf.createIRI("http://127.0.1.1:8080/cells/1/1#move-event");
        IRI otherTarget = vf.createIRI("http://127.0.1.1:8080/cells/1/2");
        IRI agent = vf.createIRI("http://127.0.1.1:8080/agents/alice");
        IRI state = vf.createIRI(MazeVocab.DYNMAZE_NS + "state");
        IRI unlocked = vf.createIRI(MazeVocab.DYNMAZE_NS + "unlocked");
        IRI locked = vf.createIRI(MazeVocab.DYNMAZE_NS + "locked");
        IRI hasStatus = vf.createIRI(MazeVocab.DYNMAZE_NS + "hasStatus");
        IRI done = vf.createIRI(MazeVocab.DYNMAZE_NS + "done");
        IRI open = vf.createIRI(MazeVocab.DYNMAZE_NS + "open");
        IRI keyValue = vf.createIRI(MazeVocab.DYNMAZE_NS + "keyValue");
        IRI moveSuccessEvent = vf.createIRI(MazeVocab.MOVE_SUCCESS_EVENT);
        IRI targetCell = vf.createIRI(MazeVocab.DYNMAZE_NS + "targetCell");
        IRI moveAgent = vf.createIRI(MazeVocab.DYNMAZE_NS + "agent");
        IRI dateTime = vf.createIRI(XMLSchema.NAMESPACE + "dateTime");
        IRI hydraOperation = vf.createIRI("http://www.w3.org/ns/hydra/core#operation");
        IRI south = vf.createIRI(MazeVocab.MAZE_NS + "south");
        IRI green = vf.createIRI(MazeVocab.MAZE_NS + "green");

        try (SailRepositoryConnection conn = repository.getConnection()) {
            conn.add(graph, RDF.TYPE, vf.createIRI(MazeVocab.MAZE_NS + "Cell"), graph);
            conn.add(graph, RDF.TYPE, vf.createIRI(MazeVocab.DYNMAZE_NS + "Lock"), graph);
            conn.add(graph, hydraOperation, action, graph);
            conn.add(graph, state, unlocked, graph);
            conn.add(graph, south, target, graph);
            conn.add(graph, green, target, graph);
            conn.add(graph, keyValue, vf.createLiteral("bluekey-9347"), graph);
            conn.add(action, hasStatus, done, graph);
            conn.add(action, dateTime, vf.createLiteral("2026-05-15T10:00:00Z", XMLSchema.DATETIME), graph);
            conn.add(event, RDF.TYPE, moveSuccessEvent, graph);
            conn.add(event, moveAgent, agent, graph);
            conn.add(event, targetCell, target, graph);
            conn.add(event, dateTime, vf.createLiteral("2026-05-15T10:00:01Z", XMLSchema.DATETIME), graph);
            conn.add(otherEvent, RDF.TYPE, moveSuccessEvent, otherGraph);
            conn.add(otherEvent, moveAgent, agent, otherGraph);
            conn.add(otherEvent, targetCell, otherTarget, otherGraph);
            conn.add(otherEvent, dateTime, vf.createLiteral("2026-05-15T10:00:01Z", XMLSchema.DATETIME), otherGraph);
        }

        SparqlService sparqlService = new SparqlService(repository);
        SparqlResult unlockResult = sparqlService.executeQuery(unlockRule.getSparqlQuery(), "text/plain");

        assertTrue(unlockResult.success(), unlockResult.errorMessage());
        try (SailRepositoryConnection conn = repository.getConnection()) {
            assertTrue(conn.hasStatement(graph, state, locked, false, graph));
            assertTrue(conn.hasStatement(action, hasStatus, open, false, graph));
            assertFalse(conn.hasStatement(graph, south, target, false, graph));
            assertFalse(conn.hasStatement(graph, green, target, false, graph));
            assertFalse(conn.hasStatement(graph, keyValue, null, false, graph));
            assertTrue(conn.hasStatement(event, RDF.TYPE, moveSuccessEvent, false, graph));
            assertTrue(conn.hasStatement(otherEvent, RDF.TYPE, moveSuccessEvent, false, otherGraph));
        }

        SparqlResult cleanupResult = sparqlService.executeQuery(cleanupRule.getSparqlQuery(), "text/plain");

        assertTrue(cleanupResult.success(), cleanupResult.errorMessage());
        try (SailRepositoryConnection conn = repository.getConnection()) {
            assertFalse(conn.hasStatement(event, null, null, false, graph));
            assertFalse(conn.hasStatement(otherEvent, null, null, false, otherGraph));
        }
    }

    @Test
    void ccrsRuleOrderingRunsCleanupAfterUnlockAndBeforeMove() {
        SailRepository repository = new RepositoryFactory().createRepository(null);
        MazeRuleService ruleService = new MazeRuleService(
                repository,
                "CcrsMaze",
                List.of("Global"),
                List.of("normalize_maze_locks*", "ccrs*", "unlock*", "cleanup*", "move*"));

        List<String> ruleNames = ruleService.getRules().stream()
                .map(MazeRule::getName)
                .toList();

        int unlockIndex = ruleNames.indexOf("CcrsMaze/unlock-keys");
        int cleanupIndex = ruleNames.indexOf("CcrsMaze/cleanup-move-success-events");
        int moveIndex = ruleNames.indexOf("Global/move");

        assertTrue(unlockIndex >= 0);
        assertTrue(cleanupIndex > unlockIndex);
        assertTrue(moveIndex > cleanupIndex);
    }
}
