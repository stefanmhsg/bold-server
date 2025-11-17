package org.maze.domain.model;

import java.util.List;

/**
 * Result of executing maze rules.
 * Contains statistics about which rules were triggered and how many triples were added.
 */
public record RuleExecutionResult(int rulesTriggered, int triplesAdded, 
                                  List<String> triggeredRuleNames) {
    
    public RuleExecutionResult {
        triggeredRuleNames = List.copyOf(triggeredRuleNames);
    }
    
    public boolean hasChanges() {
        return triplesAdded > 0;
    }
}
