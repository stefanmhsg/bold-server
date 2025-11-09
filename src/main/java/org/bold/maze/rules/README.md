# Maze Rule System

This document describes the SPARQL-based rule system for dynamic maze behavior in the BOLD server.

## Overview

The maze rule system allows dynamic game mechanics to be triggered after state changes (POST requests). Instead of using N3 logic rules with LDFu, rules are now defined as **SPARQL CONSTRUCT queries** and executed server-side by the Java game engine.

## Architecture

```
POST Request → Graph Update → Rule Engine → Apply Changes
     ↓              ↓              ↓            ↓
  (Agent         (RDF           (Evaluate    (Add new
   posts key)     store)         rules)      connections)
```

### Components

1. **MazeRule**: Represents a single rule (SPARQL CONSTRUCT query)
2. **MazeRuleLoader**: Loads `.rq` files from `src/main/resources/rules/`
3. **MazeRuleEngine**: Executes rules against the RDF repository
4. **MazeGameEngine**: Orchestrates rule execution after POST requests

## Rule File Format

Rules are stored as SPARQL CONSTRUCT queries in `.rq` files under `src/main/resources/rules/`.

### Example: Unlock Door with Red Key

```sparql
# Rule to unlock doors when red key is used
PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#>
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>

CONSTRUCT {
    ?cell dyn:state dyn:unlocked.
    ?cell maze:east <http://127.0.0.1:8080/cells/10>.
    ?action dyn:hasStatus dyn:done.
}
WHERE {
    GRAPH ?g {
        ?cell a maze:Cell.
        ?cell a dyn:Lock.
        ?cell dyn:keyValue "redkey".
        ?cell dyn:needsAction ?action.
        ?action dyn:hasStatus dyn:open.
    }
}
```

### Rule Structure

- **Comment**: First line starting with `#` is used as description
- **CONSTRUCT**: Defines triples to add when rule is triggered
- **WHERE**: Defines conditions that must be met for rule to trigger

## How Rules Work

### 1. Loading Rules (Server Startup)

When `MazeGameEngine` is initialized:

```java
MazeRuleLoader loader = new MazeRuleLoader();
List<String> ruleFiles = loader.discoverRuleFiles();
List<MazeRule> rules = loader.loadRules(ruleFiles);
MazeRuleEngine engine = new MazeRuleEngine(repository, rules);
```

### 2. Triggering Rules (After POST)

When a POST request completes successfully:

```java
@POST
public Response postGraph(...) {
    // ... add triples to graph ...
    
    // Execute rules
    RuleExecutionResult result = gameEngine.executeRules();
    
    if (result.hasChanges()) {
        log.info("Rules triggered: {}", result.getTriggeredRuleNames());
    }
    
    return Response.noContent().build();
}
```

### 3. Evaluating Rules

For each rule, the engine:

1. Executes the CONSTRUCT query against the repository
2. If query returns triples (conditions met), rule is triggered
3. Resulting triples are added to appropriate graphs
4. Transaction is committed if any rules triggered

### 4. Graph Context

Constructed triples are added to the graph of the subject URI. For example:

```sparql
CONSTRUCT { <http://localhost:8080/cells/5> maze:east <http://localhost:8080/cells/6> }
```

This triple will be added to graph `<http://localhost:8080/cells/5>`.

## Example Use Cases

### Unlocking Doors

Agent finds a key and POSTs it to a cell:

```turtle
POST /cells/5
Content-Type: text/turtle

<http://localhost:8080/cells/5> 
    dyn:foundAt dyn:RedKey .
```

This triggers the `unlock-redkey` rule, which:
1. Checks if cell 5 has a lock requiring red key
2. If so, adds connection to locked cell
3. Marks action as done


### Timed Events

Rules could check timestamps to trigger time-based changes:

```sparql
CONSTRUCT {
    ?cell dyn:state dyn:locked .
}
WHERE {
    GRAPH ?g {
        ?cell dyn:unlockTime ?time .
        FILTER(?time < NOW())
    }
}
```

## Rule Discovery

Currently, rules are discovered using a predefined list in `MazeRuleLoader.discoverRuleFiles()`:

```java
String[] commonRules = {
    "unlock-redkey.rq",
    "unlock-bluekey.rq",
    "unlock-greenkey.rq",
    "switch-toggle.rq"
};
```

### Alternative: Configuration File

You could use a `rules.properties` file:

```properties
# rules.properties
maze.rules=unlock-redkey.rq,unlock-bluekey.rq,unlock-greenkey.rq
```

### Alternative: Classpath Scanning

Use reflection to auto-discover all `.rq` files in the rules directory.

## Creating New Rules

### Step 1: Write SPARQL Query

Create a `.rq` file in `src/main/resources/rules/`:

```sparql
# Rule to activate bridge
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>
PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#>

CONSTRUCT {
    ?from maze:north ?to .
}
WHERE {
    GRAPH ?g {
        ?bridge dyn:bridgeState dyn:lowered .
        ?bridge dyn:connects ?from .
        ?bridge dyn:connectsTo ?to .
    }
}
```

### Step 2: Add to Discovery List

Update `MazeRuleLoader.discoverRuleFiles()`:

```java
String[] commonRules = {
    "unlock-redkey.rq",
    "unlock-bluekey.rq",
    "unlock-greenkey.rq",
    "activate-bridge.rq"  // <-- Add new rule
};
```

### Step 3: Test

1. Start server
2. Check logs for rule loading: `Loaded maze rule: activate-bridge`
3. Trigger the rule condition via POST
4. Verify rule execution in logs

## Migration from N3 Rules

### Old: N3 with LDFu

```n3
{
  ?cell a maze:Cell , dyn:Lock ;
    dyn:keyValue "redkey" ;
    dyn:needsAction ?action .
  ?action dyn:hasStatus dyn:open .
} => {
  [] http:mthd httpm:POST ;
     http:requestURI ?cell ;
     http:body {
        ?cell maze:east <http://127.0.1.1:8080/cells/10> ;
              dyn:state dyn:unlocked .
     } .
} .
```

### New: SPARQL Rule

```sparql
CONSTRUCT {
    ?cell dyn:state dyn:unlocked .
    ?cell maze:east <http://127.0.0.1:8080/cells/10> .
}
WHERE {
    GRAPH ?g {
        ?cell a maze:Cell .
        ?cell a dyn:Lock .
        ?cell dyn:keyValue "redkey" .
        ?cell dyn:needsAction ?action .
        ?action dyn:hasStatus dyn:open .
    }
}
```

### Key Differences

1. **No HTTP Semantics**: SPARQL rules don't generate HTTP requests, they directly modify the graph
2. **Simpler Syntax**: Standard SPARQL instead of N3 logic
3. **Server-Side**: Rules execute in Java, not external reasoner
4. **Transactional**: All rule changes in single transaction
5. **Better Logging**: Detailed execution logs per rule

## Performance Considerations

### Rule Execution Cost

Rules are executed after **every POST request**. Consider:

1. **Query Complexity**: Complex WHERE clauses slow down execution
2. **Number of Rules**: Each rule is evaluated sequentially
3. **Graph Size**: Larger graphs take longer to query

### Optimization Strategies

1. **Specific Graph Queries**: Use `GRAPH ?g` to limit scope
2. **Early Filtering**: Put most restrictive conditions first
3. **Rule Ordering**: Execute common rules first (future enhancement)
4. **Caching**: Cache rule objects (already implemented)
5. **Conditional Execution**: Only run rules if relevant graphs changed (future)

### Monitoring

Check logs for rule execution times:

```
Rule 'unlock-redkey' triggered: added 3 triples
Rules execution complete: 2 rules triggered, 6 triples added
```

## Debugging Rules

### Enable Debug Logging

In `src/main/resources/simplelogger.properties`:

```properties
org.slf4j.simpleLogger.log.org.bold.maze.rules=debug
```

### Debug Output

```
Evaluating rule: unlock-redkey
Rule 'unlock-redkey' conditions not met (no triples constructed)
```

or

```
Evaluating rule: unlock-redkey
Rule 'unlock-redkey' added 3 triples
```

### Testing Rules in SPARQL Endpoint

You can test rule queries directly against the RDF store to verify conditions.

## Future Enhancements

1. **Rule Priorities**: Execute rules in order of priority
2. **Rule Chaining**: Trigger additional rules after changes
3. **Conditional Execution**: Only run relevant rules based on changed graphs
4. **Rule Templates**: Parameterized rules for common patterns
5. **Rule Validation**: Check rules for syntax errors at startup
6. **Hot Reload**: Reload rules without server restart
7. **Rule Metrics**: Track execution times and trigger counts
8. **Visual Rule Editor**: Web UI for creating rules

## API Reference

### MazeRule

```java
public class MazeRule {
    public MazeRule(String name, String sparqlConstruct, String description);
    public String getName();
    public String getSparqlConstruct();
    public String getDescription();
}
```

### MazeRuleLoader

```java
public class MazeRuleLoader {
    public List<MazeRule> loadRules(List<String> ruleFilenames);
    public MazeRule loadRule(String filename) throws IOException;
    public List<String> discoverRuleFiles();
}
```

### MazeRuleEngine

```java
public class MazeRuleEngine {
    public MazeRuleEngine(SailRepository repository, List<MazeRule> rules);
    public RuleExecutionResult executeRules();
    public void addRule(MazeRule rule);
    public List<MazeRule> getRules();
    
    public static class RuleExecutionResult {
        public int getRulesTriggered();
        public int getTriplesAdded();
        public List<String> getTriggeredRuleNames();
        public boolean hasChanges();
    }
}
```

### MazeGameEngine

```java
public class MazeGameEngine {
    public MazeRuleEngine.RuleExecutionResult executeRules();
    public MazeRuleEngine getRuleEngine();
}
```

## Troubleshooting

### Rules Not Loading

**Problem**: `Discovered 0 rule files`

**Solution**: 
- Check rule files are in `src/main/resources/rules/`
- Verify rule names in `discoverRuleFiles()`
- Ensure files have `.rq` extension

### Rules Not Triggering

**Problem**: POST succeeds but rules don't execute

**Solution**:
- Check WHERE clause conditions are met
- Enable debug logging to see rule evaluation
- Verify SPARQL syntax is correct
- Test query directly against RDF store

### Wrong Graph Context

**Problem**: Triples added to wrong graph

**Solution**:
- Rule engine uses subject URI as graph context
- Ensure CONSTRUCT creates triples with correct subject
- Consider using explicit GRAPH clauses

## Related Documentation

- [Maze Game Engine README](../README.md)
- [SPARQL 1.1 Specification](https://www.w3.org/TR/sparql11-query/)
- [RDF4J Documentation](https://rdf4j.org/documentation/)
