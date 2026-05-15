# MASE Creator (Experimental)

MASE Creator now has two parts:

1. A logical maze editor for creating and editing coordinate-based TriG maze files.
2. The original SPARQL/Jena validation and server flow for checking real RDF store data.

The current implementation plan and open follow-up items live in [MASE-CREATOR.md](MASE-CREATOR.md).

## How to Run

```powershell
.\gradlew.bat run
```

This opens the desktop maze editor with a blank X/Y grid. The editor supports loading an existing `.trig` file, drawing paths, drawing walls, deleting cells, placing the start, placing the exit, auto-saving drafts, restoring an auto-save when needed, clearing the canvas with **Erase All**, and exporting the generated TriG file.

Editor data lives under `app/data/editor`:

- Optional editor input files: `app/data/editor/input`
- Auto-save snapshots: `app/data/editor/autosave/MaseCreator-autosave.trig`
- **Create Maze** output: `app/data/editor/output/MaseCreator.trig`

## SPARQL Validation Mode

```powershell
.\gradlew.bat run --args="--validate"
```

The validation mode will:

1. Load `app/data/validation/input/MidMaze.trig` with base IRI `http://127.0.1.1:8080`
2. Apply SPARQL updates from `app/data/validation/query/*.rq` (sorted alphabetically)
3. Run validation queries from `app/data/validation/validate/*.rq`
4. Export result to `app/data/validation/output/MaseCreator-validation.trig`
5. Start Fuseki server on port 8080

## What Validation Mode Does

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
The transformed validation dataset is saved to `app/data/validation/output/MaseCreator-validation.trig` in standard TriG format with relative URIs. The editor's **Create Maze** output remains `app/data/editor/output/MaseCreator.trig` and keeps coordinate cell graphs as one-line records.

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

## Configuration For Validation Mode

Modify constants in `MaseCreator.java`:
- `INPUT_TRIG` - Input maze file path
- `QUERY_DIR` - SPARQL update queries directory
- `VALIDATION_DIR` - Validation queries directory
- `OUTPUT_TRIG` - Output file path
- `BASE_IRI` - Base IRI for relative URI resolution
