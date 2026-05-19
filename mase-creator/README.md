# MASE Creator (Experimental)

MASE Creator now has two parts:

1. A logical maze editor for creating and editing coordinate-based maze packages.
2. The original SPARQL/Jena validation and server flow for checking real RDF store data.

The current implementation plan and open follow-up items live in [MASE-CREATOR.md](MASE-CREATOR.md).

## How to Run

```powershell
.\gradlew.bat run
```

This opens the desktop maze editor with a blank X/Y grid. The editor supports loading an existing `.trig` file, drawing paths, drawing a commented optimal route, drawing `maze:green` route predicates, drawing walls, deleting cells, placing the start, placing the exit, auto-saving drafts, restoring an auto-save when needed, clearing route metadata, clearing the canvas with **Erase All**, and exporting a generated scenario package.

When loading customized maze files, the editor preserves additional RDF inside coordinate cell named graphs, such as lock operations, key records, extra same-subject predicates, and trailing comments. Cells with preserved custom payload show a small amber corner marker. The editor still regenerates the canonical maze directions from the visible walls, so review custom references after changing or deleting customized cells.

Editor data lives under `app/data/editor`:

- Optional editor input files: `app/data/editor/input`
- Auto-save snapshots: `app/data/editor/autosave/MaseCreator-autosave.trig`
- **Create Package** output directory: `app/data/editor/output/<package-name>`

**Create Package** writes a server-ready scenario package. The default package is `app/data/editor/output/MaseCreator`, with the maze TriG file at `data/MaseCreator.trig`, runtime settings in `scenario.properties`, package metadata in `manifest.json`, active global SPARQL rules under `rules/global`, and an intentionally empty `rules/scenario` folder for future scenario-specific rules. If the default package already exists, the editor asks whether to overwrite it or create a differently named package in the same output directory. After export, the editor shows the generated package path.

Route tools mark only existing cells. They do not create cells and do not open or close walls.

- **Optimal Route** exports a `#Correct plan` comment list only.
- **maze:green** exports `maze:green </cells/x/y>` successor predicates inside cell graphs. Existing files can contain multiple disconnected `maze:green` zones; loading preserves all active zones, while drawing a new `maze:green` route replaces them with the newly drawn route.

The viewer's optimal-route overlay is hardcoded in [optimalRoutes.ts](../mase-viewer/src/lib/optimalRoutes.ts); it does not read the `#Correct plan` section from TriG. To show an exported route in the viewer, copy the generated comment cells into that TypeScript route list for the target scenario.

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

### Generated Package
The transformed validation dataset is saved to `app/data/validation/output/MaseCreator-validation.trig` in standard TriG format with relative URIs. The editor's **Create Package** output is a scenario folder under `app/data/editor/output`; base coordinate cells stay one-line records inside the package data file, while imported cells with preserved multiline custom RDF remain block formatted.

Mase-Server can load the generated package directly with `--scenario <package-directory>`. See the scenario runtime contract in [mase-server README.md](../mase-server/README.md).

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
