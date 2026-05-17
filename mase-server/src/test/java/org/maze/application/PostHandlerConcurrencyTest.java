package org.maze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.maze.application.tx.TransactionTraceContext;
import org.maze.domain.model.PostResult;
import org.maze.infrastructure.rdf.RepositoryFactory;

class PostHandlerConcurrencyTest {

    private static final String CELL = "http://127.0.1.1:8080/cells/concurrency";
    private static final String NOTE = "http://example.org/test#note";

    private SailRepository repository;
    private BlockingRuleService ruleService;
    private PostHandler postHandler;

    @BeforeEach
    void setUp() {
        repository = new RepositoryFactory().createRepository(null);
        createCellGraph();

        ruleService = new BlockingRuleService(repository);
        AccessValidator accessValidator = new AccessValidator(repository, new SparqlService(repository));
        postHandler = new PostHandler(repository, ruleService, accessValidator);
    }

    @Test
    void concurrentPostsToSameGraphExecuteOneAtATime() throws Exception {
        System.out.println();
        System.out.println("[TEST] concurrentPostsToSameGraphExecuteOneAtATime()");

        int requestCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PostResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                int requestIndex = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return postHandler.performPost(null, CELL, noteModel(requestIndex), null);
                }));
            }

            start.countDown();

            for (Future<PostResult> future : futures) {
                PostResult result = future.get(5, TimeUnit.SECONDS);
                assertTrue(result.isSuccess(), result.errorMessage());
            }

            System.out.printf(
                    "[RESULT] same graph max concurrent rule executions -> %d%n",
                    ruleService.maxConcurrentExecutions());

            assertEquals(1, ruleService.maxConcurrentExecutions());
        } finally {
            executor.shutdownNow();
        }
    }

    private void createCellGraph() {
        try (SailRepositoryConnection conn = repository.getConnection()) {
            ValueFactory vf = conn.getValueFactory();
            IRI cell = vf.createIRI(CELL);

            conn.begin();
            conn.add(cell, RDF.TYPE, vf.createIRI("http://example.org/test#Cell"), cell);
            conn.commit();
        }
    }

    private Model noteModel(int requestIndex) {
        try {
            ValueFactory vf = repository.getValueFactory();
            IRI graph = vf.createIRI(CELL);
            String turtle = "<" + CELL + "> <" + NOTE + "> \"request-" + requestIndex + "\" .";
            return Rio.parse(
                    new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
                    CELL,
                    RDFFormat.TURTLE,
                    graph);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse test RDF", e);
        }
    }

    private static final class BlockingRuleService extends MazeRuleService {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();

        private BlockingRuleService(SailRepository repository) {
            super(repository, List.of(), java.nio.file.Path.of("."), List.of());
        }

        @Override
        public void executeRules(SailRepositoryConnection connection, TransactionTraceContext traceContext) {
            int current = inFlight.incrementAndGet();
            maxConcurrent.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
        }

        private int maxConcurrentExecutions() {
            return maxConcurrent.get();
        }
    }
}
