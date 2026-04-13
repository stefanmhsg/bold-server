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


### Docker
```shell script
docker build . -t mase-server
docker run -p 8080:8080 -e TASKNAME=sim-SmallMaze -it mase-server
```

### Entry Point
http://127.0.1.1:8080/maze

### Example Agent

Run sample dfs agent (name: `bob`) against a running server:
```shell script
gradle runBobAgent
```

The sample agent always sends `Authorization: bob`, starts with `GET /maze`, enters the discovered start cell with `dyn:entersFrom`, then navigates in depth-first-search ordered by `west, north, east, south`. If the current cell is locked and `dyn:needsAction` requires a key type that was previously observed via `GET`, it posts `dyn:keyValue` to unlock and re-checks the cell. It finishes when it reaches `/cells/999` via `maze:exit`.

Detailed example-agent documentation: [src/main/java/org/maze/examples/README.md](src/main/java/org/maze/examples/README.md)

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
  Provides a trace of merged triples and per-rule added/removed triples for debugging/inspection.

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