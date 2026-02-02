# MASE Creator (Experimental)

MASE Creator loads a base maze from TriG format, applies SPARQL update queries to transform it, validates the result, and exposes it as a Linked Data server.

## How to Run

```powershell
gradle run
```

The application will:
1. Load `data/input/Maze.trig` with base IRI `http://127.0.1.1:8080`
2. Apply SPARQL updates from `data/query/*.rq` (sorted alphabetically)
3. Run validation queries from `data/validate/*.rq`
4. Export result to `data/output/MaseCreator.trig`
5. Start Fuseki server on port 8080

## What It Does

- **Loads** base maze with relative URIs (e.g., `</cells/4/4>`)
- **Transforms** maze structure using SPARQL UPDATE queries
- **Validates** transformed maze with SELECT queries
- **Exports** result as TriG with relative URIs preserved
- **Serves** maze via:
  - SPARQL query endpoint: `http://127.0.1.1:8080/query`
  - SPARQL update endpoint: `http://127.0.1.1:8080/update`
  - GSP endpoint: `http://127.0.1.1:8080/data`

## Using the Output

### Generated File
The transformed maze is saved to `data/output/MaseCreator.trig` in standard TriG format with relative URIs.

Mase-Server can directly use this file as input. Make sure to align sim-File and Rules folder.

### Accessing Graphs via GSP
Individual named graphs (cells) are accessible via Graph Store Protocol:

```
http://127.0.1.1:8080/data?graph=http://127.0.1.1:8080/cells/4/4
```

### SPARQL Queries
Query the dataset at `http://127.0.1.1:8080/query`:

```sparql
SELECT ?cell ?label WHERE {
  GRAPH ?cell {
    ?cell rdfs:label ?label .
  }
}
```

## Configuration

Modify constants in `MaseCreator.java`:
- `INPUT_TRIG` - Input maze file path
- `QUERY_DIR` - SPARQL update queries directory
- `VALIDATION_DIR` - Validation queries directory
- `OUTPUT_TRIG` - Output file path
- `BASE_IRI` - Base IRI for relative URI resolution
