package org.maze.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.maze.application.tx.TransactionTraceContext;
import org.maze.application.tx.TransactionTraceMode;
import org.maze.domain.model.PostResult;
import org.maze.infrastructure.rdf.DataLoader;
import org.maze.infrastructure.rdf.RepositoryFactory;

class MazeResetServiceTest {

    private static final String BASE = "http://127.0.1.1:8080";
    private static final URI RDF_BASE_URI = URI.create(BASE + "/gsp/");
    private static final String SMALL_MAZE_DATASET = "data/SmallMaze.trig";
    private static final String MAZE = BASE + "/maze";
    private static final String START = BASE + "/cells/0/0";
    private static final String CELL = BASE + "/cells/reset-test";
    private static final String NOTE = "http://example.org/test#note";

    private SailRepository repository;
    private MazeMutationCoordinator mutationCoordinator;

    @BeforeEach
    void setUp() throws Exception {
        repository = new RepositoryFactory().createRepository(null);
        mutationCoordinator = new MazeMutationCoordinator();
        new DataLoader().loadData(repository, SMALL_MAZE_DATASET, RDF_BASE_URI);
    }

    @Test
    void resetRestoresConfiguredDatasetAndRemovesRuntimeMutation() throws Exception {
        addNote(START, START, "runtime");
        assertTrue(hasNote(START, START, "runtime"));

        MazeResetService resetService = resetService(SMALL_MAZE_DATASET, smallMazeRuleService(), null);

        resetService.resetToInitialDataset();

        assertFalse(hasNote(START, START, "runtime"));
        assertTrue(hasStatement(MAZE, MAZE, RDF.TYPE.stringValue(),
                "http://www.w3.org/ns/ldp#BasicContainer"));
        assertTrue(hasStatement(START, START, RDF.TYPE.stringValue(),
                "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#Cell"));
    }

    @Test
    void resetRollsBackWhenDatasetCannotBeParsed(@TempDir Path tempDir) throws Exception {
        Path badTrig = tempDir.resolve("bad.trig");
        Files.writeString(badTrig, "this is not valid TriG", StandardCharsets.UTF_8);

        addNote(START, START, "preserved");
        MazeResetService resetService = resetService(badTrig.toString(), smallMazeRuleService(), null);

        assertThrows(Exception.class, resetService::resetToInitialDataset);

        assertTrue(hasNote(START, START, "preserved"));
        assertTrue(hasStatement(START, START, RDF.TYPE.stringValue(),
                "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#Cell"));
    }

    @Test
    void resetWaitsForInFlightPostMutation() throws Exception {
        createTestCellGraph();

        BlockingRuleService blockingRules = new BlockingRuleService(repository);
        AccessValidator accessValidator = new AccessValidator(
                repository,
                new SparqlService(repository, mutationCoordinator));
        PostHandler postHandler = new PostHandler(
                repository,
                blockingRules,
                accessValidator,
                TransactionTraceMode.OFF,
                mutationCoordinator);
        MazeResetService resetService = resetService(SMALL_MAZE_DATASET, smallMazeRuleService(), accessValidator);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PostResult> postFuture = executor.submit(() ->
                    postHandler.performPost(null, CELL, noteModel(1), null));

            assertTrue(blockingRules.awaitRuleEntered(), "POST did not enter the blocking rule");

            Future<?> resetFuture = executor.submit(() -> {
                resetService.resetToInitialDataset();
                return null;
            });

            Thread.sleep(150);
            assertFalse(resetFuture.isDone(), "Reset should wait for the in-flight POST mutation");

            blockingRules.releaseRule();

            assertTrue(postFuture.get(5, TimeUnit.SECONDS).isSuccess());
            resetFuture.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private MazeResetService resetService(String datasetPattern,
                                          MazeRuleService ruleService,
                                          AccessValidator accessValidator) {
        return new MazeResetService(
                repository,
                datasetPattern,
                RDF_BASE_URI,
                ruleService,
                TransactionTraceMode.OFF,
                mutationCoordinator,
                accessValidator);
    }

    private MazeRuleService smallMazeRuleService() {
        return new MazeRuleService(
                repository,
                "SmallMaze",
                List.of("Global"),
                List.of("unlock*", "stigmergy-traffic-increment*", "stigmergy-traffic-add*",
                        "stigmergy-traffic-new*", "move*"));
    }

    private void createTestCellGraph() {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            ValueFactory vf = conn.getValueFactory();
            IRI cell = vf.createIRI(CELL);

            conn.begin();
            conn.add(cell, RDF.TYPE, vf.createIRI("http://example.org/test#Cell"), cell);
            conn.commit();
        }
    }

    private void addNote(String subjectIri, String graphIri, String value) {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.add(
                    vf.createIRI(subjectIri),
                    vf.createIRI(NOTE),
                    vf.createLiteral(value),
                    vf.createIRI(graphIri));
            conn.commit();
        }
    }

    private boolean hasNote(String subjectIri, String graphIri, String value) {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            ValueFactory vf = conn.getValueFactory();
            return conn.hasStatement(
                    vf.createIRI(subjectIri),
                    vf.createIRI(NOTE),
                    vf.createLiteral(value),
                    false,
                    vf.createIRI(graphIri));
        }
    }

    private boolean hasStatement(String subjectIri, String graphIri, String predicateIri, String objectIri) {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            ValueFactory vf = conn.getValueFactory();
            return conn.hasStatement(
                    vf.createIRI(subjectIri),
                    vf.createIRI(predicateIri),
                    vf.createIRI(objectIri),
                    false,
                    vf.createIRI(graphIri));
        }
    }

    private Model noteModel(int requestIndex) {
        try {
            String turtle = "<" + CELL + "> <" + NOTE + "> \"request-" + requestIndex + "\" .";
            return Rio.parse(
                    new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
                    CELL,
                    RDFFormat.TURTLE,
                    repository.getValueFactory().createIRI(CELL));
        } catch (Exception e) {
            throw new AssertionError("Failed to parse test RDF", e);
        }
    }

    private static final class BlockingRuleService extends MazeRuleService {
        private final CountDownLatch ruleEntered = new CountDownLatch(1);
        private final CountDownLatch releaseRule = new CountDownLatch(1);

        private BlockingRuleService(SailRepository repository) {
            super(repository, "MissingMaze", List.of(), List.of());
        }

        @Override
        public void executeRules(SailRepositoryConnection connection, TransactionTraceContext traceContext) {
            ruleEntered.countDown();
            try {
                releaseRule.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean awaitRuleEntered() throws InterruptedException {
            return ruleEntered.await(5, TimeUnit.SECONDS);
        }

        private void releaseRule() {
            releaseRule.countDown();
        }
    }
}
