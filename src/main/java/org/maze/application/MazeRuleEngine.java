package org.maze.application;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.application.services.SparqlService;
import org.maze.domain.model.SparqlResult;
import org.maze.domain.rules.MazeRule;
import org.maze.domain.vocab.MazeVocab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes maze game rules against the RDF repository.
 * Rules are evaluated after state changes to trigger dynamic maze behaviors.
 * 
 * Supports two rule types:
 * - CONSTRUCT: Uses SPARQL CONSTRUCT with custom state overwrite semantics
 * - UPDATE: Uses SPARQL UPDATE (DELETE/INSERT) with native RDF4J transaction handling
 */
public class MazeRuleEngine {
    
    private static final Logger log = LoggerFactory.getLogger(MazeRuleEngine.class);
    
    private final List<MazeRule> rules;
    private final SparqlService sparqlService;
    
    /**
     * Create a new rule engine.
     * 
     * @param repository The RDF repository to execute rules against
     * @param rules List of rules to evaluate
     * @param sparqlService Service for executing SPARQL UPDATE queries
     */
    public MazeRuleEngine(List<MazeRule> rules, SparqlService sparqlService) {
        this.rules = new ArrayList<>(rules);
        this.sparqlService = sparqlService;
        
        long constructCount = rules.stream().filter(r -> r.getRuleType() == MazeRule.RuleType.CONSTRUCT).count();
        long updateCount = rules.stream().filter(r -> r.getRuleType() == MazeRule.RuleType.UPDATE).count();
        
        log.info("Initialized MazeRuleEngine with {} rules ({} CONSTRUCT, {} UPDATE)", 
                rules.size(), constructCount, updateCount);
    }
    
    /**
     * Execute all rules and apply any resulting triples to the repository.
     * This should be called after state-changing operations (e.g., POST requests).
     */
    public void executeRules() {
        log.debug("Executing {} maze rules", rules.size());
                
        for (MazeRule rule : rules) {
            try {
                    sparqlService.executeQuery(rule.getSparqlQuery(), "text/plain", rule.getName());
            } catch (Exception e) {
                // Log and continue on failure (don't rollback operations)
                log.error("Error executing rule '{}' ({}): {}", 
                        rule.getName(), rule.getRuleType(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Add a new rule to the engine.
     * 
     * @param rule Rule to add
     */
    public void addRule(MazeRule rule) {
        rules.add(rule);
        log.info("Added rule: {}", rule.getName());
    }
    
    /**
     * Get all rules in this engine.
     * 
     * @return List of rules
     */
    public List<MazeRule> getRules() {
        return new ArrayList<>(rules);
    }
    
}
