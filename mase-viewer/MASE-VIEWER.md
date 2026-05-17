# MASE_VIEWER: Maintain viewer runtime observability and state handling

This document keeps the existing `MASE-VIEWER.md` name because it is already a durable viewer planning and context file. It now follows the high-level ExecPlan section order so future Codex sessions can resume viewer work from one file.

No `PLANS.md` or `.agent/PLANS.md` guide is currently checked into this repository. This document follows the local `PLAN_<SCOPE>.md` structure from the Codex exec-plan guidance while preserving the viewer-specific notes that were already useful.

Last reviewed: 2026-05-17.

## Purpose / Big Picture

The viewer is the browser-facing visualization and inspection tool for MASE. It displays the current maze, projects live runtime events onto a Konva canvas, and exposes event and RDF context so agent behavior can be debugged while a simulation is running.

The viewer should remain a diagnostic tool first. It should make live execution observable without becoming the system of record for simulation state. The MASE server and RDF store remain authoritative, and the viewer should consume snapshots and runtime events from that authoritative source.

Future work on log retention, event archival, reset behavior, and scenario metadata should keep that boundary clear. A user should be able to watch a long-running experiment, inspect recent and archived events, and recover the viewer state after reconnects or scenario resets without the canvas depending on unbounded table history.

## Progress

- [x] (2026-05-16) Recorded current viewer architecture, runtime event flow, bottlenecks, risks, and candidate improvements in the original living notes.
- [x] (2026-05-17 13:19Z) Re-aligned this file to ExecPlan-style high-level sections while preserving the existing viewer guidance.
- [x] (2026-05-17 13:19Z) Documented the dependency between viewer reset invalidation and [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md).
- [x] (2026-05-17) Added a trigger-only Reset Store button in [src/routes/+page.svelte](src/routes/+page.svelte) that calls `POST /admin/maze/reset`.
- [x] (2026-05-17) Kept the user-facing "Cell Updates" label and removed the rename/clarification task.
- [x] (2026-05-17) Added bounded hot log limits of 50 rows each for agent movement and transaction tables.
- [x] (2026-05-17) Replaced expanded transaction row index with a stable event key.
- [x] (2026-05-17) Introduced [src/lib/eventArchive.ts](src/lib/eventArchive.ts), an IndexedDB event archive module with NDJSON export.
- [x] (2026-05-17) Changed Reset Store into an Export logs / Discard logs / Cancel flow, then fully reset the viewer after export or discard.
- [x] (2026-05-17) Kept log export Docker-compatible by using browser-side file save/download instead of assuming write access to the container filesystem.
- [x] (2026-05-17) Added an Export Logs button outside the reset flow.
- [x] (2026-05-17) Renamed Clear Logs to Clear Tables and made it clear visible table rows without deleting archived logs.
- [x] (2026-05-17) Added Load More paging from IndexedDB into both event tables, with 100-row pages and automatic continuation after the first click when the user scrolls to the bottom.
- [x] (2026-05-17) Added archive counts by event type, search-aware archived Load More, and event-type checkbox selection for NDJSON export.
- [x] (2026-05-17) Replaced hard-coded viewer server URLs with runtime configuration for local and Dockerized runs.
- [x] (2026-05-17) Cleaned up exit-cell canvas resource handling by releasing completed-agent color assignments.
- [x] (2026-05-17) Investigated creator-generated `#Correct plan` comments as a source for future server-owned optimal route metadata.
- [x] (2026-05-17) Reworked the backlog so completed hot-log, export, and reset items no longer appear as future work.
- [x] (2026-05-17) Added export processing spinners and changed success message auto-dismiss to 15 seconds.
- [ ] Add a dedicated archive browser state in the UI: event-type filters, run/session scope, visible loaded/available counts, and a clearer distinction between hot rows and cold rows appended from IndexedDB.
- [ ] Add focused checks for archive/export behavior: event-type filtered NDJSON export, Clear Tables preserving IndexedDB data, Reset Store discard/export clearing the right state, and 15-second success banner behavior.
- [ ] Decide remote/Docker graph URI handling for Cell and Agent Inspectors, because inspector fetches still use server-authored resource URLs directly.
- [ ] Define the server/scenario metadata contract for optimal routes parsed from creator `#Correct plan` comments, then replace [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) when the server exposes that data.
- [ ] Add completed-agent summaries derived from archived movement events without deleting transaction history.
- [ ] Revisit table virtualization only after manual runs show that repeated Load More creates a real rendering problem.

## Surprises & Discoveries

- Observation: The viewer already has two separate state paths: page-load snapshot state and live WebSocket projection state.
  Evidence: [src/routes/+page.server.ts](src/routes/+page.server.ts) fetches `/admin/maze`, while [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) receives WebSocket events and [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) applies runtime canvas updates.

- Observation: Offloading event history only helps because the live UI now stops retaining and rendering the entire event history.
  Evidence: `agentEvents` and `transactionEvents` in [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) are bounded to 50 rows each, while older accepted events are appended to [src/lib/eventArchive.ts](src/lib/eventArchive.ts).

- Observation: `sessionStorage` persistence was replaced because it rewrote complete arrays and did not fit long-running archives.
  Evidence: [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) now appends events to [src/lib/eventArchive.ts](src/lib/eventArchive.ts), which stores records in IndexedDB and exports NDJSON.

- Observation: Viewer reset invalidation is coupled to the server reset plan and to viewer log/archive design.
  Evidence: [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md) defers viewer invalidation while planning a `POST /admin/maze/reset` endpoint and a reset notification. The viewer must decide whether reset clears, archives, or preserves hot logs before it can safely implement a button or automatic `ADMIN_RESET` handling.

- Observation: The viewer now initiates server reset only after the user chooses what to do with current logs.
  Evidence: [src/routes/+page.svelte](src/routes/+page.svelte) opens an Export logs / Discard logs / Cancel dialog, posts to the configured admin reset URL after Export or Discard, then clears logs, invalidates data, remounts [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte), and closes inspectors.

- Observation: Browser code cannot silently write exported logs into the repository or Docker container filesystem.
  Evidence: File export must be initiated by the browser, either through the File System Access API with a user-selected directory such as `mase-viewer/log`, or through a normal browser download fallback.

- Observation: Table clearing and log deletion are now separate operations.
  Evidence: [src/routes/+page.svelte](src/routes/+page.svelte) labels the visible clear action as Clear Tables and calls `mazeState.clearTables()`, while reset/discard still clears the IndexedDB archive through `mazeState.resetForNewRun()`.

- Observation: Dockerized viewer deployments need separate internal and browser-facing server URLs.
  Evidence: [src/routes/+page.server.ts](src/routes/+page.server.ts) fetches the initial snapshot through `MASE_SERVER_INTERNAL_HTTP_URL`, while [src/routes/+page.svelte](src/routes/+page.svelte) and [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) use the returned public HTTP/WebSocket URLs for browser-side reset and live events.

- Observation: Creator-generated TriG files already carry parseable optimal route comments.
  Evidence: [MazeTrigSerializer.java](../mase-creator/app/src/main/java/org/mase/creator/trig/MazeTrigSerializer.java) writes a `#Correct plan` section with one commented cell URI per route step, and [MazeTrigParser.java](../mase-creator/app/src/main/java/org/mase/creator/trig/MazeTrigParser.java) parses that section back into `MazeModel.optimalRoute()`.

- Observation: With continuous IndexedDB archival, exit-cell handling should not delete diagnostic transaction history.
  Evidence: [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) archives every accepted event before hot-window pruning, so completed-agent cleanup can stay focused on canvas resources in [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte).

## Decision Log

- Decision: Keep the server and RDF repository authoritative.
  Rationale: The viewer is a projection and diagnostic surface. It should not invent simulation state or treat browser storage as the source of truth.
  Date/Author: 2026-05-16 / prior viewer notes

- Decision: Keep the runtime canvas update path independent from table rendering.
  Rationale: A slow table or large event history must not block agent markers, UI overlays, or cell background updates.
  Date/Author: 2026-05-16 / prior viewer notes

- Decision: Treat reset invalidation as a cross-plan dependency rather than immediate viewer work.
  Rationale: The server reset endpoint in [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md) must define the reset response and notification contract first. The viewer also needs a log/archive policy because reset can reasonably mean "clear live state", "archive old experiment state", or "preserve logs for audit while remounting the canvas".
  Date/Author: 2026-05-17 / Codex

- Decision: Add a trigger-only reset button as an intermediate step before full invalidation behavior.
  Rationale: The endpoint was useful to invoke from the browser before the hot/cold log model, canvas remount strategy, and inspector lifecycle were implemented. This has since been superseded by the Export logs / Discard logs / Cancel flow.
  Date/Author: 2026-05-17 / Codex

- Decision: Keep "Cell Updates" as the user-facing table label.
  Rationale: The label is more useful for users than exposing the implementation detail that the rows are backed by `TRANSACTION` events. Technical work should focus on retention, stable row identity, and archive access rather than renaming the table.
  Date/Author: 2026-05-17 / Codex

- Decision: Use NDJSON as the export format for viewer logs.
  Rationale: Transaction traces can contain nested rule diffs, request bodies, merge deltas, and future nested event fields. NDJSON preserves one complete JSON object per event without flattening or losing structure.
  Date/Author: 2026-05-17 / Codex

- Decision: Keep 50 recent agent movement rows and 50 recent Cell Updates rows in reactive memory.
  Rationale: The live UI only needs a small hot window for immediate inspection. Older events should move to IndexedDB and export paths so Svelte reactivity and table rendering stay bounded.
  Date/Author: 2026-05-17 / Codex

- Decision: Reset Store must ask whether to export or discard logs before resetting.
  Rationale: Reset starts a new experiment run. The viewer should not silently destroy diagnostic logs, but it also should not keep stale canvas, inspector, or hot log state attached to the fresh run after the user chooses export or discard.
  Date/Author: 2026-05-17 / Codex

- Decision: Clear Tables must not delete archived logs.
  Rationale: Users need a quick way to reduce visible table noise without losing experiment evidence. Log deletion is reserved for Reset Store with Discard Logs, or future explicit archive-management controls.
  Date/Author: 2026-05-17 / Codex

- Decision: Archived table paging loads 100 rows per page and switches to scroll continuation after the first click.
  Rationale: The first click is a deliberate opt-in to cold log browsing. After that, scrolling to the table bottom is enough to append older archived rows without repeated button clicks.
  Date/Author: 2026-05-17 / Codex

- Decision: Export keeps all event types selected by default but lets the user deselect noisy types such as `UI_UPSERT`.
  Rationale: Full export remains lossless by default, while targeted export can produce agent-focused NDJSON without UI overlay churn when that noise is not useful for an experiment report.
  Date/Author: 2026-05-17 / Codex

- Decision: Use split runtime URL configuration for viewer-to-server traffic.
  Rationale: In Docker, the SvelteKit node process can reach `mase-server` by service name, while the browser usually needs `localhost` or another host-reachable URL. `MASE_SERVER_INTERNAL_HTTP_URL` and `PUBLIC_MASE_SERVER_*` keep both paths explicit.
  Date/Author: 2026-05-17 / Codex

- Decision: Do not prune archived transaction rows when an agent reaches the exit.
  Rationale: Transaction events can describe shared environment or cell updates that remain relevant after one agent completes. Continuous archival plus bounded hot windows gives enough performance relief without destroying context.
  Date/Author: 2026-05-17 / Codex

- Decision: Treat optimal route extraction as server/scenario metadata work, not frontend file parsing.
  Rationale: `#Correct plan` comments are parseable from generated TriG, but the browser should not read scenario files directly. The server package/scenario metadata path should expose route metadata, after which [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) can be retired.
  Date/Author: 2026-05-17 / Codex

- Decision: Keep frontend scenario-specific logic visibly separated from runtime state projection.
  Rationale: Static optimal routes are acceptable as visualization aids, but scenario-specific simulation behavior belongs in the server rules and RDF data.
  Date/Author: 2026-05-16 / prior viewer notes

## Outcomes & Retrospective

This file has been converted from freestyle living notes into an ExecPlan-style viewer planning document. The viewer now includes an export/discard/cancel reset flow backed by an IndexedDB archive.

The current implementation has bounded hot arrays, IndexedDB archival, NDJSON export with event-type selection, archive counts, search-aware archived Load More, runtime server URL configuration, reset invalidation, export spinners, and 15-second success confirmations. The next useful work is a clearer archive browsing UI and focused checks around export/reset behavior. Server-owned scenario metadata and long-running event-source architecture remain separate follow-up tracks.

## Context and Orientation

The `mase-viewer` module is a SvelteKit application using Svelte 5, Vite, Tailwind, and Konva. The server module is `mase-server`, which owns the RDF store and WebSocket event stream. The viewer starts from a server snapshot and then applies compact runtime events.

### Current Entry Points

- [README.md](README.md) documents user-facing setup and feature behavior.
- [package.json](package.json) defines the SvelteKit, Svelte 5, Tailwind, Vite, and Konva dependencies.
- [src/routes/+page.server.ts](src/routes/+page.server.ts) loads the initial admin snapshot through the configured internal MASE server URL.
- [src/routes/+page.svelte](src/routes/+page.svelte) composes the main page, inspectors, log filters, and live event tables.
- [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) owns WebSocket connectivity, event buffering, bounded live state arrays, replay de-duplication, and archive coordination.
- [src/lib/eventArchive.ts](src/lib/eventArchive.ts) owns IndexedDB storage, archive run ids, NDJSON export, browser directory save, and download fallback.
- [src/lib/mazeServerConfig.ts](src/lib/mazeServerConfig.ts) resolves internal and browser-facing MASE server URLs for local and Dockerized runs.
- [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) renders the maze, runtime UI overlay, agent markers, tooltips, and optimal route overlay.
- [src/lib/components/AgentEventLog.svelte](src/lib/components/AgentEventLog.svelte) renders the Agent Movements table.
- [src/lib/components/CellEventLog.svelte](src/lib/components/CellEventLog.svelte) renders the Cell Updates table, which currently means transaction trace events from the server.
- [src/lib/types.ts](src/lib/types.ts) defines the admin snapshot and maze layout types shared by the route and canvas.
- [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) contains frontend-only optimal route overlays keyed by scenario name.

### Runtime Architecture

The startup flow is:

1. The SvelteKit server load function in [src/routes/+page.server.ts](src/routes/+page.server.ts) fetches `/admin/maze` from the configured MASE server.
2. The page receives a `MazeAdminSnapshot`, including the maze layout, current UI snapshot, and optional scenario name.
3. [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) draws the maze cells, walls, start/exit labels, UI snapshot, and optional optimal route.
4. On mount, [src/routes/+page.svelte](src/routes/+page.svelte) calls `mazeState.connect()`.
5. [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) opens a WebSocket to the configured browser-facing WebSocket URL.
6. Incoming WebSocket events are validated, timestamped in the browser, queued, and flushed once per animation frame.
7. Runtime canvas events are emitted to registered listeners. The canvas updates directly from this listener path instead of re-rendering from the log table arrays.

The server event types currently understood by the viewer are:

- `AGENT_MOVED`: append to movement log and update the agent marker layer.
- `UI_UPSERT`: append to in-memory UI event history and create or update a Konva node.
- `UI_DELETE`: append to in-memory UI delete history and remove a Konva node.
- `TRANSACTION`: append to the Cell Updates table data.

### Design Principles

The server and RDF repository are authoritative. The viewer projects state and logs events; it should not silently invent simulation state.

Live rendering should use compact runtime events. Heavy transaction traces are diagnostic data and should not be needed for canvas correctness.

UI state should be resilient across short reloads, but browser storage should not be treated as a durable archive.

The canvas update path should remain independent from table rendering. A slow event table must not block agent marker movement or UI overlay updates.

Live memory should be bounded. Long-running or multi-agent simulations need an explicit archival story.

Debuggability should be preserved. Any pruning or offloading should keep a route to older event data through pagination, export, or archived inspection.

Scenario-specific simulation behavior should remain in the server and rules. Frontend scenario knowledge should be limited to visualization aids such as optimal route overlays.

### Current Design Choices

[src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) uses Svelte 5 runes and a single exported `mazeState` instance. The page binds directly to `agentEvents`, `transactionEvents`, and `status`. This is simple and works well for small to medium live sessions, but large arrays directly affect Svelte reactivity and table rendering.

Incoming WebSocket messages are queued and flushed with `requestAnimationFrame`. This is the right direction for visual smoothness because bursts are handled in frame batches instead of synchronously per WebSocket message. The store currently schedules persistence after every flush, with a short debounce. The debounce avoids immediate write storms, but the write itself still serializes the complete retained logs.

On connect, the store seeds a small signature set from recent persisted events. During the first replay window it ignores payloads already seen. This protects the table from duplicate replay bursts after reconnects. The signature excludes the browser timestamp, so two identical event payloads in the replay window can be considered the same. This is intentional for replay protection, but it is not a general event identity system.

[src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) uses four Konva layers: a maze layer for cells, walls, labels, and cell background fills; a UI layer for RDF-defined overlay nodes; an agent layer for live markers; and a tooltip layer for cell coordinate tooltips. Layer draws are batched through `scheduleLayerDraw()` for UI and maze updates. Agent movement currently calls `agentLayer.draw()` directly after each movement operation inside the flushed batch.

SVG-like `Path` UI elements are rasterized to images and cached by geometry and paint attributes. This avoids expensive repeated Konva path rendering for frequently reused icons or markers.

The canvas has a semantic hook for RDF UI events where `ui:layer` is `cellBackground`. Those events update the tracked fill for the matching cell instead of creating a separate overlay node. This keeps cell state visually integrated into the base maze layer.

[src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) stores static route suffixes keyed by normalized scenario names. This is explicitly frontend-only and should stay that way unless the server begins exposing solution metadata. The overlay is selected by `scenarioName` from the admin snapshot and controlled by [src/lib/routeOverlayStore.ts](src/lib/routeOverlayStore.ts).

The Cell Inspector and Agent Inspector fetch RDF directly from the graph URI and request Turtle. The page strips unused prefixes for readability. Double-clicking an agent in the Agent Movements table opens the Agent Inspector. Double-clicking a maze cell opens the Cell Inspector.

### Event Logs Today

The viewer keeps displayed logs as bounded in-memory arrays. `agentEvents` stores the latest 50 retained `AGENT_MOVED` events, and `transactionEvents` stores the latest 50 retained `TRANSACTION` events.

Every accepted WebSocket event is appended asynchronously to IndexedDB through [src/lib/eventArchive.ts](src/lib/eventArchive.ts). Export writes selected archived event types as NDJSON, preserving nested transaction trace payloads.

The two event table components receive visible arrays directly from the page. Initially those arrays are the latest 50 rows. After the user clicks Load More, [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) loads 100 older rows from IndexedDB and appends them to the visible table. Further scrolling to the bottom loads the next 100-row page automatically. When a filter is active, archived Load More scans older IndexedDB records for matching payloads before appending rows.

The Cell Updates table is especially expensive in `full` transaction trace mode because each event can include request body text, merge diffs, and per-rule added/removed triples.

### Exit Cell Behavior

The server layout exposes a logical `exitCell` in the admin snapshot, but the layout service removes that logical exit target from the drawable cell map. In practice, `/cells/999` is a movement target, not a normal maze cell on the canvas.

When the canvas receives an `AGENT_MOVED` event and the event cell equals `maze.exitCell`, it destroys the agent's Konva marker, removes the agent from the `agents` map, removes the agent from the `agentPositions` map, re-layouts the old cell, and draws the agent layer.

Current limitations are that the movement log is not explicitly marked with a completed-agent summary and exit handling is local to the canvas. Transaction logs related to the exiting agent are intentionally not pruned because they can explain shared environment updates.

The exit event is a good lifecycle hook for run summaries. Because all events are already archived continuously, a future implementation should focus on adding a completed-agent summary or archive query view rather than moving rows at exit time.

Be more careful with transactions. Some transactions are global or cell-centric, so deleting all transactions for an exiting agent may hide environment updates that still explain later behavior by other agents. Prefer continuous archival and bounded recent transaction summaries.

### Known Issues and Risks

Visible event arrays start at 50 rows per table, so they no longer grow without user intent or rewrite full `sessionStorage` payloads. Archive data now lives in IndexedDB, which is browser-local and quota-limited. Load More can append cold rows to the visible table in 100-row pages, and the archive can scan for matching rows when the visible filter is active. Filtering over already visible rows is still linear.

The Cell Updates table intentionally keeps its user-facing label, even though it is backed by `TRANSACTION` events from the server. Expanded transaction row state now uses a stable event key instead of a filtered row index.

Browser timestamps are assigned on receipt, not server event timestamps. Cell and Agent Inspector fetches use server-authored graph URIs directly, so non-local deployments still need graph URIs that the browser can reach. Runtime UI event arrays are retained even though rendering uses the current `uiNodes` map, and their long-term purpose should be clarified.

Large full transaction traces can make the frontend heavy even before rows are expanded. The optimal route data is static and duplicated across several scenario keys. The Cell and Agent Inspectors fetch arbitrary graph URIs directly and assume CORS and server availability from the browser.

## Milestones

Milestone 1 stabilizes the live event tables. Add bounded hot log limits of 50 rows each for `agentEvents` and `transactionEvents`, and replace filtered-row index expansion with a stable key. This is implemented in [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) and [src/lib/components/CellEventLog.svelte](src/lib/components/CellEventLog.svelte).

Milestone 2 adds a cold event archive. Introduce an event archive module with an interface such as `appendEvents`, `getEventsByType`, `count`, `clear`, and `exportNdjson`. This is implemented in [src/lib/eventArchive.ts](src/lib/eventArchive.ts). Basic table paging from the archive is implemented; richer archive search remains future work.

Milestone 3 coordinates reset invalidation with the server reset plan. [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md) has defined and implemented the `POST /admin/maze/reset` endpoint, replay-buffer clearing, and reset notification contract. The viewer now presents Export logs, Discard logs, and Cancel choices. Export and Discard call the reset endpoint and then clear hot/archive state, invalidate page data, remount [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte), and close selected inspectors.

Milestone 4 improves deployment configuration. This is implemented by [src/lib/mazeServerConfig.ts](src/lib/mazeServerConfig.ts), [src/routes/+page.server.ts](src/routes/+page.server.ts), [README.md](README.md), and [../docker-compose.ccrs.yml](../docker-compose.ccrs.yml). The viewer can target a non-local MASE server without source edits by setting `MASE_SERVER_INTERNAL_HTTP_URL`, `PUBLIC_MASE_SERVER_HTTP_URL`, and optionally `PUBLIC_MASE_SERVER_WS_URL`.

Milestone 5 improves archive browsing and export control. Archive counts by event type, event-type export checkboxes, and search-aware archived Load More are implemented. Richer archive search UI, saved filter presets, and table virtualization remain follow-up work if large archived views become difficult to browse.

Milestone 6 clarifies scenario metadata boundaries. Creator-generated TriG files already contain parseable `#Correct plan` sections, but the viewer should not parse server data files directly. This milestone is complete at the planning level when the route ownership decision is recorded; implementation belongs with server scenario/package metadata before [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) is retired.

## Plan of Work

Keep the live hot path bounded. [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) should continue to deliver runtime canvas events directly and keep only the small hot windows needed for live diagnosis. Event-table changes must not make table history the source of truth for marker movement or UI overlays.

For archive browsing, build on [src/lib/eventArchive.ts](src/lib/eventArchive.ts). Counts, type-filtered export, and search-aware paging are now available. Next useful increments are a clearer archive browser state in the UI, optional virtualization if visible rows become large after repeated Load More, and more precise indexed queries if full JSON scanning becomes too slow.

For reset/export behavior, keep Reset Store explicit. Cancel does nothing. Export logs writes selected NDJSON event types before reset; Discard logs skips export. After either Export or Discard, reset clears hot and IndexedDB logs, starts a new archive run, invalidates page data, remounts the canvas, closes selected inspectors, clears the filter, and shows a temporary success or failure message with a close button. The separate Export Logs button exports selected event types without resetting, and Clear Tables clears visible rows without deleting archived logs.

For validation, prioritize focused checks before adding more features. The highest-value checks are around [src/lib/eventArchive.ts](src/lib/eventArchive.ts), [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts), [src/lib/mazeServerConfig.ts](src/lib/mazeServerConfig.ts), and the reset/export state transitions in [src/routes/+page.svelte](src/routes/+page.svelte). They should prove the data-retention contract, not only DOM details.

For exit-cell lifecycle behavior, do not delete archived transaction context. The canvas may release marker and color resources when an agent exits. A future store-level completed-agent summary can use the already archived movement events rather than moving or deleting rows at exit time.

For optimal route metadata, coordinate with the server scenario-package work. The creator's `#Correct plan` comment section is parseable, but a browser should receive route metadata from an admin/scenario endpoint or the existing admin snapshot rather than opening TriG files itself.

Keep current agent location rendering behavior fast. There is a backlog workpackage to investigate RDF-backed agent location rendering from `maze:contains` cell triples, but that work should normalize any authoritative location changes into the same compact canvas movement command shape used by the current `AGENT_MOVED` listener.

## Concrete Steps

For viewer implementation work, run commands from `mase-viewer`:

    cd mase-viewer
    npm run check
    npm run lint

Expected result is that Svelte type checking and linting finish without errors. If an implementation touches rendering behavior, also run the dev server:

    cd mase-viewer
    npm run dev

Then open the local Vite URL and verify the canvas, tables, inspectors, and WebSocket status manually against a running `mase-server`.

For reset-related viewer work, do not start until the server endpoint from [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md) exists. After that endpoint exists, manually exercise the integration:

    curl -X POST http://localhost:8080/admin/maze/reset

The reset UI path is [src/routes/+page.svelte](src/routes/+page.svelte). It should open a confirmation dialog first. Export logs should create an NDJSON artifact from IndexedDB. In browsers with the File System Access API, the user can select a directory such as `mase-viewer/log`; otherwise the viewer should fall back to a normal browser download. This remains compatible with Docker because the browser owns the file write.

Then verify that the viewer applies the chosen invalidation behavior. The preferred behavior for this slice is SvelteKit invalidation plus a keyed [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) remount, so old Konva nodes and agent positions do not survive.

## Validation and Acceptance

For log-related changes, verify that live agent markers still move smoothly during event bursts, the Agent Movements table shows recent rows and can access archived rows, and the Cell Updates table works in transaction trace modes `off`, `summary`, and `full`.

Verify reload behavior with existing stored data. Verify Clear Tables clears visible rows without deleting IndexedDB logs, Export Logs can export selected event types, search text is applied when loading archived rows, and Reset Store with Discard Logs clears both visible rows and archived logs.

For canvas changes, verify that maze cells, walls, labels, UI overlays, cell backgrounds, and optimal route overlay still render. Verify that UI upsert/delete events update the correct Konva layer, path rasterization fallback still handles unsupported or invalid path data, and cell and agent inspectors still open from double-click interactions.

For reset invalidation, acceptance depends on the server reset contract in [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md). Once the server can atomically reset the RDF store and clear stale WebSocket replay events, the viewer must prove that a reset does not leave old agent markers, old UI overlays, stale selected inspector data, or stale hot logs attached to the new experiment view unless those old logs are intentionally archived and labeled as a previous run.

For the reset dialog, acceptance is: clicking Reset Store opens Export logs / Discard logs / Cancel. Cancel performs no reset. Export writes NDJSON for the selected event types or reports cancellation/failure without resetting. Export and Discard call `POST /admin/maze/reset`; on success, hot logs, IndexedDB logs, selected inspectors, filter text, and canvas runtime state are cleared, page data is invalidated, and the success message remains visible for 15 seconds unless manually closed.

## Idempotence and Recovery

Log pruning and archival should be safe to run repeatedly. If an archive write fails because of quota, private browsing restrictions, or IndexedDB errors, the live canvas should continue to update and the UI should surface the persistence failure without breaking runtime rendering.

Reset invalidation must be idempotent. Receiving an `ADMIN_RESET` notification twice, or clicking reset twice, should not duplicate archive sessions, corrupt the canvas, or leave the page between two snapshots. If a reset POST succeeds but the snapshot refetch fails, the viewer should show a clear disconnected or stale-state indication and allow retry.

Do not make a destructive archive decision implicit. If reset should delete logs, make that explicit in the UI or configuration. If reset should archive the previous run, assign the archived records to a distinguishable run/session id before clearing the hot view.

The reset button should disable duplicate reset actions while export or reset is pending. IndexedDB clear and archive run rotation should be safe to perform repeatedly.

## Artifacts and Notes

### Log Storage Offloading Findings

Offloading helps because the live UI no longer retains and renders the entire event history. The original bottlenecks were browser memory growth, Svelte reactivity over large arrays, linear filtering, one DOM row per retained event, large nested transaction payloads, synchronous full-array `sessionStorage` writes, and browser quota limits.

The implemented direction keeps a bounded hot window in memory for live tables, appends every event asynchronously to a cold archive, exports selected archived event types as NDJSON, and preserves the direct runtime canvas listener path so canvas updates do not depend on table history. Rich archive UI and table virtualization remain future options.

Suggested hot/cold model:

- Hot `agentEvents`: latest 50 movement events.
- Hot `transactionEvents`: latest 50 transaction events.
- Cold movement archive: append-only records keyed by agent and timestamp.
- Cold transaction archive: append-only records keyed by timestamp, graph, agent, transaction id, and trace mode.
- Table default view: hot/recent rows.
- Archived view: event tables can page older IndexedDB rows; exported NDJSON remains the durable handoff format.

`Clear Tables` clears visible rows only. Reset Store with Discard Logs clears both hot arrays and the IndexedDB archive. Export Logs writes selected archive event types without resetting.

Export target note: [log/README.md](log/README.md) keeps a repository-local `log` folder available for users who want to select `mase-viewer/log` in browsers that support file save. The folder intentionally avoids hidden dotfiles because directory-grant pickers can reject folders that contain system or hidden files. Browser save dialogs cannot be forced to open an arbitrary repository path, but the export picker uses a stable id so supported browsers remember the last selected folder. Browsers without the File System Access API use the normal downloads folder. Dockerized deployments use the same browser-side export path; no container write access is required.

Validation note, 2026-05-17: `git diff --check` reported no whitespace errors. npm-based viewer checks were intentionally not run because manual hot reload testing is being handled by the user.

### Future Feature Backlog

Near-term work is the unchecked `Progress` list: archive browsing UI, focused export/reset/config checks, inspector URL strategy, server-owned optimal-route metadata, completed-agent summaries, and virtualization only if large visible tables prove problematic.

Medium-term work includes RDF-backed agent location rendering investigation and storing transaction summary rows separately from full transaction details if full traces remain heavy.

Long-term work includes a server-supported event history or trace endpoint if long-running audit trails become core and adding performance benchmarks for high-volume movement and transaction streams.

### Workpackage: Agent Location Rendering from Cell Containment Triples

Goal: determine whether live agent markers can be rendered from authoritative `maze:contains` triples in cell graphs instead of relying only on frontend projection from `AGENT_MOVED` events.

Review the server-side movement source of truth in [PostHandler.java](../mase-server/src/main/java/org/maze/application/PostHandler.java), [move.rq](../mase-server/src/main/resources/rules/Global/move.rq), and [move_start.rq](../mase-server/src/main/resources/rules/Global/move_start.rq).

Determine how the viewer could receive containment state: admin snapshot fields, a SPARQL query, transaction trace deltas, a new compact WebSocket event, or deriving `AGENT_MOVED` server-side from RDF updates.

Compare the RDF-backed path with the current canvas path in [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) and [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte), especially for latency, event ordering, reconnect replay, trace mode `off`, and exit-cell handling.

Prototype a normalization layer that turns containment changes into the same canvas movement command shape used by the current `AGENT_MOVED` listener so rendering stays fast and table history does not become the canvas source of truth.

Decide whether `AGENT_MOVED` should remain the compact rendering event, become derived from RDF containment changes on the server, or be replaced by an RDF-backed location event.

### Guidance for Future Codex Sessions

Read [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) before changing live event behavior. It is the central coordination point.

Read [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) before changing runtime visuals. Canvas state is intentionally separate from table state.

Keep the runtime canvas listener path fast and bounded. Avoid making table history the source of truth for canvas rendering.

When changing log retention, update Clear Tables, Reset Store log deletion behavior, and this document. When adding persistent storage, handle quota failures and private browsing failures gracefully.

Do not assume `TRANSACTION` events are enabled. The server can run trace mode `off`, `summary`, or `full`. Do not assume full transaction detail exists, because summary mode intentionally omits request bodies and triple-level diffs.

Before adding reset controls or automatic reset handling, read [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md). Viewer reset behavior depends on the server endpoint, stale replay cleanup, and reset notification contract.

## Interfaces and Dependencies

The viewer depends on `GET /admin/maze` returning a `MazeAdminSnapshot` with layout, UI snapshot, and optional scenario name. The corresponding TypeScript type is defined in [src/lib/types.ts](src/lib/types.ts). The SvelteKit server-side fetch uses `MASE_SERVER_INTERNAL_HTTP_URL` when present.

The viewer depends on browser-facing WebSocket events from the configured `PUBLIC_MASE_SERVER_WS_URL`, currently consumed in [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts). Existing event types are `AGENT_MOVED`, `UI_UPSERT`, `UI_DELETE`, and `TRANSACTION`.

The reset UI depends on [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md). The server plan defines the `POST /admin/maze/reset` response, the WebSocket replay-buffer cleanup behavior, and the `ADMIN_RESET` notification. The viewer should use that endpoint after the user chooses Export logs or Discard logs, then invalidate page data, remount or reset [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte), clear hot and IndexedDB logs in [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts), and close selected cell and agent inspectors in [src/routes/+page.svelte](src/routes/+page.svelte).

[src/lib/eventArchive.ts](src/lib/eventArchive.ts) exposes stable methods: `appendEvents`, `getEventsByType`, `getRecentEventsByType`, `count`, `countByType`, `clear`, and `exportNdjson`. The initial implementation uses IndexedDB, but the interface should not prevent later server-backed storage.

Revision note, 2026-05-17: Reorganized the viewer notes into ExecPlan-style sections and cross-linked the reset/data invalidation dependency with [PLAN_ADMIN_RESET.md](../mase-server/PLAN_ADMIN_RESET.md). No implementation work was performed.

Revision note, 2026-05-17: Added a trigger-only Reset Store button to [src/routes/+page.svelte](src/routes/+page.svelte). The button initiates server reset but intentionally leaves data cleanup, canvas remounting, and snapshot refresh behavior for later work.

Revision note, 2026-05-17: Removed the Cell Updates rename/clarification TODO. The table label remains user-facing wording while the implementation can still use transaction event data internally.

Revision note, 2026-05-17: Planned the IndexedDB/NDJSON log archive and reset dialog implementation. The selected defaults are 50 hot rows each for movement and Cell Updates, NDJSON export for all event types, browser-side directory save or download fallback for Docker compatibility, and full viewer reset after Export or Discard.

Revision note, 2026-05-17: Split table clearing from log deletion, added independent Export Logs, and added 100-row archived log paging with scroll continuation for both visible event tables.

Revision note, 2026-05-17: Applied plan hygiene for the next viewer focus. Completed items were moved out of backlog language, event-type export filtering and runtime server URL configuration were recorded, exit-cell cleanup was narrowed to canvas resources, and optimal-route parsing was assigned to future server/scenario metadata work.

Revision note, 2026-05-17: Changed log export from directory-grant selection to file-save selection with a stable picker id, and replaced the old per-folder ignore file with [log/README.md](log/README.md) plus root ignore rules so browser directory restrictions do not reject `mase-viewer/log`.

Revision note, 2026-05-17: Applied plan hygiene after the export spinner and 15-second banner change. The plan now has an explicit unchecked next-TODO list and the backlog points to that list instead of repeating stale completed work.
