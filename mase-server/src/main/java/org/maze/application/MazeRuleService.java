package org.maze.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.application.tx.TransactionTraceContext;
import org.maze.domain.rules.MazeRule;
import org.maze.infrastructure.storage.MazeRuleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MazeRuleService {
    
    private static final Logger log = LoggerFactory.getLogger(MazeRuleService.class);
    
    private final SparqlService sparqlService;
    private final List<MazeRule> rules;

    public MazeRuleService(SailRepository repository, List<Path> ruleFiles, Path ruleNameRoot, List<String> orderPatterns) {
        MazeRuleLoader ruleLoader = new MazeRuleLoader();
        List<MazeRule> rules = ruleLoader.loadRulesFromPaths(ruleFiles, ruleNameRoot, orderPatterns);

        this.sparqlService = new SparqlService(repository);
        this.rules = rules;

        log.info("MazeRuleService initialized from scenario package with {} rules", rules.size());
    }
    
    /**
     * Execute all rules within an external transaction.
     * This should be called after state-changing operations (e.g., POST requests).
     * 
     * @param connection the active repository connection with an open transaction
     */
    public void executeRules(SailRepositoryConnection connection) {
        executeRules(connection, null);
    }

    public void executeRules(SailRepositoryConnection connection, TransactionTraceContext traceContext) {
        log.debug("Executing {} maze rules", rules.size());
                
        for (MazeRule rule : rules) {
            if (traceContext != null) {
                traceContext.beginRule(rule.getName(), connection);
            }

            try {
                sparqlService.executeQuery(rule.getSparqlQuery(), "text/plain", rule.getName(), connection);
            } catch (Exception e) {
                if (traceContext != null) {
                    traceContext.markCurrentRuleError(e.getMessage());
                }
                // Log and continue on failure (don't rollback operations)
                log.error("Error executing rule '{}' ({}): {}", 
                        rule.getName(), rule.getRuleType(), e.getMessage(), e);
            } finally {
                if (traceContext != null) {
                    traceContext.endRule(connection);
                }
            }
        }
    }


    public void addRule(MazeRule rule) {
        rules.add(rule);
        log.info("Added rule: {}", rule.getName());
    }
    

    public List<MazeRule> getRules() {
        return new ArrayList<>(rules);
    }

}
