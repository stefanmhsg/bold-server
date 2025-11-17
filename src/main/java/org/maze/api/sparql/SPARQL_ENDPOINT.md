# SPARQL Endpoint Documentation

## Overview

The SPARQL endpoint at `/sparql` allows direct execution of SPARQL queries against the RDF repository.

## Endpoint

- **URL**: `POST http://localhost:8080/sparql`
- **Authentication**: Optional (via `Authorization` header for audit logging)

## Supported Query Types

1. **SELECT** - Returns tabular results
2. **CONSTRUCT** - Returns RDF graph
3. **ASK** - Returns boolean result
4. **DESCRIBE** - Returns RDF description
5. **UPDATE** - Modifies repository (INSERT, DELETE, etc.)

## Request Methods

### Method 1: Query in Request Body

```bash
curl -X POST http://localhost:8080/sparql \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: application/sparql-results+json" \
  --data-binary @query.rq
```

### Method 2: Query as URL Parameter

```bash
curl -X POST "http://localhost:8080/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D%20LIMIT%2010" \
  -H "Accept: application/sparql-results+json"
```

## Content Negotiation

### SELECT/ASK Queries

**Accept Headers:**
- `application/sparql-results+json` (default)
- `application/sparql-results+xml`
- `text/csv`
- `text/tab-separated-values`

### CONSTRUCT/DESCRIBE Queries

**Accept Headers:**
- `application/ld+json` (default)
- `text/turtle`
- `application/rdf+xml`
- `application/n-triples`

### UPDATE Queries

**Response:**
- `application/json` with success message

## Examples

### Example 1: SELECT Query

**Query File** (`select-cells.rq`):
```sparql
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>

SELECT ?cell ?direction ?neighbor
WHERE {
  GRAPH ?cell {
    ?cell ?direction ?neighbor .
    FILTER(?direction IN (maze:north, maze:south, maze:east, maze:west))
  }
}
LIMIT 10
```

**Execute:**
```bash
curl -X POST http://localhost:8080/sparql \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: application/sparql-results+json" \
  --data-binary @select-cells.rq
```

**Response** (JSON):
```json
{
  "head": {
    "vars": ["cell", "direction", "neighbor"]
  },
  "results": {
    "bindings": [
      {
        "cell": {"type": "uri", "value": "http://127.0.0.1:8080/cells/0"},
        "direction": {"type": "uri", "value": "https://kaefer3000.github.io/2021-02-dagstuhl/vocab#south"},
        "neighbor": {"type": "uri", "value": "http://127.0.0.1:8080/cells/1"}
      }
    ]
  }
}
```

### Example 2: ASK Query

**Query:**
```sparql
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>

ASK {
  GRAPH ?g {
    ?agent a maze:Agent .
  }
}
```

**Execute:**
```bash
curl -X POST http://localhost:8080/sparql \
  -H "Content-Type: application/sparql-query" \
  --data "PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> ASK { GRAPH ?g { ?agent a maze:Agent . } }"
```

**Response:**
```json
{"head":{},"boolean":true}
```

### Example 3: CONSTRUCT Query

**Query:**
```sparql
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>

CONSTRUCT {
  ?cell a maze:Cell .
  ?cell maze:north ?north .
}
WHERE {
  GRAPH ?cell {
    ?cell a maze:Cell .
    OPTIONAL { ?cell maze:north ?north }
  }
}
LIMIT 5
```

**Execute:**
```bash
curl -X POST http://localhost:8080/sparql \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: text/turtle" \
  --data-binary @construct-cells.rq
```

**Response** (Turtle):
```turtle
@prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .

<http://127.0.0.1:8080/cells/0> a maze:Cell ;
    maze:north <http://127.0.0.1:8080/cells/5> .
```

### Example 4: UPDATE Query (INSERT)

**Query:**
```sparql
PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#>

INSERT DATA {
  GRAPH <http://127.0.0.1:8080/cells/5> {
    <http://127.0.0.1:8080/cells/5#testTriple> dyn:testProperty "test value" .
  }
}
```

**Execute:**
```bash
curl -X POST http://localhost:8080/sparql \
  -H "Content-Type: application/sparql-query" \
  -H "Authorization: Agent testAgent" \
  --data-binary @insert-test.rq
```

**Response:**
```json
{"message":"Update executed successfully"}
```

### Example 5: UPDATE Query (DELETE)

**Query:**
```sparql
PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#>

DELETE DATA {
  GRAPH <http://127.0.0.1:8080/cells/5> {
    <http://127.0.0.1:8080/cells/5#testTriple> dyn:testProperty "test value" .
  }
}
```

**Execute:**
```bash
curl -X POST http://localhost:8080/sparql \
  -H "Content-Type: application/sparql-query" \
  --data-binary @delete-test.rq
```

### Example 6: Complex UPDATE Query (DELETE/INSERT)

**Query:**
```sparql
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>
PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#>

DELETE {
  GRAPH ?cell {
    ?cell dyn:state dyn:locked .
  }
}
INSERT {
  GRAPH ?cell {
    ?cell dyn:state dyn:unlocked .
  }
}
WHERE {
  GRAPH ?cell {
    ?cell a maze:Cell .
    ?cell dyn:keyValue "redkey" .
    ?cell dyn:state dyn:locked .
  }
}
```

**Execute:**
```bash
curl -X POST http://localhost:8080/sparql \
  -H "Content-Type: application/sparql-query" \
  -H "Authorization: Agent admin" \
  --data-binary @unlock-cells.rq
```

## Authentication

The endpoint supports optional authentication via the `Authorization` header:

```bash
curl -X POST http://localhost:8080/sparql \
  -H "Authorization: Agent myAgent" \
  -H "Content-Type: application/sparql-query" \
  --data "SELECT * WHERE { ?s ?p ?o } LIMIT 1"
```

Agent name will be logged for audit purposes.

## Error Handling

### Syntax Error (400 Bad Request)

```json
{
  "error": "org.eclipse.rdf4j.query.parser.ParseException: Encountered unexpected token..."
}
```

### Server Error (500 Internal Server Error)

```json
{
  "error": "Connection to repository failed"
}
```

### Empty Query (400 Bad Request)

```json
{
  "error": "Query is required"
}
```

## Use Cases

### 1. Debugging Maze State

Check which cells an agent can access:
```sparql
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>

SELECT ?from ?direction ?to
WHERE {
  GRAPH ?from {
    <http://127.0.0.1:8080/agents/myAgent> ^maze:contains ?from .
    ?from ?direction ?to .
    FILTER(?direction IN (maze:north, maze:south, maze:east, maze:west))
  }
}
```

### 2. Finding All Locked Cells

```sparql
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>
PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#>

SELECT ?cell ?keyValue
WHERE {
  GRAPH ?cell {
    ?cell a dyn:Lock .
    ?cell dyn:keyValue ?keyValue .
    ?cell dyn:state dyn:locked .
  }
}
```

### 3. Bulk Unlock Operation

```sparql
PREFIX dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#>

DELETE {
  GRAPH ?cell {
    ?cell dyn:state dyn:locked .
  }
}
INSERT {
  GRAPH ?cell {
    ?cell dyn:state dyn:unlocked .
  }
}
WHERE {
  GRAPH ?cell {
    ?cell dyn:state dyn:locked .
  }
}
```

### 4. Query Execution Statistics

Get all agents and their current locations:
```sparql
PREFIX maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#>

SELECT ?agent ?cell
WHERE {
  GRAPH ?cell {
    ?cell maze:contains ?agent .
  }
}
```

## Technical Notes

1. **Transaction Management**: UPDATE queries are executed within a transaction. On error, changes are rolled back.

2. **Graph Context**: Queries can use `GRAPH` clauses to target specific named graphs. Without `GRAPH`, queries execute over the default dataset (all graphs).

3. **Performance**: Complex queries may take time depending on repository size. Consider using `LIMIT` for exploratory queries.

4. **Concurrent Access**: The endpoint supports concurrent queries. UPDATE operations use transaction isolation.

5. **Logging**: All queries are logged with timestamp and agent name (if authenticated).

## Integration with Game Rules

The SPARQL endpoint complements the rule engine:

- **Rule Engine**: Executes CONSTRUCT queries automatically on POST/MOVE
- **SPARQL Endpoint**: Allows manual queries and updates for testing/debugging

Both share the same RDF repository, ensuring consistency.
