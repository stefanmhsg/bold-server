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
import org.maze.domain.model.RuleExecutionResult;
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
    
    private final SailRepository repository;
    private final List<MazeRule> rules;
    private final SparqlService sparqlService;
    
    /**
     * Create a new rule engine.
     * 
     * @param repository The RDF repository to execute rules against
     * @param rules List of rules to evaluate
     * @param sparqlService Service for executing SPARQL UPDATE queries
     */
    public MazeRuleEngine(SailRepository repository, List<MazeRule> rules, SparqlService sparqlService) {
        this.repository = repository;
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
     * 
     * Executes in a SEPARATE transaction from the operation that triggered it.
     * On failure, logs error and continues to next rule.
     * 
     * @return RuleExecutionResult containing statistics about what was applied
     */
    public RuleExecutionResult executeRules() {
        log.debug("Executing {} maze rules", rules.size());
        
        int totalTriplesAdded = 0;
        int rulesTriggered = 0;
        List<String> triggeredRuleNames = new ArrayList<>();
        
        for (MazeRule rule : rules) {
            try {
                // Each rule gets its own transaction
                int triplesAdded = rule.isUpdateRule() 
                    ? executeUpdateRule(rule) 
                    : executeConstructRule(rule);
                
                if (triplesAdded > 0) {
                    totalTriplesAdded += triplesAdded;
                    rulesTriggered++;
                    triggeredRuleNames.add(rule.getName());
                    log.info("Rule '{}' ({}) triggered: modified {} triples", 
                            rule.getName(), rule.getRuleType(), triplesAdded);
                }
            } catch (Exception e) {
                // Log and continue on failure (don't rollback operations)
                log.error("Error executing rule '{}' ({}): {}", 
                        rule.getName(), rule.getRuleType(), e.getMessage(), e);
            }
        }
        
        if (totalTriplesAdded > 0) {
            log.info("Rules execution complete: {} rules triggered, {} triples modified", 
                    rulesTriggered, totalTriplesAdded);
        } else {
            log.debug("No rules triggered");
        }
        
        return new RuleExecutionResult(rulesTriggered, totalTriplesAdded, triggeredRuleNames);
    }
    
    /**
     * Execute a SPARQL UPDATE rule using the SparqlService.
     * 
     * @param rule The UPDATE rule to execute
     * @return Number of triples modified (returns 1 if successful, 0 otherwise)
     */
    private int executeUpdateRule(MazeRule rule) {
        log.debug("Executing UPDATE rule: {}", rule.getName());
        
        SparqlResult result = sparqlService.executeQuery(rule.getSparqlConstruct(), "text/plain");
        
        if (result.success()) {
            log.debug("UPDATE rule '{}' executed successfully", rule.getName());
            return 1; // UPDATE queries don't return triple count, just success
        } else {
            log.error("UPDATE rule '{}' failed: {}", rule.getName(), result.errorMessage());
            return 0;
        }
    }
    
    /**
     * Execute a SPARQL CONSTRUCT rule with custom state overwrite logic.
     * 
     * @param rule The CONSTRUCT rule to execute
     * @return Number of triples added by this rule
     */
    private int executeConstructRule(MazeRule rule) {
        log.debug("Executing CONSTRUCT rule: {}", rule.getName());
        
        try (SailRepositoryConnection connection = repository.getConnection()) {
            connection.begin();
            
            try {
                int triplesAdded = executeConstructRuleInternal(connection, rule);
                
                if (triplesAdded > 0) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                
                return triplesAdded;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }
    
    /**
     * Internal implementation of CONSTRUCT rule execution.
     * 
     * @param connection Open repository connection
     * @param rule The rule to execute
     * @return Number of triples added by this rule
     */
    private int executeConstructRuleInternal(SailRepositoryConnection connection, MazeRule rule) {
        log.debug("Evaluating rule: {}", rule.getName());
        
        // Execute the CONSTRUCT query
        GraphQuery query = connection.prepareGraphQuery(rule.getSparqlConstruct());
        Model resultModel = QueryResults.asModel(query.evaluate());
        
        if (resultModel.isEmpty()) {
            log.debug("Rule '{}' conditions not met (no triples constructed)", rule.getName());
            return 0;
        }
        
        // Add constructed triples to appropriate graphs
        // All maze rules must ALWAYS write to the cell's graph
        // For state predicates, apply cleanup to remove conflicting values
        int triplesAdded = 0;
        int triplesRemoved = 0;

        IRI lastIriSubject = null;
        
        for (Statement stmt : resultModel) {

            IRI targetGraph = (IRI) stmt.getContext();   // may be null if rule file did not set GRAPH

            if (!(targetGraph instanceof IRI)) {
                // Infer the target graph from the subject URI
                // If subject is http://127.0.1.1:8080/cells/5#redAction
                // Then target graph is http://127.0.1.1:8080/cells/5
                targetGraph = inferTargetGraph(stmt.getSubject(), connection);
            }

            // If still not an IRI, fallback to last known IRI subject graph
            if (!(targetGraph instanceof IRI)) {
                 log.warn("Could not infer target graph for subject: {}", stmt.getSubject());

                // For blank nodes, assuming previously used graph
                if (lastIriSubject != null) {
                    targetGraph = lastIriSubject;
                    log.debug("Using last known graph: {}", targetGraph);
                } else {
                    log.warn("No previous graph available, skipping triple: {}", stmt);
                    continue;
                }
            }

            if (targetGraph instanceof IRI) {
                lastIriSubject = targetGraph;
            }
            

            
            // Only apply cleanup for whitelisted state predicates
            if (MazeVocab.STATE_PREDICATES.contains(stmt.getPredicate().stringValue())) {
                
                // Find all old values for this subject/predicate in the same graph
                try (RepositoryResult<Statement> existing = connection.getStatements(
                        stmt.getSubject(), stmt.getPredicate(), null, targetGraph)) {
                    
                    List<Statement> toRemove = new ArrayList<>();
                    while (existing.hasNext()) {
                        Statement oldStmt = existing.next();
                        // Remove only if value differs
                        if (!oldStmt.getObject().equals(stmt.getObject())) {
                            toRemove.add(oldStmt);
                        }
                    }
                    
                    if (!toRemove.isEmpty()) {
                        log.debug("Cleanup: removing {} outdated triple(s) for {} {}",
                                  toRemove.size(), stmt.getSubject(), stmt.getPredicate());
                        connection.remove(toRemove);
                        triplesRemoved += toRemove.size();
                    }
                }
            }
            
            // Add the new triple (idempotent even if it already exists)
            connection.add(
                stmt.getSubject(),
                stmt.getPredicate(),
                stmt.getObject(),
                targetGraph
            );
            triplesAdded++;
        }
        
        if (triplesRemoved > 0) {
            log.debug("Rule '{}' cleaned up {} outdated triples, added {} new triples", 
                     rule.getName(), triplesRemoved, triplesAdded);
        } else {
            log.debug("Rule '{}' added {} triples", rule.getName(), triplesAdded);
        }
        return triplesAdded;
    }
    
    /**
     * Infer the target graph from a subject URI.
     * Removes the fragment identifier to get the base cell URI.
     * 
     * For example:
     * - http://127.0.1.1:8080/cells/5#redAction -> http://127.0.1.1:8080/cells/5
     * - http://127.0.1.1:8080/cells/5 -> http://127.0.1.1:8080/cells/5
     * 
     * @param subject The subject resource
     * @param connection Repository connection for ValueFactory
     * @return The target graph IRI, or null if subject is a blank node
     */
    private IRI inferTargetGraph(Resource subject, SailRepositoryConnection connection) {
        if (!(subject instanceof IRI)) {
            // Blank nodes: skip or choose a policy
            log.debug("Subject is not an IRI (blank node?), skipping: {}", subject);
            return null;
        }
        
        IRI iri = (IRI) subject;
        String full = iri.stringValue();
        
        // Remove fragment if present
        int hashIndex = full.indexOf('#');
        String graphUri = (hashIndex >= 0) ? full.substring(0, hashIndex) : full;
        
        return connection.getValueFactory().createIRI(graphUri);
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
