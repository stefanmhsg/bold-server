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
- Future: export a complete MASE scenario package, not only a standalone TriG file.
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

## Technology Notes And Migration Discussion

Current MVP technology:

- Java with Gradle is used because this project already lives near Java/RDF tooling and can reuse the existing repository setup.
- Swing is used for the desktop editor UI because it is available in the JDK, needs no browser/runtime packaging, and is enough for a grid canvas, toolbar buttons, file dialogs, and quick local testing.
- The editor model is plain Java objects (`MazeModel`, `MazeCell`, `CellCoordinate`, `Direction`) rather than RDF4J/Jena state. This keeps drawing operations deterministic and testable.
- TriG parsing/serialization is mostly custom and lexical. This was chosen because the editor must preserve comments, one-line cell formatting, commented-out scenario variants, and custom graph payloads that normal RDF parsers/writers would discard or reformat.
- Tests are JUnit-based and focus on model behavior plus parser/serializer round trips.

Why this is acceptable for the MVP:

- It keeps the creator self-contained and fast to iterate on.
- It avoids introducing a web build toolchain before the core maze model and preservation rules are stable.
- It allows direct local file access, auto-save, and fixed output paths without a server component.
- It makes the important architectural boundary clear: logical maze state is separate from RDF/SPARQL validation.

Known limitations of the current stack:

- Swing is functional but not ideal for a polished authoring tool with rich inspectors, docked panels, searchable RDF forms, drag handles, minimaps, and complex validation feedback.
- Custom lexical TriG handling is useful for preservation, but it is also a maintenance risk. It should remain isolated behind parser/serializer tests and fixture round trips.
- UI state is currently coupled to the Swing panel. A future migration is easier if core operations stay in model/service classes and do not depend on Swing types.
- Rich RDF editing will become awkward if implemented only through text dialogs. Typed editors for keys, locks, UI markers, and rule requirements need stronger form abstractions.

Future UI technology candidates:

- **JavaFX desktop app**:
  - Keeps the app in Java and can reuse the current model almost directly.
  - Better UI controls and styling than Swing.
  - Still a desktop app; packaging and distribution need attention.
- **Web UI with local backend**:
  - A browser frontend can provide a more sophisticated canvas, side panels, validation views, and package wizards.
  - The Java core could become a local service or CLI library that handles parsing, serialization, rule generation, and validation.
  - Adds frontend build complexity and local server/process coordination.
- **Electron/Tauri-style desktop shell**:
  - Good for a polished desktop authoring experience using web UI technology.
  - Needs a clear bridge to the Java/RDF logic or a rewrite of the core services.
  - Packaging can become more complex than the current Gradle app.
- **Integrated server-hosted editor**:
  - The creator could eventually run as part of `mase-server`, editing scenario folders directly.
  - Useful once server-side scenario packages exist.
  - Risk: editor experiments could become coupled to runtime server behavior too early.

Migration principles:

- Keep `MazeModel` and related logical operations UI-agnostic.
- Keep TriG preservation, package generation, and rule generation behind service APIs with tests.
- Do not put Swing-specific types into parser, serializer, rule-generation, or scenario-package code.
- Treat the current Swing UI as replaceable MVP shell around reusable core logic.
- Prefer adding new authoring features first as model/service capabilities, then exposing them through Swing controls. That makes later migration to JavaFX or web UI less expensive.

Open discussion questions:

- Should the long-term creator be a standalone desktop application, a web UI backed by a local service, or a module inside `mase-server`?
- Should RDF editing use structured forms only, or also expose an expert text editor for raw cell graph snippets?
- Should scenario-package generation become the main output path, with standalone TriG export treated as an advanced/debug export?
- Should LLM-assisted SPARQL drafting live inside the creator UI, or as a separate command/plugin that the creator can call?
- How much of the current lexical TriG preservation should remain once typed editors cover the major custom scenario features?

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
- It preserves document-level comments and non-coordinate named graphs that the logical grid does not edit directly, such as legacy `</cells/0>`, `</counter>`, and `</colors>` blocks.
- It preserves raw `#Correct plan` comment sections when loading existing files, so legacy entries such as `/cells/0` are not dropped by the coordinate-only route model.
- It preserves non-coordinate direction targets on coordinate cells, such as `</cells/0/2> maze:north </cells/0>`, until the user edits that side.
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

## Scenario Package Vision

The current editor exports a single TriG maze file. A later implementation should add a higher-level **Create Scenario Package** workflow that emits a complete scenario directory the user can copy into `mase-server` as one unit.

Envisioned package layout:

```text
<scenario-name>/
  README.md
  scenario.properties
  data/
    <scenario-name>.trig
  rules/
    Global/
      *.rq
    CcrsMaze/
      *.rq
  ui/
    ui.rq
  manifest.json
  .env.example
```

The exact names can change during implementation, but the package should include:

- the generated or edited maze TriG file;
- a properties document derived from the current `sim-*.properties` pattern;
- SPARQL rule files needed for movement, UI metadata, dynamic locks, key pickup, redirects, broken cells, and other interaction rules;
- a scenario `README.md` describing purpose, start/exit, expected route hints, custom mechanics, required server version, and how to run it;
- a manifest with package metadata, generated file list, rule generation settings, and warnings;
- an `.env.example` for optional LLM configuration, never a real `.env` containing secrets.

This package export should be a separate action from **Create Maze**. **Create Maze** remains the deterministic TriG output path for fast editing. **Create Scenario Package** becomes the richer authoring/export path.

## Custom Scenario Authoring Vision

The editor should grow from grid drawing into scenario authoring. The logical maze model remains the base layer, and higher-level tools add typed custom content.

Planned tools:

- **Cell RDF Inspector**:
  - Shows canonical generated predicates separately from preserved custom content.
  - Allows adding inline predicate-object pairs to the cell subject, such as `maze:orange </cells/32/43>` or custom UI markers.
  - Allows adding custom graph blocks under the cell named graph, such as a key resource or Hydra operation block.
  - Highlights custom content that references cells affected by wall edits, deleted cells, or missing targets.
- **Key Tool**:
  - Places a key in a cell.
  - Captures key type, key value, target lock cell, display label, and optional UI styling.
  - Generates the corresponding key RDF block and any supporting UI annotations.
- **Lock Tool**:
  - Marks a cell as a lock or one-way interaction gate.
  - Captures accepted key type/value, unlock target, success/failure behavior, and whether passage is single-use or permanently opened.
  - Generates Hydra operation blocks, SHACL input shape fragments, and dynamic state predicates.
- **Runtime Behavior Wizard**:
  - Lets the user choose known interaction patterns such as key-lock, redirect, broken cell, color marker, pickup item, or single-passage enforcement.
  - Produces RDF snippets and SPARQL rule requirements from structured options instead of forcing manual RDF editing.

The first implementation should prefer deterministic templates based on rule files that already work in this repository. Existing rule files such as [ccrs.rq](../mase-server/src/main/resources/rules/CcrsMaze/ccrs.rq), [unlock-keys.rq](../mase-server/src/main/resources/rules/CcrsMaze/unlock-keys.rq), [move.rq](../mase-server/src/main/resources/rules/Global/move.rq), and [ui.rq](../mase-server/src/main/resources/rules/Global/ui.rq) should be treated as reference material for template extraction.

## SPARQL Rule Generation Vision

SPARQL generation should have two layers:

1. **Template-based generation** for supported mechanics.
   - Inputs are structured editor settings.
   - Outputs are deterministic `.rq` files or snippets.
   - Generated rules should be traceable to the selected mechanics and scenario cells.
2. **Optional LLM-assisted generation** for experimental or custom mechanics.
   - The user can provide a model/API key through a local `.env`.
   - The `.env` is used only locally and is never copied into the scenario package.
   - The prompt context should include the selected scenario metadata, relevant RDF snippets, existing working rule examples, available vocabularies, and requested behavior.
   - The generated SPARQL must be saved as a draft with warnings until validated by local tests.

LLM support should not replace deterministic generation for known mechanics. It should help draft SPARQL for custom cases, explain generated rules, and suggest missing RDF/UI vocabulary. The editor should require explicit user review before including LLM-generated rules in a package.

Validation for generated rules should include:

- syntax checking;
- dry-run execution against a temporary RDF dataset;
- scenario-specific smoke tests for movement, key pickup, lock opening, redirects, and UI shape output;
- warnings when rules reference cells, predicates, or prefixes absent from the package.

## MASE Server Scenario Folder Vision

Server-side changes are not part of the current `mase-creator` implementation, but this is the intended direction for later `mase-server` documentation and implementation.

`mase-server` should be simplified so it can load one scenario folder that groups all scenario assets:

- data file;
- scenario properties;
- rule folders;
- validation queries or smoke tests;
- README/documentation;
- optional manifest metadata.

The server should be able to point at a scenario directory, read the manifest or default file names, and load the data/rules/config from that directory. Users should not need to copy a TriG file to one folder, rule files to a second folder, and properties to a third folder manually.

Expected server behavior:

- accept a scenario folder path in the existing sim/config flow;
- resolve data, rules, and validation files relative to that scenario folder;
- keep compatibility with the current separate-file setup during migration;
- report missing required package files with actionable messages;
- expose package metadata in startup logs and, later, in a simple server endpoint.

This section should eventually move into a dedicated `mase-server` README once the server-side scenario-folder loader is implemented.

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
- Parser preserves document-level comments, non-coordinate named graphs, and raw `#Correct plan` sections during no-edit round trips.
- Parser preserves legacy non-coordinate direction targets on coordinate cells.
- Parser loads the optimal route from `#Correct plan`.
- Parser loads the `maze:green` route from `maze:green` successors.
- Parser normalizes adjacent parsed references into bidirectional connections.
- Parser ignores non-coordinate named graphs without failing.
- Boundary hit testing only selects a wall when the click is within the configured margin.
- Auto-save writes a restorable TriG file.
- Current UI shell test: toolbar controls are grouped by workflow, all action/tool buttons expose tooltips, and the active drawing tool remains visually clear.
- Fixture round trip: parse [CcrsMazeV1.trig](app/src/test/resources/fixtures/CcrsMazeV1.trig), serialize it, and verify active `maze:green` successors plus representative comments, non-coordinate graphs, legacy direction targets, custom cell payloads, and raw correct-plan comments survive whitespace-insensitively.
- Future package export test: create a scenario package and verify it contains data, properties, rule folders, README, manifest, and `.env.example`.
- Future package export test: generated package paths are relative to the package root and contain no user-local absolute paths or secrets.
- Future custom authoring test: key-lock tool emits key RDF, lock RDF, SHACL shape, Hydra operation, and matching SPARQL rule files.
- Future custom authoring test: custom inline predicate-object additions survive load, edit, package export, and re-import.
- Future rule-generation test: deterministic rule templates produce syntactically valid SPARQL and pass a dry-run against the generated dataset.
- Future LLM-assisted rule test: with mocked LLM output, generated draft rules are marked as unvalidated until local validation succeeds.
- Future server-package smoke test: a scenario folder can be loaded as one unit by the server-side loader once that loader exists.

## Work Packages

These packages group the remaining work into coherent increments. Packages are intentionally ordered from near-term MVP polish to larger authoring and packaging features.

### WP0: Completed Foundations

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
- [x] Add a CcrsMazeV1 fixture round-trip regression for preserving existing scenario components.

### WP1: Current MVP UI Cleanup

Goal: make the existing Swing editor easier to use before adding deeper scenario-authoring features.

- [x] Group toolbar actions by workflow:
  - file/session actions: `New`, `Open`, `Restore Auto-Save`, `Create Maze`;
  - destructive/reset actions: `Erase All`, `Clear Optimal`, `Clear maze:green`;
  - drawing tools: path, wall, delete, start, exit, optimal route, `maze:green`;
  - grid size controls: X/Y spinners.
- [x] Add tooltips to every action and drawing tool.
- [x] Use clearer labels where the current wording is ambiguous.
- [x] Add visual separators or small titled groups so the toolbar no longer reads as one long button row.
- [x] Keep keyboard focus and selected-tool state predictable after clicking action buttons.
- [x] Add lightweight UI tests or component-level assertions where practical.

### WP2: Preservation And Editing Safety

Goal: make imported custom scenarios safer to inspect and edit without silently breaking preserved RDF.

- [ ] Warn when preserved custom references point at cells whose base maze connections changed.
- [ ] Add validation warnings directly in the editor before export.
- [ ] Support non-coordinate legacy start cells through an explicit migration workflow.
- [ ] Show a custom-content summary for selected cells.
- [ ] Track whether a custom cell payload is unchanged, edited, or potentially stale.

### WP3: Custom RDF Editing

Goal: give advanced users controlled editing access to cell-specific RDF without hand-editing the exported TriG file.

- [ ] Add typed editing controls for preserved custom per-cell statements.
- [ ] Add custom inline predicate-object editor for the cell subject.
- [ ] Add custom cell-block editor for additional statements inside the cell named graph.
- [ ] Separate generated canonical predicates from user-authored custom predicates in the UI.
- [ ] Validate custom RDF snippets before allowing export.
- [ ] Preserve custom inline predicate-object and custom block edits across load, edit, export, and re-import.

### WP4: Typed Scenario Mechanics

Goal: model common interactive mechanics explicitly so users do not need to write RDF and SPARQL manually.

- [ ] Add typed key-lock authoring tools that generate RDF and matching SPARQL rule requirements.
- [ ] Add key placement settings: key type, key value, display label, target lock, and optional UI styling.
- [ ] Add lock placement settings: accepted key, unlock behavior, single-use/permanent behavior, and target passage.
- [ ] Add templates for redirects, broken cells, colored markers, pickup items, and single-passage enforcement.
- [ ] Generate Hydra operation and SHACL shape fragments from structured settings where needed.
- [ ] Add smoke tests for generated key-lock and redirect mechanics.

### WP5: Deterministic Rule Generation

Goal: turn existing working SPARQL rules into reusable creator templates.

- [ ] Add deterministic SPARQL rule templates based on existing working rules.
- [ ] Extract reusable rule fragments from current CcrsMaze and Global rules.
- [ ] Add a rule-generation service that maps typed mechanics to `.rq` outputs.
- [ ] Validate generated SPARQL syntax.
- [ ] Dry-run generated rules against a temporary dataset.
- [ ] Report missing prefixes, missing cells, and missing supporting RDF before package export.

### WP6: Scenario Package Export

Goal: export a complete scenario folder instead of requiring users to manually place data, rules, config, and docs.

- [ ] Add **Create Scenario Package** export with TriG, properties, rules, README, manifest, and `.env.example`.
- [ ] Add package metadata fields: scenario name, description, start/exit notes, mechanics used, generated files, and warnings.
- [ ] Generate a properties file derived from the current `sim-*.properties` pattern.
- [ ] Generate a scenario README from the package metadata.
- [ ] Ensure generated package paths are relative to the package root and contain no user-local absolute paths.
- [ ] Add scenario-package validation that checks data, rules, properties, docs, missing references, and secret leakage.

### WP7: Optional LLM-Assisted SPARQL Drafting

Goal: support experimental rule authoring while keeping deterministic templates as the trusted default.

- [ ] Add optional LLM-assisted SPARQL drafting through a local `.env` key.
- [ ] Keep `.env` local and export only `.env.example`.
- [ ] Provide prompt context from scenario metadata, selected RDF snippets, existing working rules, and requested behavior.
- [ ] Mark LLM-generated rules as drafts until local validation succeeds.
- [ ] Add mocked-output tests for the LLM rule draft workflow.

### WP8: Server Scenario Folder Alignment

Goal: align future creator packages with a simpler `mase-server` loading model.

- [ ] Define the scenario folder contract in a future `mase-server` README.
- [ ] Add server-side support for loading data, rules, properties, validation files, and docs from one scenario folder.
- [ ] Keep compatibility with the current separated data/rules/config setup during migration.
- [ ] Add server-package smoke tests once the loader exists.

### WP9: Editor Ergonomics

Goal: improve day-to-day editing once the main authoring model is stable.

- [ ] Add undo/redo for editor operations.
- [ ] Add keyboard shortcuts for common tools and actions.
- [ ] Add selection details for the currently hovered or selected cell.
- [ ] Add optional minimap or viewport controls for large scenarios.
