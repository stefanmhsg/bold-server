package org.maze.application;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.domain.model.PostResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostHandler {

    private static final Logger log = LoggerFactory.getLogger(PostHandler.class);

    private final SailRepository repository;
    private final MazeRuleService ruleService;

    public PostHandler(SailRepository repository,
                       MazeRuleService ruleService) {
        this.repository = repository;
        this.ruleService = ruleService;
    }

    public PostResult performPost(String agentName, String graphIRI, Model rdfModel) {

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
                return PostResult.notFound(msg);
            }

            int triplesAdded = rdfModel.size();

            // Add incoming triples
            conn.add(rdfModel);
            log.debug("Added {} triples to graph {}", triplesAdded, graphIRI);

            // Execute rules inside the same transaction
            ruleService.executeRules(conn);

            conn.commit();
            log.info("POST committed: {} triples merged into {}", triplesAdded, graphIRI);

            String message = checkSuccessCondition(agentName);
            return PostResult.success(graphIRI, triplesAdded, message);

        } catch (Exception e) {
            log.error("Error during POST to {}", graphIRI, e);
            return PostResult.failed(e.getMessage());
        }
    }

    public String checkSuccessCondition(String agentName) {
        log.debug("Success condition check not yet implemented for agent: {}", agentName);
        return "Success condition check not implemented yet.";
    }
}
