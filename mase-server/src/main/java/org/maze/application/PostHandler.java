package org.maze.application;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.websocket.MazeBroadcaster;
import org.maze.application.tx.TransactionTraceContext;
import org.maze.domain.model.PostResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class PostHandler {

    private static final Logger log = LoggerFactory.getLogger(PostHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final SailRepository repository;
    private final MazeRuleService ruleService;

    public PostHandler(SailRepository repository,
                       MazeRuleService ruleService) {
        this.repository = repository;
        this.ruleService = ruleService;
    }

    public PostResult performPost(String agentName, String graphIRI, Model rdfModel) {
        return performPost(agentName, graphIRI, rdfModel, null);
    }

    public PostResult performPost(String agentName, String graphIRI, Model rdfModel, String requestBody) {
        TransactionTraceContext trace = TransactionTraceContext.forPost(agentName, graphIRI, requestBody);

        try (SailRepositoryConnection conn = repository.getConnection()) {

            conn.begin();

            ValueFactory vf = conn.getValueFactory();
            IRI graphName = vf.createIRI(graphIRI);

            // Check graph exists
            boolean exists = conn.hasStatement(null, null, null, false, graphName);
            if (!exists) {
                conn.rollback();
                String msg = "Graph not found: " + graphIRI;
                log.info(msg);
                trace.markRolledBack(msg);
                broadcastTransaction(trace);
                return PostResult.notFound(msg);
            }

            int triplesAdded = rdfModel.size();

            // Add incoming triples
            trace.captureMergeBefore(conn, graphIRI);
            conn.add(rdfModel);
            trace.captureMergeAfter(conn, graphIRI);
            log.debug("Added {} triples to graph {}", triplesAdded, graphIRI);

            // Execute rules inside the same transaction
            ruleService.executeRules(conn, trace);

            conn.commit();
            trace.markCommitted();
            broadcastTransaction(trace);
            log.info("POST committed: {} triples merged into {}", triplesAdded, graphIRI);

            String message = checkSuccessCondition(agentName);
            return PostResult.success(graphIRI, triplesAdded, message);

        } catch (Exception e) {
            trace.markFailed(e.getMessage());
            broadcastTransaction(trace);
            log.error("Error during POST to {}", graphIRI, e);
            return PostResult.failed(e.getMessage());
        }
    }

    private void broadcastTransaction(TransactionTraceContext trace) {
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
