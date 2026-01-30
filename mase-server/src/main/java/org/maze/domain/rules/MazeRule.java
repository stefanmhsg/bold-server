package org.maze.domain.rules;

/**
 * Represents a single maze game rule loaded from a SPARQL query.
 * Rules are evaluated after state changes (e.g., POST requests) to trigger
 * dynamic maze behavior like unlocking doors, activating switches, etc.
 * 
 * Supports two rule types:
 * - CONSTRUCT: Uses SPARQL CONSTRUCT with custom state overwrite semantics
 * - UPDATE: Uses SPARQL UPDATE (DELETE/INSERT) with native RDF4J transaction handling
 */
public class MazeRule {
    
    /**
     * Type of SPARQL query used in the rule.
     */
    public enum RuleType {
        /** SPARQL CONSTRUCT query with custom state overwrite logic */
        CONSTRUCT,
        /** SPARQL UPDATE (DELETE/INSERT) query using native RDF4J */
        UPDATE
    }
    
    private final String name;
    private final String sparqlQuery;
    private final String description;
    private final RuleType ruleType;
    
    /**
     * Create a new maze rule.
     * 
     * @param name Unique identifier for the rule (typically the filename)
     * @param sparqlQuery The SPARQL query that defines the rule (CONSTRUCT or UPDATE)
     * @param description Optional human-readable description of what the rule does
     * @param ruleType The type of rule (CONSTRUCT or UPDATE)
     */
    public MazeRule(String name, String sparqlQuery, String description, RuleType ruleType) {
        this.name = name;
        this.sparqlQuery = sparqlQuery;
        this.description = description;
        this.ruleType = ruleType;
    }
    
    /**
     * Create a rule without a description (defaults to CONSTRUCT type for backward compatibility).
     */
    public MazeRule(String name, String sparqlQuery) {
        this(name, sparqlQuery, null, RuleType.CONSTRUCT);
    }
    
    /**
     * Create a rule without a description.
     * 
     * @param name Unique identifier for the rule
     * @param sparqlQuery The SPARQL query
     * @param ruleType The type of rule (CONSTRUCT or UPDATE)
     */
    public MazeRule(String name, String sparqlQuery, RuleType ruleType) {
        this(name, sparqlQuery, null, ruleType);
    }
    
    public String getName() {
        return name;
    }
    
    public String getSparqlQuery() {
        return sparqlQuery;
    }
    
    public String getDescription() {
        return description;
    }
    
    public RuleType getRuleType() {
        return ruleType;
    }
    
    /**
     * Check if this rule is a SPARQL UPDATE rule.
     * 
     * @return true if this is an UPDATE rule, false if CONSTRUCT
     */
    public boolean isUpdateRule() {
        return ruleType == RuleType.UPDATE;
    }
    
    @Override
    public String toString() {
        return "MazeRule{name='" + name + "'" + 
               ", type=" + ruleType +
               (description != null ? ", description='" + description + "'" : "") + "}";
    }
}
