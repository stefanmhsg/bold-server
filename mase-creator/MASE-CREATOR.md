# MASE Creator Implementation Plan

This document is the working plan for turning [README.md](README.md)'s experimental SPARQL-based creator into a two-part tool:

1. A logical maze editor that creates or edits coordinate-based maze TriG files.
2. The existing RDF/Jena validation and server amenities that operate on real store data.

All implementation work for this feature stays inside this `mase-creator` project. Existing maze files such as `../mase-server/data/CcrsMazeV1.trig` are references only.

## Goals

- Create a maze from a blank adjustable X/Y grid.
- Load an existing `.trig` file and parse coordinate cell named graphs into an editable form.
- Keep the editor model logical, not SPARQL-driven: cells, walls, start, exit, and future custom cell data are first-class Java objects.
- Render each base cell as one named graph line in the generated TriG file:

  ```trig
  </cells/31/42> { </cells/31/42> a maze:Cell ; maze:north maze:Wall; maze:west </cells/31/41>; maze:south maze:Wall; maze:east </cells/31/43> . }
  ```

- Generate a complete maze document with prefixes, the `</maze>` graph, coordinate cell graphs, and optional exit support through `maze:exit </cells/999>`.
- Auto-save edits inside the creator project so work is recoverable.
- Preserve an extensible design for later custom cell statements, colors, items, locks, keys, or UI annotations.

## Coordinate And Direction Rules

The in-use files treat the first coordinate as the north/south axis and the second coordinate as the west/east axis:

- `maze:north` points to `</cells/x-1/y>`.
- `maze:south` points to `</cells/x+1/y>`.
- `maze:west` points to `</cells/x/y-1>`.
- `maze:east` points to `</cells/x/y+1>`.

Generated coordinate cells are sorted by first priority X and second priority Y. The editor starts new blank mazes at `1/1`, matching the desired `xhv:start </cells/1/1>` convention. When loading existing files, bounds expand to include the parsed coordinates.

## Logical Model

Core types:

- `CellCoordinate`: immutable coordinate identity and TriG IRI rendering.
- `Direction`: fixed order `north`, `west`, `south`, `east`, offsets, and opposites.
- `MazeCell`: one editable cell with cardinal connection targets and an extension slot for future custom statements.
- `MazeModel`: grid bounds, coordinate cells, start cell, exit source cell, and mutating editor operations.
- `PathStroke`: per-drag state so a path only connects cells that are part of the same stroke.

The model stores walls as absent connections. Connections are always normalized bidirectionally when the target is an adjacent coordinate cell.

## Editing Behavior

- Empty grid tiles render white with light borders.
- Created cells render mid-light-green.
- Walls render as thicker black borders.
- Connected sides do not render a black wall between cells.
- `Draw Path`:
  - Clicking or dragging over empty tiles creates cells.
  - Consecutive adjacent cells in the same drag stroke are connected.
  - A stroke that starts inside an existing cell connects the new tunnel to that cell.
  - A stroke that starts in an empty tile does not auto-connect to unrelated adjacent existing cells.
  - Dragging through two existing adjacent cells opens the wall between them.
- `Draw Wall`:
  - Clicks are interpreted only near a cell boundary.
  - Removing a wall means deleting the connection on both sides.
- `Delete Cell`:
  - Removes the cell and all incoming/outgoing neighbor connections.
  - Leaves an empty walled space.
- `Place Start`:
  - Marks an existing coordinate cell as `xhv:start`.
- `Place Exit`:
  - Marks an existing coordinate cell with `maze:exit </cells/999>`.
  - Generated output includes the dedicated exit graph:

    ```trig
    </cells/999> { </cells/999> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }
    ```

- `Optimal Route`:
  - Click and drag across existing cells only.
  - Does not create cells.
  - Does not require or create wall connections; the route may cross walls because runtime wall state can change.
  - Renders a purple overlay in the editor.
  - Appends a `#Correct plan` comment list with absolute cell URLs, matching the comment convention visible in [CcrsMazeV1.trig](../mase-server/data/CcrsMazeV1.trig).
  - Can be cleared independently from the maze with the `Clear Optimal` action.
  - The viewer does not read this comment section from TriG. To show it in the viewer overlay, copy the exported route cells into [optimalRoutes.ts](../mase-viewer/src/lib/optimalRoutes.ts) for the target scenario.
- `maze:green`:
  - Click and drag across existing cells only.
  - Does not create cells.
  - Does not require or create wall connections.
  - Renders a small dark-green overlay in the editor.
  - Serializes route successors as `maze:green </cells/x/y>` predicates inside the corresponding cell graphs.
  - Loaded files may contain multiple disconnected `maze:green` zones; the parser preserves and renders all of them.
  - Drawing a new `maze:green` route from the tool replaces the loaded route set with the newly drawn route.
  - Can be cleared independently from the maze with the `Clear maze:green` action.

## Parsing Existing TriG

The parser is intentionally tolerant:

- It scans coordinate cell named graphs such as `</cells/12/34> { ... }`.
- It accepts one-line or multiline graph bodies.
- It accepts either `a maze:Cell` or `rdf:type maze:Cell`.
- It extracts the canonical directions, `maze:exit`, `maze:green`, and `#Correct plan` route comments.
- It strips comments before interpreting route predicates so commented-out snippets such as `#maze:east ...; maze:green ...` do not become active editor data.
- It ignores graph blocks that are commented out line-by-line.
- It preserves multiple disconnected active `maze:green` chains, including chains that intentionally point at a missing/commented target graph.
- It preserves custom cell graph payloads lexically:
  - extra same-subject types such as `dyn:Lock`;
  - extra same-subject predicates such as `hydra:operation ...` or `maze:orange ...`;
  - additional statements inside the cell named graph, including multiline lock/key blocks;
  - trailing same-line graph comments after `}`.
- It ignores non-coordinate cell names except the special exit graph `</cells/999>`.
- It normalizes adjacent cell references into bidirectional editor connections.

The preservation strategy is deliberately lexical rather than a full RDF rewrite. The editor regenerates the canonical maze statement and replays the preserved custom payload around it. Cells with preserved custom payload are marked with a small amber corner indicator in the editor. They remain editable because walls and base directions are known maze structure, but custom references can become semantically stale if the user changes nearby cells. Deleting a custom cell deletes its preserved payload. Future custom editing should build typed controls on top of this preserved payload instead of changing the base direction model.

## Serialization

The serializer owns the fixed block order:

1. Prefix declarations.
2. `</maze>` graph.
3. `#NAMED_GRAPHS_START`.
4. Coordinate cell named graphs sorted by X, then Y.
5. Optional `</cells/999>` exit graph.
6. `#NAMED_GRAPHS_END`.

Coordinate cells without extra graph payload are emitted as one line for clarity and diff stability. Cells that were loaded with additional multiline statements are emitted as a small block so the extra RDF remains readable and intact. Direction order is always:

1. `maze:north`
2. `maze:west`
3. `maze:south`
4. `maze:east`

## Auto-Save

The two modes use separate data roots:

- Editor mode: `app/data/editor`
- Validation/server mode: `app/data/validation`

Auto-save writes a generated TriG snapshot to `app/data/editor/autosave/MaseCreator-autosave.trig` after model changes. This is a recoverable working draft, not the final export. Startup opens a blank canvas by default; the editor provides a restore action for the auto-save when needed. The `Create Maze` command writes the final `.trig` file to `app/data/editor/output/MaseCreator.trig` without asking for a path.

The SPARQL validation/server mode writes its RDFWriter output to `app/data/validation/output/MaseCreator-validation.trig` so it cannot overwrite the editor's one-line coordinate cell format.

## Test Cases

- `Draw Path` creates cells and connects consecutive adjacent cells.
- A new path starting in an empty tile next to an existing cell does not auto-connect to that existing cell.
- A path starting inside an existing cell connects the next adjacent cell.
- Drawing through two existing adjacent cells opens a wall.
- `Draw Wall` removes a connection on both cells.
- `Delete Cell` removes the cell and all neighbor references.
- `Place Start` and `Place Exit` persist in serialized TriG.
- Serializer emits one coordinate cell per line and sorts by X, then Y.
- Serializer emits directions in the fixed `north`, `west`, `south`, `east` order.
- Serializer emits optimal-route `#Correct plan` comments without changing cell graph predicates.
- Serializer emits `maze:green` route successors only for the separate `maze:green` tool.
- Parser loads one-line and multiline named graph cell bodies.
- Parser tolerates extra predicates such as `maze:green`.
- Parser preserves custom same-subject predicates such as `maze:orange`.
- Parser preserves extra same-subject types such as `dyn:Lock`.
- Parser preserves additional multiline statements inside cell graphs, such as lock operations and key records.
- Parser preserves trailing same-line graph comments.
- Parser ignores commented-out `maze:green` predicates when reconstructing the separate `maze:green` route.
- Parser preserves multiple disconnected `maze:green` zones and active `maze:green` targets without coordinate cell graphs.
- Parser loads the optimal route from `#Correct plan`.
- Parser loads the `maze:green` route from `maze:green` successors.
- Parser normalizes adjacent parsed references into bidirectional connections.
- Parser ignores non-coordinate named graphs without failing.
- Boundary hit testing only selects a wall when the click is within the configured margin.
- Auto-save writes a restorable TriG file.

## To Do

- [x] Add this living implementation document.
- [x] Add logical model classes for coordinates, directions, cells, strokes, and grid bounds.
- [x] Add logical operations for drawing paths, drawing walls, deleting cells, placing start, and placing exit.
- [x] Add tolerant TriG parser for coordinate cell named graphs.
- [x] Add deterministic TriG serializer for generated maze files.
- [x] Add auto-save support.
- [x] Add Swing editor UI for grid rendering and tool selection.
- [x] Add fixed-path `Create Maze` export and an `Erase All` canvas reset.
- [x] Keep validation export separate from editor export so RDFWriter block formatting cannot replace creator output.
- [x] Split mode data into `app/data/editor` and `app/data/validation`.
- [x] Split optimal-route drawing and `maze:green` drawing into separate tools and outputs.
- [x] Keep the existing SPARQL validation/server path available as the second part.
- [x] Add regression tests for the logical editor and TriG IO.
- [x] Preserve custom per-cell graph payloads during load and export.
- [ ] Future: add typed editing controls for preserved custom per-cell statements.
- [ ] Future: warn when preserved custom references point at cells whose base maze connections changed.
- [ ] Future: add undo/redo for editor operations.
- [ ] Future: add validation warnings directly in the editor before export.
- [ ] Future: support non-coordinate legacy start cells through an explicit migration workflow.
