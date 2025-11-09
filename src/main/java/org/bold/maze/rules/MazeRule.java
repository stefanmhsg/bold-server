package org.bold.maze.rules;

/**
 * Represents a single maze game rule loaded from a SPARQL CONSTRUCT query.
 * Rules are evaluated after state changes (e.g., POST requests) to trigger
 * dynamic maze behavior like unlocking doors, activating switches, etc.
 */
public class MazeRule {
    
    private final String name;
    private final String sparqlConstruct;
    private final String description;
    
    /**
     * Create a new maze rule.
     * 
     * @param name Unique identifier for the rule (typically the filename)
     * @param sparqlConstruct The SPARQL CONSTRUCT query that defines the rule
     * @param description Optional human-readable description of what the rule does
     */
    public MazeRule(String name, String sparqlConstruct, String description) {
        this.name = name;
        this.sparqlConstruct = sparqlConstruct;
        this.description = description;
    }
    
    /**
     * Create a rule without a description.
     */
    public MazeRule(String name, String sparqlConstruct) {
        this(name, sparqlConstruct, null);
    }
    
    public String getName() {
        return name;
    }
    
    public String getSparqlConstruct() {
        return sparqlConstruct;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return "MazeRule{name='" + name + "'" + 
               (description != null ? ", description='" + description + "'" : "") + "}";
    }
}
