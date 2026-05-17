package org.maze.application;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.websocket.MazeBroadcaster;
import org.maze.application.AccessValidator.PostAccessDecision;
import org.maze.application.AccessValidator.PostRequestType;
import org.maze.application.tx.TransactionTraceContext;
import org.maze.application.tx.TransactionTraceMode;
import org.maze.domain.model.AccessResult;
import org.maze.domain.model.PostResult;
import org.maze.domain.vocab.MazeVocab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class PostHandler {

    private static final Logger log = LoggerFactory.getLogger(PostHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final RequestLockRegistry REQUEST_LOCKS = new RequestLockRegistry();

    private final SailRepository repository;
    private final MazeRuleService ruleService;
    private final AccessValidator accessValidator;
    private final TransactionTraceMode transactionTraceMode;
    private final MazeMutationCoordinator mutationCoordinator;

    public PostHandler(SailRepository repository,
                       MazeRuleService ruleService,
                       AccessValidator accessValidator) {
        this(repository, ruleService, accessValidator, TransactionTraceMode.OFF, new MazeMutationCoordinator());
    }

    public PostHandler(SailRepository repository,
                       MazeRuleService ruleService,
                       AccessValidator accessValidator,
                       TransactionTraceMode transactionTraceMode) {
        this(repository, ruleService, accessValidator, transactionTraceMode, new MazeMutationCoordinator());
    }

    public PostHandler(SailRepository repository,
                       MazeRuleService ruleService,
                       AccessValidator accessValidator,
                       TransactionTraceMode transactionTraceMode,
                       MazeMutationCoordinator mutationCoordinator) {
        this.repository = repository;
        this.ruleService = ruleService;
        this.accessValidator = accessValidator;
        this.transactionTraceMode = transactionTraceMode;
        this.mutationCoordinator = mutationCoordinator;
    }

    public PostResult performPost(String agentName, String graphIRI, Model rdfModel) {
        return performPost(agentName, graphIRI, rdfModel, null);
    }

    public PostResult performPost(String agentName, String graphIRI, Model rdfModel, String requestBody) {
        return mutationCoordinator.withSharedMutation(() -> performPostUnlocked(agentName, graphIRI, rdfModel, requestBody));
    }

    private PostResult performPostUnlocked(String agentName, String graphIRI, Model rdfModel, String requestBody) {
        TransactionTraceContext trace = TransactionTraceContext.forPost(
                transactionTraceMode,
                agentName,
                graphIRI,
                requestBody);

        try (RequestLocks ignored = acquireRequestLocks(agentName, graphIRI, rdfModel);
             SailRepositoryConnection conn = repository.getConnection()) {

            conn.begin();

            ValueFactory vf = conn.getValueFactory();
            IRI graphName = vf.createIRI(graphIRI);

            // Check graph exists
            boolean exists = conn.hasStatement(null, null, null, false, graphName);
            if (!exists) {
                conn.rollback();
                String msg = "Graph not found: " + graphIRI;
                log.info(msg);
                markRolledBack(trace, msg);
                broadcastTransaction(trace);
                return PostResult.notFound(msg);
            }

            // Core MASE semantics: movement may target an adjacent cell; all other
            // cell actions are local interactions with the agent's current cell.
            PostAccessDecision accessDecision = accessValidator.validatePost(agentName, graphIRI, rdfModel, conn);
            AccessResult accessResult = accessDecision.access();
            if (!accessResult.isAllowed()) {
                conn.rollback();
                markRolledBack(trace, accessResult.message());
                broadcastTransaction(trace);
                log.warn("POST denied for agent {} on {}: {}", agentName, graphIRI, accessResult.message());
                return accessFailure(accessResult.message());
            }

            int triplesAdded = rdfModel.size();

            // Add incoming triples
            captureMergeBefore(trace, conn, graphIRI);
            conn.add(rdfModel);
            captureMergeAfter(trace, conn, graphIRI);
            log.debug("Added {} triples to graph {}", triplesAdded, graphIRI);

            // Execute rules inside the same transaction
            ruleService.executeRules(conn, trace);

            if (!validateCorePostconditions(conn, accessDecision)) {
                conn.rollback();
                String msg = "Movement request did not materialize. The request may be stale or no movement rule matched.";
                markRolledBack(trace, msg);
                broadcastTransaction(trace);
                log.warn("POST rolled back for agent {} on {}: {}", agentName, graphIRI, msg);
                return PostResult.conflict(msg);
            }

            conn.commit();
            markCommitted(trace);
            broadcastTransaction(trace);
            log.info("POST committed: {} triples merged into {}", triplesAdded, graphIRI);

            String message = checkSuccessCondition(agentName);
            return PostResult.success(graphIRI, triplesAdded, message);

        } catch (Exception e) {
            markFailed(trace, e.getMessage());
            broadcastTransaction(trace);
            log.error("Error during POST to {}", graphIRI, e);
            return PostResult.failed(e.getMessage());
        }
    }

    private RequestLocks acquireRequestLocks(String agentName, String graphIRI, Model rdfModel) {
        List<String> keys = requestLockKeys(agentName, graphIRI, rdfModel);
        return REQUEST_LOCKS.acquireAll(keys);
    }

    private List<String> requestLockKeys(String agentName, String graphIRI, Model rdfModel) {
        Set<String> keys = new HashSet<>();
        keys.add("graph:" + graphIRI);

        if (agentName != null && !agentName.trim().isEmpty()) {
            keys.add("agent:" + buildAgentUri(graphIRI, agentName));
        }

        ValueFactory vf = repository.getValueFactory();
        IRI entersFrom = vf.createIRI(MazeVocab.ENTERS_FROM);
        rdfModel.filter(null, entersFrom, null).forEach(statement ->
                keys.add("graph:" + statement.getObject().stringValue()));

        // Deterministic acquisition order prevents deadlocks between overlapping moves.
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        return sorted;
    }

    private String buildAgentUri(String resourceUri, String agentName) {
        String baseUri = extractBaseUri(resourceUri);
        String agentPrefix = baseUri + "/agents/";
        if (agentName.startsWith(agentPrefix)) {
            return agentName;
        }
        return agentPrefix + agentName;
    }

    private String extractBaseUri(String resourceUri) {
        int cellsIndex = resourceUri.lastIndexOf("/cells");
        if (cellsIndex == -1) {
            int lastSlash = resourceUri.lastIndexOf("/");
            return resourceUri.substring(0, lastSlash);
        }
        return resourceUri.substring(0, cellsIndex);
    }

    private static final class RequestLockRegistry {
        private final Map<String, LockEntry> entries = new HashMap<>();

        private RequestLocks acquireAll(List<String> keys) {
            List<KeyedLock> acquired = new ArrayList<>(keys.size());

            for (String key : keys) {
                LockEntry entry = retain(key);
                entry.lock.lock();
                acquired.add(new KeyedLock(key, entry));
            }

            return new RequestLocks(this, acquired);
        }

        private synchronized LockEntry retain(String key) {
            LockEntry entry = entries.computeIfAbsent(key, ignored -> new LockEntry());
            entry.references++;
            return entry;
        }

        private synchronized void release(String key, LockEntry entry) {
            entry.references--;
            if (entry.references == 0) {
                entries.remove(key, entry);
            }
        }
    }

    private static final class LockEntry {
        // Fair locks preserve request arrival order for one graph/agent key as closely
        // as the JVM scheduler allows, matching MASE's per-resource request queue model.
        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;
    }

    private record KeyedLock(String key, LockEntry entry) {
    }

    private record RequestLocks(RequestLockRegistry registry, List<KeyedLock> locks) implements AutoCloseable {
        @Override
        public void close() {
            for (int i = locks.size() - 1; i >= 0; i--) {
                KeyedLock keyedLock = locks.get(i);
                keyedLock.entry().lock.unlock();
                registry.release(keyedLock.key(), keyedLock.entry());
            }
        }
    }

    private void captureMergeBefore(TransactionTraceContext trace, SailRepositoryConnection conn, String graphIRI) {
        if (trace != null) {
            trace.captureMergeBefore(conn, graphIRI);
        }
    }

    private void captureMergeAfter(TransactionTraceContext trace, SailRepositoryConnection conn, String graphIRI) {
        if (trace != null) {
            trace.captureMergeAfter(conn, graphIRI);
        }
    }

    private void markCommitted(TransactionTraceContext trace) {
        if (trace != null) {
            trace.markCommitted();
        }
    }

    private void markRolledBack(TransactionTraceContext trace, String message) {
        if (trace != null) {
            trace.markRolledBack(message);
        }
    }

    private void markFailed(TransactionTraceContext trace, String message) {
        if (trace != null) {
            trace.markFailed(message);
        }
    }

    private PostResult accessFailure(String message) {
        if (message != null && message.startsWith("Invalid movement request:")) {
            return PostResult.invalid(message);
        }
        if (message != null && message.contains("You claim to enter from")) {
            return PostResult.conflict(message);
        }
        return PostResult.denied(message);
    }

    private boolean validateCorePostconditions(SailRepositoryConnection conn, PostAccessDecision decision) {
        if (decision.type() != PostRequestType.MOVEMENT || decision.agentUri() == null) {
            return true;
        }

        ValueFactory vf = conn.getValueFactory();
        IRI agent = vf.createIRI(decision.agentUri());
        IRI sourceGraph = vf.createIRI(decision.sourceCell());
        IRI targetGraph = vf.createIRI(decision.targetCell());
        IRI contains = vf.createIRI(MazeVocab.MAZE_NS + "contains");
        IRI entersFrom = vf.createIRI(MazeVocab.ENTERS_FROM);

        boolean agentInTarget = conn.hasStatement(targetGraph, contains, agent, false, targetGraph);
        boolean requestConsumed = !conn.hasStatement(agent, entersFrom, sourceGraph, false, targetGraph);
        boolean sourceStillContainsAgent = conn.hasStatement(sourceGraph, contains, agent, false, sourceGraph);

        // The movement rule is core behavior: after a successful movement request,
        // the agent's embodiment must be represented by containment in the target cell.
        return agentInTarget && requestConsumed && !sourceStillContainsAgent;
    }

    private void broadcastTransaction(TransactionTraceContext trace) {
        if (trace == null) {
            return;
        }

        try {
            MazeBroadcaster.broadcast(mapper.writeValueAsString(trace.getEvent()));
        } catch (Exception e) {
            log.error("Failed to broadcast transaction event", e);
        }
    }

    public String checkSuccessCondition(String agentName) {
        log.debug("Success condition check not yet implemented for agent: {}", agentName);
        return "Success condition check not implemented yet.";
    }
}
