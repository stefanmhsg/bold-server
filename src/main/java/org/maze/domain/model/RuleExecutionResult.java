package org.maze.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of executing maze rules.
 * Contains statistics about which rules were triggered and how many triples were added.
 */
public class RuleExecutionResult {
    private final int rulesTriggered;
    private final int triplesAdded;
    private final List<String> triggeredRuleNames;
    
    public RuleExecutionResult(int rulesTriggered, int triplesAdded, 
                              List<String> triggeredRuleNames) {
        this.rulesTriggered = rulesTriggered;
        this.triplesAdded = triplesAdded;
        this.triggeredRuleNames = new ArrayList<>(triggeredRuleNames);
    }
    
    public int getRulesTriggered() {
        return rulesTriggered;
    }
    
    public int getTriplesAdded() {
        return triplesAdded;
    }
    
    public List<String> getTriggeredRuleNames() {
        return new ArrayList<>(triggeredRuleNames);
    }
    
    public boolean hasChanges() {
        return triplesAdded > 0;
    }
    
    @Override
    public String toString() {
        return String.format("RuleExecutionResult{rulesTriggered=%d, triplesAdded=%d, rules=%s}",
                           rulesTriggered, triplesAdded, triggeredRuleNames);
    }
}
