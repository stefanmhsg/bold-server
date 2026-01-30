package org.maze.application;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.domain.rules.MazeRule;
import org.maze.infrastructure.storage.MazeRuleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MazeRuleService {
    
    private static final Logger log = LoggerFactory.getLogger(MazeRuleService.class);
    
    private final SparqlService sparqlService;
    private final List<MazeRule> rules;
    
    public MazeRuleService(SailRepository repository, String mazeName, List<String> additionalRulesets, List<String> orderPatterns) {
        // Initialize rule Service with loaded rules
        MazeRuleLoader ruleLoader = new MazeRuleLoader();
        List<String> ruleFiles = ruleLoader.discoverRuleFiles(mazeName);
        
        // Load all additional global rulesets if specified
        if (additionalRulesets != null && !additionalRulesets.isEmpty()) {
            for (String ruleset : additionalRulesets) {
                List<String> additionalRuleFiles = ruleLoader.discoverRuleFiles(ruleset);
                ruleFiles.addAll(additionalRuleFiles);
                log.info("Added {} rules from additional ruleset: {}", additionalRuleFiles.size(), ruleset);
            }
        }
        
        List<MazeRule> rules = ruleLoader.loadRules(ruleFiles, orderPatterns);
        
        // Initialize SPARQL service first (needed by rule service)
        this.sparqlService = new SparqlService(repository);
        this.rules = rules;
        
        String rulesetsInfo = additionalRulesets != null && !additionalRulesets.isEmpty() 
                ? " + " + String.join(", ", additionalRulesets) 
                : "";
        log.info("MazeRuleService initialized{}{} with {} rules", 
                mazeName != null ? " for " + mazeName : "",
                rulesetsInfo,
                rules.size());
    }
    
    /**
     * Constructor without rule ordering (backward compatibility).
     * Uses alphabetical order for rules.
     */
    public MazeRuleService(SailRepository repository, String mazeName, List<String> additionalRulesets) {
        this(repository, mazeName, additionalRulesets, null);
    }
    
    /**
     * Execute all rules within an external transaction.
     * This should be called after state-changing operations (e.g., POST requests).
     * 
     * @param connection the active repository connection with an open transaction
     */
    public void executeRules(SailRepositoryConnection connection) {
        log.debug("Executing {} maze rules", rules.size());
                
        for (MazeRule rule : rules) {
            try {
                sparqlService.executeQuery(rule.getSparqlQuery(), "text/plain", rule.getName(), connection);
            } catch (Exception e) {
                // Log and continue on failure (don't rollback operations)
                log.error("Error executing rule '{}' ({}): {}", 
                        rule.getName(), rule.getRuleType(), e.getMessage(), e);
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
