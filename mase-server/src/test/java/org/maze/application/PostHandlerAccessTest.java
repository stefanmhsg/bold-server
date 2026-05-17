package org.maze.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.maze.domain.model.PostResult;
import org.maze.domain.vocab.MazeVocab;
import org.maze.infrastructure.config.ServerConfiguration;
import org.maze.infrastructure.rdf.DataLoader;
import org.maze.infrastructure.rdf.RepositoryFactory;
import org.maze.infrastructure.scenario.ScenarioPackage;

class PostHandlerAccessTest {

    private static final String BASE = "http://127.0.1.1:8080";
    private static final String MAZE = BASE + "/maze";
    private static final String START = BASE + "/cells/0/0";
    private static final String EAST_OF_START = BASE + "/cells/0/1";
    private static final String NOT_CURRENT_SOURCE = BASE + "/cells/0/2";
    private static final String BOB = BASE + "/agents/bob";
    private static final String TEST_NOTE = "http://example.org/test#note";
    private static final Path SMALL_MAZE_SCENARIO = Path.of("scenarios/smallmaze");

    private SailRepository repository;
    private PostHandler postHandler;
    private String currentTestName;

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        currentTestName = testInfo.getDisplayName();
        logTestStart();

        repository = new RepositoryFactory().createRepository(null);
        ServerConfiguration config = smallMazeConfig();
        new DataLoader().loadData(
                repository,
                config.getInitDataset(),
                URI.create(BASE + "/gsp/"));

        MazeRuleService ruleService = smallMazeRuleService();
        runStartupRules(ruleService);

        SparqlService sparqlService = new SparqlService(repository);
        AccessValidator accessValidator = new AccessValidator(repository, sparqlService);
        postHandler = new PostHandler(repository, ruleService, accessValidator);
    }

    @Test
    void movementRequestCanEnterMazeAtStartCell() {
        PostResult result = enterMaze("bob");
        logResult("enter maze at start", result);

        assertTrue(result.isSuccess());
        assertAgentInCell(BOB, START);
        assertNoMovementRequestTriple(BOB, MAZE, START);
    }

    @Test
    void localInteractionToCurrentCellIsAccepted() {
        enterMaze("bob");

        PostResult result = post("bob", START, turtle(
                "<" + START + "> <" + TEST_NOTE + "> \"local\" ."));
        logResult("local POST to current cell", result);

        assertTrue(result.isSuccess());
        assertStatement(START, START, TEST_NOTE, "local");
    }

    @Test
    void localInteractionToAdjacentCellIsDenied() {
        enterMaze("bob");

        PostResult result = post("bob", EAST_OF_START, turtle(
                "<" + EAST_OF_START + "> <" + TEST_NOTE + "> \"not local\" ."));
        logResult("local POST to adjacent cell", result);

        assertFalse(result.isSuccess());
        assertEquals(403, result.statusCode());
        assertNoStatement(EAST_OF_START, EAST_OF_START, TEST_NOTE, "not local");
    }

    @Test
    void movementRequestToAdjacentCellIsAccepted() {
        enterMaze("bob");

        PostResult result = move("bob", EAST_OF_START, START);
        logResult("move east from start", result);

        assertTrue(result.isSuccess());
        assertAgentInCell(BOB, EAST_OF_START);
        assertAgentNotInCell(BOB, START);
        assertNoMovementRequestTriple(BOB, START, EAST_OF_START);
    }

    @Test
    void movementRequestWithWrongSourceIsRejectedAsConflict() {
        enterMaze("bob");

        PostResult result = move("bob", EAST_OF_START, NOT_CURRENT_SOURCE);
        logResult("move with wrong claimed source", result);

        assertFalse(result.isSuccess());
        assertEquals(409, result.statusCode());
        assertAgentInCell(BOB, START);
        assertAgentNotInCell(BOB, EAST_OF_START);
    }

    @Test
    void movementRequestWithMultipleSourcesIsUnprocessable() {
        enterMaze("bob");

        PostResult result = post("bob", EAST_OF_START, turtle(
                "<" + BOB + "> <" + MazeVocab.ENTERS_FROM + "> <" + START + "> .\n" +
                "<" + BOB + "> <" + MazeVocab.ENTERS_FROM + "> <" + NOT_CURRENT_SOURCE + "> ."));
        logResult("move with multiple sources", result);

        assertFalse(result.isSuccess());
        assertEquals(422, result.statusCode());
        assertAgentInCell(BOB, START);
        assertAgentNotInCell(BOB, EAST_OF_START);
    }

    @Test
    void movementRequestRollsBackWhenNoMovementRuleMaterializesIt() throws Exception {
        MazeRuleService noRules = new MazeRuleService(repository, List.of(), Path.of("."), List.of());
        AccessValidator accessValidator = new AccessValidator(repository, new SparqlService(repository));
        PostHandler noRulePostHandler = new PostHandler(repository, noRules, accessValidator);

        PostResult result = noRulePostHandler.performPost("bob", START, movementModel(START, MAZE));
        logResult("movement without materializing rule", result);

        assertFalse(result.isSuccess());
        assertEquals(409, result.statusCode());
        assertAgentNotInCell(BOB, START);
        assertNoMovementRequestTriple(BOB, MAZE, START);
    }

    private ServerConfiguration smallMazeConfig() throws Exception {
        return ServerConfiguration.forScenarioPackage(SMALL_MAZE_SCENARIO);
    }

    private MazeRuleService smallMazeRuleService() throws Exception {
        ServerConfiguration config = smallMazeConfig();
        ScenarioPackage scenario = config.getScenarioPackage().orElseThrow();
        return new MazeRuleService(
                repository,
                scenario.ruleFiles(),
                scenario.root(),
                config.getRuleExecutionOrder());
    }

    private void runStartupRules(MazeRuleService ruleService) {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            ruleService.executeRules(conn);
            conn.commit();
        }
    }

    private PostResult enterMaze(String agentName) {
        return post(agentName, START, movementModel(START, MAZE));
    }

    private PostResult move(String agentName, String targetCell, String sourceCell) {
        return post(agentName, targetCell, movementModel(targetCell, sourceCell));
    }

    private PostResult post(String agentName, String graphIri, Model model) {
        return postHandler.performPost(agentName, graphIri, model, null);
    }

    private void logTestStart() {
        System.out.println();
        System.out.println("[TEST] " + currentTestName);
    }

    private void logResult(String action, PostResult result) {
        System.out.printf(
                "[RESULT] %s -> success=%s status=%d graph=%s triples=%d error=%s%n",
                action,
                result.isSuccess(),
                result.statusCode(),
                result.graphUri(),
                result.triplesAdded(),
                result.errorMessage());
    }

    private Model movementModel(String targetCell, String sourceCell) {
        return turtle(targetCell, "<" + BOB + "> <" + MazeVocab.ENTERS_FROM + "> <" + sourceCell + "> .");
    }

    private Model turtle(String turtle) {
        return turtle(START, turtle);
    }

    private Model turtle(String graphIri, String turtle) {
        try {
            ValueFactory vf = repository.getValueFactory();
            IRI graph = vf.createIRI(graphIri);
            return Rio.parse(
                    new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
                    graphIri,
                    RDFFormat.TURTLE,
                    graph);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse test RDF", e);
        }
    }

    private void assertAgentInCell(String agentUri, String cellUri) {
        assertTrue(hasStatement(cellUri, cellUri, MazeVocab.MAZE_NS + "contains", agentUri));
    }

    private void assertAgentNotInCell(String agentUri, String cellUri) {
        assertFalse(hasStatement(cellUri, cellUri, MazeVocab.MAZE_NS + "contains", agentUri));
    }

    private void assertNoMovementRequestTriple(String agentUri, String sourceCell, String targetCell) {
        assertFalse(hasStatement(targetCell, agentUri, MazeVocab.ENTERS_FROM, sourceCell));
    }

    private void assertStatement(String graphUri, String subjectUri, String predicateUri, String literalValue) {
        assertTrue(hasLiteralStatement(graphUri, subjectUri, predicateUri, literalValue));
    }

    private void assertNoStatement(String graphUri, String subjectUri, String predicateUri, String literalValue) {
        assertFalse(hasLiteralStatement(graphUri, subjectUri, predicateUri, literalValue));
    }

    private boolean hasStatement(String graphUri, String subjectUri, String predicateUri, String objectUri) {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            ValueFactory vf = conn.getValueFactory();
            return conn.hasStatement(
                    vf.createIRI(subjectUri),
                    vf.createIRI(predicateUri),
                    vf.createIRI(objectUri),
                    false,
                    vf.createIRI(graphUri));
        }
    }

    private boolean hasLiteralStatement(String graphUri, String subjectUri, String predicateUri, String literalValue) {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            ValueFactory vf = conn.getValueFactory();
            return conn.hasStatement(
                    vf.createIRI(subjectUri),
                    vf.createIRI(predicateUri),
                    vf.createLiteral(literalValue),
                    false,
                    vf.createIRI(graphUri));
        }
    }
}
