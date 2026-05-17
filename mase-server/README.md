# Linked Data MASE Server

This is the server for the Linked Data Multi-Agent System Environment (MASE) platform. It allows users to run simulations of interactive maze scenarios accessible to agents via HTTP GET and POST. A corresponding front end for visualization purposes is available (see [MASE viewer](../mase-viewer/README.md)).

## Getting Started

### Gradle

Dev - deafults to "SmallMaze" (loads rules/Global per default)
```shell script
gradle runMase
```

Specifing maze as argument (loads rules/Global per default)
```shell script
gradle runMase --args="sim-MidMaze"
```

Specifing maze AND rules as argument (loads rules/Global per default AND rules/Global/Stigmergy)
```shell script
gradle runMase --args="sim-SmallMaze  Stigmergy"
```

Run a self-contained scenario package:
```shell script
gradle runMase --args="--scenario scenarios/smallmaze"
```

Package mode can also be selected through an environment variable when no command-line argument is supplied:
```shell script
$env:MASE_SCENARIO_DIR = "scenarios/ccrs"
gradle runMase
```

In package mode the server reads `scenario.properties`, RDF data, and SPARQL rules relative to the selected scenario directory. Every `.rq` file below the package's `rules/` directory is active and loaded recursively. Files below `rules-disabled/` are ignored and can be used for deactivated or work-in-progress rules.

Scenario-local runtime settings and agent launch defaults belong in `scenario.properties`. Scenario packages should not need a separate `.env.example` file just to hold those parameters.

The current CCRS agent Gradle tasks read launch defaults from the selected package's `scenario.properties` and pass them to the legacy Java agent classes as environment variables. Use `-PmaseScenario=scenarios/ccrs` to point those tasks at a different package; shell environment variables still override package defaults.

Built-in package examples live under [scenarios](scenarios).


### Docker
```shell script
docker build . -t mase-server
docker run -p 8080:8080 -e TASKNAME=sim-SmallMaze -it mase-server
docker run -p 8080:8080 -e MASE_SCENARIO_DIR=scenarios/smallmaze -it mase-server
```

### Entry Point
http://127.0.1.1:8080/maze

### Scenario Runtime Settings

Server runtime settings that belong to a simulation run are configured in the selected legacy `sim-*.properties` file or in a package-local `scenario.properties` file.

```properties
# Transaction trace mode: off, summary headers/rule count, or full per-triple debug diffs
mase.transaction.trace = summary
```

`mase.transaction.trace` controls WebSocket transaction diagnostics:

- `off`: no `TRANSACTION` debug events.
- `summary`: emit lightweight transaction headers with time, trigger, agent, graph, status, error, and executed rule count. This keeps cell-update visibility without per-triple logging.
- `full`: include request body, merged triples, and per-rule RDF diffs. This is useful for debugging but expensive during larger multi-agent runs because it snapshots RDF state around rule execution.

### Admin Reset Endpoint

The admin API can reset the in-memory RDF4J store back to the dataset configured by `mase.init.dataset` in the active legacy `sim-*.properties` file or package-local `scenario.properties` file:

```shell script
curl -X POST http://localhost:8080/admin/maze/reset
```

The endpoint clears the repository, reloads the configured TriG dataset, runs the same startup rules as server boot, clears stale WebSocket replay messages, and returns a fresh admin maze snapshot as JSON. The reset is serialized against normal agent POST mutations and SPARQL updates so other writes cannot interleave with the clear/reload transaction.

Viewer-side data invalidation is intentionally handled separately. See [PLAN_ADMIN_RESET.md](PLAN_ADMIN_RESET.md) and [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md) for the reset/viewer dependency and future UI considerations.

### Example Agent

Run sample dfs agent (name: `bob`) against a running server:
```shell script
gradle runBobAgent
```

The sample agent always sends `Authorization: bob`, starts with `GET /maze`, enters the discovered start cell with `dyn:entersFrom`, then navigates in depth-first-search ordered by `west, north, east, south`. If the current cell is locked and `dyn:needsAction` requires a key type that was previously observed via `GET`, it posts `dyn:keyValue` to unlock and re-checks the cell. It finishes when it reaches `/cells/999` via `maze:exit`.

Detailed example-agent documentation: [src/main/java/org/maze/examples/README.md](src/main/java/org/maze/examples/README.md)

API response behavior is documented in [src/main/java/org/maze/api/README.md](src/main/java/org/maze/api/README.md).

---

## Defining UI Elements in RDF

UI elements in the maze are modeled as RDF resources and allow to extend and dynamically modify visual aspects of the simulation without changing code. 

Each UI element corresponds conceptually to a Konva node. The backend does not render anything and does not depend on Konva APIs. Instead, it emits declarative UI descriptions that can be directly mapped to [Konva shapes](https://konvajs.org/category/shapes) creation or updates by the frontend.

The mapping principle is simple:

- One RDF resource corresponds to one visual element.
- The RDF predicate `ui:konvaType` defines which Konva node type is instantiated, such as `Rect`, `Circle` or `Arrow`.
- Exception: semantic layer `ui:layer "cellBackground"` can be used to set a cell background color directly via `ui:fill`, without requiring `ui:konvaType`.
- RDF predicates under the ui namespace map one to one to Konva attributes.
- UI elements are attached to cells and interpreted relative to cell layout by the frontend. Therefore, the IRI of the cell should be part of the UI element's IRI e.g. `<http://127.0.1.1:8080/cells/5#ui-lock>`.


The backend defines a minimal UI vocabulary under the ui namespace. This vocabulary is used to describe visual elements declaratively inside RDF graphs.

Core predicates include:

- `ui:hasUiElement`
  Links a maze cell to a UI element resource.

- `ui:konvaType`
  String identifying the Konva node type to instantiate. Examples include `Rect`, `Circle` or `Arrow`.

- `ui:layer`
  Konva layer on canvas. Special value `cellBackground` updates the base fill of a cell (semantic mode, no Konva node creation).

- `ui:anchor`
  Anchor point within the parent cell based on 3x3 grid to position the element. Examples include C for center, NW for top-left, N for top-center, SE for bottom-right, etc.

- `ui:offsetX` and `ui:offsetY`
  Pixel offsets applied relative to the anchor point.

- `ui:width`, `ui:height`, `ui:radius`
  Size attributes depending on the Konva node type.

- `ui:fill`, `ui:stroke`, `ui:strokeWidth`, `ui:opacity`
  Visual styling attributes.

- `ui:direction`
  Symbolic direction value for type `Arrow`. Can be `N`, `E`, `S`, `W`. This is resolved to a rotation angle.

SPARQL Update queries can be used to add, modify or remove UI element definitions at runtime. The frontend will reflect these changes dynamically during the simulation.

### Examples

<img src="ui-elements.png" width="200">

Example UI element definition for a lock icon as a red square in bottom-right corner of cell 5:

```turtle
<http://127.0.1.1:8080/cells/5#ui-lock> ui:konvaType "Rect";
  ui:layer "layer";
  ui:anchor "SE";
  ui:offsetX 0;
  ui:offsetY 0;
  ui:width 12;
  ui:height 12;
  ui:fill "red";
  ui:stroke "black";
  ui:strokeWidth 1 .
```

Example UI element definition for an arrow icon as a green arrow pointing south:

```turtle
<http://127.0.1.1:8080/cells/10#ui-greenArrowS> ui:konvaType "Arrow";
  ui:layer "layer";
  ui:direction "S";
  ui:anchor "C";
  ui:offsetX 0;
  ui:offsetY 0;
  ui:fill "green";
  ui:stroke "green";
  ui:opacity 0.6;
  ui:strokeWidth 4 .
```

Example UI element definition for a cell background color (without `ui:konvaType`):

```turtle
<http://127.0.1.1:8080/cells/5#ui-bg> ui:layer "cellBackground";
  ui:fill "#fde68a" .
```

Example SPARQL Update queries to add and change UI elements can be found in [ui.rq](src/main/resources/rules/Global/ui.rq).

---

## Maze Event Broadcasting

MASE broadcasts runtime state changes to connected viewers via WebSocket (`/ws`, see [MazeBroadcaster.java](src/main/java/org/maze/api/websocket/MazeBroadcaster.java)).
The broadcast stream is generated from RDF statement changes and committed transactions,
so visual updates can happen without reloading the page.

### Background

- The server executes SPARQL UPDATE rules after POST requests.
- During execution, RDF add/remove operations are observed by a connection listener ([MazeUpdateListener.java](src/main/java/org/maze/infrastructure/rdf/MazeUpdateListener.java)).
- Events are buffered and sent after successful commit to avoid partial/rolled-back UI states.

### Event Types

- `AGENT_MOVED`
  Implemented by [AgentMovedEvent.java](src/main/java/org/maze/api/websocket/events/AgentMovedEvent.java).
  Signals agent movement between cells.

- `UI_UPSERT`
  Implemented by [UiUpsertEvent.java](src/main/java/org/maze/api/websocket/events/UiUpsertEvent.java).
  Signals that a UI element was added or updated (e.g., lock color, arrow, key marker).

- `UI_DELETE`
  Implemented by [UiDeleteEvent.java](src/main/java/org/maze/api/websocket/events/UiDeleteEvent.java).
  Signals that a UI element should be removed from the canvas.

- `TRANSACTION`
  Implemented by [TransactionEvent.java](src/main/java/org/maze/api/websocket/events/TransactionEvent.java).
  Provides transaction diagnostics when `mase.transaction.trace` is `summary` or `full` in the selected scenario properties file. Summary mode emits the transaction id, transaction header, and executed rule count; full mode also includes merged triples and per-rule added/removed triples for debugging/inspection.

### Request Ordering

POST requests are handled as server-side transactions. Requests that share a direct MASE resource scope use the same request queue:

- target graph,
- movement source graph from `dynmaze:entersFrom`,
- authenticated agent identity.

This means same-cell updates and same-agent requests are processed one at a time, while unrelated graph scopes can proceed concurrently. MASE does not impose a global simulation clock; transaction ids are server-issued trace identifiers for analysis of server-observed request order.

### Relation to Viewer Rendering

- On startup, the viewer loads a UI snapshot (current RDF UI state) via [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java) and [ +page.ts](../mase-viewer/src/routes/+page.ts).
- During runtime, incremental WebSocket events are applied:
  - `UI_UPSERT` -> create/update Konva node
  - `UI_DELETE` -> destroy Konva node
  - `AGENT_MOVED` -> update agent marker position
- Runtime event handling in viewer is implemented in [mazeState.svelte.ts](../mase-viewer/src/lib/mazeState.svelte.ts) and [MazeCanvas.svelte](../mase-viewer/src/lib/components/MazeCanvas.svelte).
- This keeps the Konva scene synchronized with RDF state in near real time.

In short: RDF is the source of truth, broadcasting is the transport, and the viewer applies
events as visual deltas on top of the initial snapshot.


## Logging

Change the logging level in [jetty-logging.properties](../mase-server/src/main/resources/jetty-logging.properties)
