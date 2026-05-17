package org.maze.application;

import java.net.URI;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.dto.MazeAdminSnapshotDto;
import org.maze.api.websocket.MazeBroadcaster;
import org.maze.application.tx.TransactionTraceContext;
import org.maze.application.tx.TransactionTraceMode;
import org.maze.infrastructure.rdf.DataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Restores the RDF repository to the configured startup dataset.
 */
public class MazeResetService {

    private static final Logger log = LoggerFactory.getLogger(MazeResetService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ADMIN_RESET_EVENT = "{\"type\":\"ADMIN_RESET\"}";

    private final SailRepository repository;
    private final String datasetPattern;
    private final URI rdfBaseUri;
    private final MazeRuleService ruleService;
    private final TransactionTraceMode transactionTraceMode;
    private final MazeMutationCoordinator mutationCoordinator;
    private final DataLoader dataLoader;
    private final AccessValidator accessValidator;

    public MazeResetService(SailRepository repository,
                            String datasetPattern,
                            URI rdfBaseUri,
                            MazeRuleService ruleService,
                            TransactionTraceMode transactionTraceMode,
                            MazeMutationCoordinator mutationCoordinator,
                            AccessValidator accessValidator) {
        this.repository = repository;
        this.datasetPattern = datasetPattern;
        this.rdfBaseUri = rdfBaseUri;
        this.ruleService = ruleService;
        this.transactionTraceMode = transactionTraceMode;
        this.mutationCoordinator = mutationCoordinator;
        this.accessValidator = accessValidator;
        this.dataLoader = new DataLoader();
    }

    public MazeAdminSnapshotDto resetToInitialDataset() throws Exception {
        return mutationCoordinator.withExclusiveMutation(this::resetInExclusiveSection);
    }

    private MazeAdminSnapshotDto resetInExclusiveSection() throws Exception {
        TransactionTraceContext resetTrace = TransactionTraceContext.forReset(transactionTraceMode);

        try (SailRepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            try {
                log.info("Resetting RDF repository from dataset pattern: {}", datasetPattern);
                conn.clear();
                dataLoader.loadData(conn, datasetPattern, rdfBaseUri);
                ruleService.executeRules(conn, resetTrace);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                markFailed(resetTrace, e);
                broadcastTransaction(resetTrace);
                throw e;
            }
        }

        if (accessValidator != null) {
            accessValidator.clearCaches();
        }

        MazeBroadcaster.clearRecentMessages();
        markCommitted(resetTrace);
        broadcastTransaction(resetTrace);
        MazeBroadcaster.broadcast(ADMIN_RESET_EVENT);

        MazeAdminSnapshotDto snapshot = buildSnapshot();
        log.info("Admin reset completed: dataset={}, scenario={}, {}x{}, ui elements={}",
                datasetPattern,
                snapshot.scenario,
                snapshot.layout.width,
                snapshot.layout.height,
                snapshot.ui.size());
        return snapshot;
    }

    private MazeAdminSnapshotDto buildSnapshot() {
        MazeLayoutService layoutService = new MazeLayoutService(repository);

        MazeAdminSnapshotDto snapshot = new MazeAdminSnapshotDto();
        snapshot.layout = layoutService.getMazeLayout();
        snapshot.ui = layoutService.getUiSnapshot();
        snapshot.scenario = layoutService.getMazeScenarioName();
        return snapshot;
    }

    private void markCommitted(TransactionTraceContext trace) {
        if (trace != null) {
            trace.markCommitted();
        }
    }

    private void markFailed(TransactionTraceContext trace, Exception e) {
        if (trace != null) {
            trace.markFailed(e.getMessage());
        }
    }

    private void broadcastTransaction(TransactionTraceContext trace) {
        if (trace == null) {
            return;
        }

        try {
            MazeBroadcaster.broadcast(mapper.writeValueAsString(trace.getEvent()));
        } catch (Exception e) {
            log.error("Failed to broadcast reset transaction event", e);
        }
    }
}
