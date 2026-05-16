# MASE Viewer Living Notes

This document is a working context file for future Codex sessions and developer
iterations on the MASE viewer. It records the current frontend architecture,
important design choices, known issues, and candidate improvements.

Last reviewed: 2026-05-16.

## Purpose

The viewer is the browser-facing visualization and inspection tool for MASE.
Its main job is to display the current maze, project live runtime events onto a
Konva canvas, and expose enough event and RDF context to debug agent behavior.

The viewer should remain a diagnostic tool first. It should make live execution
observable without becoming the system of record for simulation state. The MASE
server and RDF store remain authoritative.

## Current Entry Points

- [README.md](README.md) documents user-facing setup and feature behavior.
- [package.json](package.json) defines the SvelteKit, Svelte 5, Tailwind, Vite,
  and Konva dependencies.
- [src/routes/+page.ts](src/routes/+page.ts) loads the initial admin snapshot
  from `http://localhost:8080/admin/maze`.
- [src/routes/+page.svelte](src/routes/+page.svelte) composes the main page,
  inspectors, log filters, and live event tables.
- [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) owns WebSocket
  connectivity, event buffering, live state arrays, replay de-duplication, and
  current `sessionStorage` persistence.
- [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte)
  renders the maze, runtime UI overlay, agent markers, tooltips, and optimal
  route overlay.
- [src/lib/components/AgentEventLog.svelte](src/lib/components/AgentEventLog.svelte)
  renders the Agent Movements table.
- [src/lib/components/CellEventLog.svelte](src/lib/components/CellEventLog.svelte)
  renders the Cell Updates table, which currently means transaction trace
  events from the server.
- [src/lib/types.ts](src/lib/types.ts) defines the admin snapshot and maze
  layout types shared by the route and canvas.
- [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) contains frontend-only
  optimal route overlays keyed by scenario name.

## Runtime Architecture

The startup flow is:

1. The SvelteKit load function in [src/routes/+page.ts](src/routes/+page.ts)
   fetches `/admin/maze` from the local MASE server.
2. The page receives a `MazeAdminSnapshot`, including the maze layout, current
   UI snapshot, and optional scenario name.
3. [MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) draws the maze
   cells, walls, start/exit labels, UI snapshot, and optional optimal route.
4. On mount, [src/routes/+page.svelte](src/routes/+page.svelte) calls
   `mazeState.connect()`.
5. [mazeState.svelte.ts](src/lib/mazeState.svelte.ts) opens a WebSocket to
   `ws://localhost:8080/ws`.
6. Incoming WebSocket events are validated, timestamped in the browser, queued,
   and flushed once per animation frame.
7. Runtime canvas events are emitted to registered listeners. The canvas updates
   directly from this listener path instead of re-rendering from the log table
   arrays.

The server event types currently understood by the viewer are:

- `AGENT_MOVED`: append to movement log and update the agent marker layer.
- `UI_UPSERT`: append to in-memory UI event history and create or update a
  Konva node.
- `UI_DELETE`: append to in-memory UI delete history and remove a Konva node.
- `TRANSACTION`: append to the Cell Updates table data.

## Design Principles

- The server and RDF repository are authoritative. The viewer projects state and
  logs events; it should not silently invent simulation state.
- Live rendering should use compact runtime events. Heavy transaction traces are
  diagnostic data and should not be needed for canvas correctness.
- UI state should be resilient across short reloads, but browser storage should
  not be treated as a durable archive.
- Keep the canvas update path independent from table rendering. A slow event
  table must not block agent marker movement or UI overlay updates.
- Prefer bounded live memory. Long-running or multi-agent simulations need an
  explicit archival story.
- Preserve debuggability. Any pruning or offloading should keep a route to older
  event data through pagination, export, or archived inspection.
- Keep scenario-specific simulation behavior in the server/rules. Frontend
  scenario knowledge should be limited to visualization aids such as optimal
  route overlays.

## Current Design Choices

### Svelte State

[mazeState.svelte.ts](src/lib/mazeState.svelte.ts) uses Svelte 5 runes and a
single exported `mazeState` instance. The page binds directly to `agentEvents`,
`transactionEvents`, and `status`.

This is simple and works well for small to medium live sessions. It also means
large arrays have direct impact on Svelte reactivity and table rendering.

### WebSocket Batching

Incoming messages are queued and flushed with `requestAnimationFrame`. This is
the right direction for visual smoothness because bursts are handled in frame
batches instead of synchronously per WebSocket message.

The store currently schedules persistence after every flush, with a short
debounce. The debounce avoids immediate write storms, but the write itself still
serializes the complete retained logs.

### Replay De-duplication

On connect, the store seeds a small signature set from recent persisted events.
During the first replay window it ignores payloads already seen. This protects
the table from duplicate replay bursts after reconnects.

The signature excludes the browser timestamp, so two identical event payloads in
the replay window can be considered the same. This is intentional for replay
protection, but it is not a general event identity system.

### Canvas Rendering

[MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) uses Konva layers:

- Maze layer for cells, walls, labels, and cell background fills.
- UI layer for RDF-defined overlay nodes.
- Agent layer for live agent markers.
- Tooltip layer for cell coordinate tooltips.

Layer draws are batched through `scheduleLayerDraw()` for UI and maze updates.
Agent movement currently calls `agentLayer.draw()` directly after each movement
operation inside the flushed batch.

SVG-like `Path` UI elements are rasterized to images and cached by geometry and
paint attributes. This avoids expensive repeated Konva path rendering for
frequently reused icons or markers.

### Cell Background Updates

The canvas has a semantic hook for RDF UI events where `ui:layer` is
`cellBackground`. Those events update the tracked fill for the matching cell
instead of creating a separate overlay node.

This is a useful convention because it keeps cell state visually integrated into
the base maze layer.

### Optimal Route Overlay

[optimalRoutes.ts](src/lib/optimalRoutes.ts) stores static route suffixes keyed
by normalized scenario names. This is explicitly frontend-only and should stay
that way unless the server begins exposing solution metadata.

The overlay is selected by `scenarioName` from the admin snapshot and controlled
by [routeOverlayStore.ts](src/lib/routeOverlayStore.ts).

### Inspectors

The Cell Inspector and Agent Inspector fetch RDF directly from the graph URI and
request Turtle. The page strips unused prefixes for readability.

Double-clicking an agent in the Agent Movements table opens the Agent Inspector.
Double-clicking a maze cell opens the Cell Inspector.

## Event Logs Today

The viewer currently keeps the displayed logs as unbounded in-memory arrays:

- `agentEvents` stores all retained `AGENT_MOVED` events.
- `transactionEvents` stores all retained `TRANSACTION` events.

These arrays are also persisted to browser `sessionStorage` as complete JSON
arrays. On every debounced persistence run, the viewer calls `JSON.stringify()`
over both full arrays and writes both full strings back to `sessionStorage`.

The two event table components receive these arrays directly from the page and
render every retained row:

- [AgentEventLog.svelte](src/lib/components/AgentEventLog.svelte) filters by
  agent URI or formatted cell location and renders all matching rows.
- [CellEventLog.svelte](src/lib/components/CellEventLog.svelte) filters by
  agent or graph and renders all matching transaction rows.

The Cell Updates table is especially expensive in `full` transaction trace mode
because each event can include request body text, merge diffs, and per-rule
added/removed triples.

## Log Storage Offloading Findings

Offloading can help, but only if the live UI stops retaining and rendering the
entire event history.

The current bottlenecks are:

- Browser memory grows with every movement and transaction event.
- Svelte must track and update increasingly large arrays.
- Filtering scans all retained events.
- The DOM contains one row per retained event.
- Transaction rows may contain large nested arrays even when collapsed.
- `sessionStorage` writes are synchronous full-array rewrites.
- `sessionStorage` has limited quota and is not a good long-running log archive.

Recommended direction:

1. Keep a bounded hot window in memory for the live tables.
2. Append every event asynchronously to a cold archive.
3. Add pagination, search, or export for archived rows.
4. Virtualize table rendering if users need thousands of visible live rows.
5. Preserve the direct runtime canvas event listener path so canvas updates do
   not depend on table history.

Storage options:

- IndexedDB is the best browser-side default. It is async, persistent, and can
  index by timestamp, agent, cell, graph, transaction id, and event type.
- NDJSON is a good export/archive format for transaction events because nested
  traces are preserved without flattening.
- CSV is suitable for agent movements because those events are flat:
  timestamp, agent, and cell.
- CSV is a weak primary format for full transaction traces because rule changes
  and RDF triple diffs are nested.
- A browser in-memory database only helps if the Svelte arrays are bounded. It
  improves query ergonomics but does not solve memory pressure by itself.
- Server-side append-only logs would be strongest for durable audit trails, but
  that changes the system boundary and should be coordinated with the server
  runtime model.

Suggested hot/cold model:

- Hot `agentEvents`: latest N movement events plus any not-yet-archived active
  agent trails.
- Hot `transactionEvents`: latest N transaction summaries.
- Cold movement archive: append-only records keyed by agent and timestamp.
- Cold transaction archive: append-only records keyed by timestamp, graph,
  agent, transaction id, and trace mode.
- Table default view: hot/recent rows.
- Archived view: paginated queries from IndexedDB or exported NDJSON/CSV.

`Clear Logs` should clear both hot arrays and the selected archive backend, or
the UI should clearly distinguish between "clear live view" and "delete
archive".

## Exit Cell Behavior

The server layout exposes a logical `exitCell` in the admin snapshot, but the
layout service removes that logical exit target from the drawable cell map. In
practice, `/cells/999` is a movement target, not a normal maze cell on the
canvas.

When the canvas receives an `AGENT_MOVED` event and the event cell equals
`maze.exitCell`, it:

- destroys the agent's Konva marker,
- removes the agent from the `agents` map,
- removes the agent from the `agentPositions` map,
- re-layouts the old cell,
- draws the agent layer.

Current limitations:

- The movement log is not pruned when an agent reaches the exit.
- Transaction logs related to the exiting agent are not pruned.
- `agentColors` keeps the agent color assignment after exit.
- Exit handling is local to the canvas. The event store does not currently know
  that an agent has completed.

The exit event is a good lifecycle hook for archival:

1. Detect exit in the store or in a dedicated session/log manager, not only in
   the canvas.
2. Append the completed agent trail to the cold archive.
3. Remove that agent's older movement rows from the hot movement buffer.
4. Keep a compact completion summary in the hot table.
5. Let the canvas release visual resources, including the color assignment if
   colors do not need to remain stable for archived display.

Be more careful with transactions. Some transactions are global or cell-centric,
so deleting all transactions for an exiting agent may hide environment updates
that still explain later behavior by other agents. Prefer continuous archival
and bounded recent transaction summaries.

## Known Issues And Risks

- Event arrays are unbounded and directly drive the visible tables.
- `sessionStorage` persistence rewrites all retained log data each time.
- Event logs survive reload only within the current browser tab session.
- There is no explicit maximum row count, pagination, or virtualization.
- Filtering is linear over all retained rows.
- The Cell Updates table name can be misleading because it displays
  `TRANSACTION` events, not only cell-local UI or state updates.
- Expanded transaction row state is tracked by filtered row index. Filtering or
  inserted rows can make the expansion point at a different event.
- Browser timestamps are assigned on receipt, not server event timestamps.
- The WebSocket URL and admin fetch URL are hard-coded to local server ports.
- Agent marker cleanup on exit does not clear all per-agent canvas metadata.
- Runtime UI event arrays are retained even though rendering uses the current
  `uiNodes` map. Their long-term purpose should be clarified.
- Large full transaction traces can make the frontend heavy even before rows
  are expanded.
- The optimal route data is static and duplicated across several scenario keys.
- The Cell and Agent Inspectors fetch arbitrary graph URIs directly and assume
  CORS/server availability from the browser.

## Future Features

- IndexedDB-backed event archive with hot-window table state.
- Export controls for agent movement CSV and transaction NDJSON.
- Table pagination and virtual scrolling.
- Per-agent session summaries: first seen, current cell, exit time, step count,
  duration, and status.
- Agent completion archive triggered by exit cell arrival.
- Transaction detail lazy loading so collapsed rows keep only summary fields.
- Better table identity using transaction id or generated event id instead of
  filtered row index.
- Configurable server base URL and WebSocket URL.
- Event rate metrics and dropped/pruned row indicators.
- Scenario metadata endpoint for optimal routes or annotations, replacing
  hard-coded route lists where possible.
- A compact "current agents" panel separate from the raw movement log.
- Optional pause/resume for table updates while canvas rendering continues.
- Dedicated diagnostics for WebSocket reconnects, replay drops, and persistence
  failures.

## Iterative To-Do List

Near-term:

- Add bounded hot log limits for `agentEvents` and `transactionEvents`.
- Replace expanded transaction row index with a stable key.
- Clear `agentColors` when an agent exits if stable historical colors are not
  required.
- Rename or label the Cell Updates table to clarify that it displays
  transaction events.
- Add visible row counts and retained/archive counts.
- Add lightweight tests or component checks around log pruning behavior.

Medium-term:

- Introduce an event archive module with an interface that can start with
  IndexedDB and later support export or server-backed storage.
- Archive completed agent trails when an exit movement arrives.
- Add CSV export for agent movement archives.
- Add NDJSON export for transaction archives.
- Add table pagination or virtual scrolling.
- Store summary rows separately from full transaction details.

Long-term:

- Add a server-supported event history or trace endpoint if long-running audit
  trails become a core feature.
- Move optimal route metadata out of frontend source if routes should be
  scenario data.
- Support multiple server base URLs or remote MASE deployments cleanly.
- Add performance benchmarks for high-volume movement and transaction streams.

## Guidance For Future Codex Sessions

- Read [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) before
  changing live event behavior. It is the central coordination point.
- Read [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte)
  before changing runtime visuals. Canvas state is intentionally separate from
  table state.
- Keep the runtime canvas listener path fast and bounded.
- Avoid making table history the source of truth for canvas rendering.
- Prefer small, explicit interfaces for archival work:
  `appendEvent`, `queryRecent`, `queryByAgent`, `clear`, and `export`.
- When changing log retention, update both the Clear Logs behavior and this
  document.
- When adding persistent storage, handle quota failures and private browsing
  failures gracefully.
- Do not assume `TRANSACTION` events are enabled. The server can run trace mode
  `off`, `summary`, or `full`.
- Do not assume full transaction detail exists. Summary mode intentionally omits
  request bodies and triple-level diffs.
- Keep frontend scenario-specific logic visibly separated from runtime state
  projection.

## Validation Checklist

For log-related changes:

- Verify live agent markers still move smoothly during event bursts.
- Verify the Agent Movements table shows recent rows and can access archived
  rows if archival is implemented.
- Verify the Cell Updates table works in transaction trace modes `off`,
  `summary`, and `full`.
- Verify reload behavior with existing stored data.
- Verify Clear Logs behavior for both hot and archived data.
- Verify an agent reaching `maze.exitCell` removes its marker and does not break
  old-cell re-layout.
- Verify completed agent data remains inspectable through the chosen archive or
  summary UI.

For canvas changes:

- Verify maze cells, walls, labels, UI overlays, cell backgrounds, and optimal
  route overlay still render.
- Verify UI upsert/delete events update the correct Konva layer.
- Verify path rasterization fallback still handles unsupported or invalid path
  data.
- Verify cell and agent inspectors still open from double-click interactions.
