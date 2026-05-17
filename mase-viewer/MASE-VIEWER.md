# MASE_VIEWER: Maintain viewer runtime observability and state handling

This document keeps the existing `MASE-VIEWER.md` name because it is already the durable viewer planning and context file. It follows the local ExecPlan section order and describes the current viewer baseline, active work, durable decisions, and operational notes.

No `PLANS.md` or `.agent/PLANS.md` guide is currently checked into this repository. This document follows the local `PLAN_<SCOPE>.md` structure from the Codex exec-plan guidance while preserving viewer-specific notes that are useful for future sessions.

Last reviewed: 2026-05-17.

## Purpose / Big Picture

The viewer is the browser-facing visualization and inspection tool for MASE. It displays the current maze, projects live runtime events onto a Konva canvas, and exposes event and RDF context so agent behavior can be debugged while a simulation is running.

The viewer is a diagnostic tool, not the system of record. The MASE server and RDF store remain authoritative. The viewer starts from server snapshots, applies compact runtime events, keeps bounded live table state, archives accepted events in browser-local IndexedDB, and exports selected events as NDJSON when a user needs durable logs.

Future work should preserve that boundary. Users should be able to watch long-running experiments, inspect recent and archived events, reset the server store from the UI, and export or discard logs without old canvas state or stale inspector data leaking into the next run.

## Progress

- [ ] Finish the dedicated archive browser state in the UI. Completed: run id display, loaded/available counts per table, hot versus archived row counters, and visible archived-row labels. Remaining: event-type filters beyond export, richer run/session scope, and any deeper archive search controls.
- [ ] Add focused checks for archive/export behavior: event-type filtered NDJSON export, Clear Tables preserving IndexedDB data, Reset Store discard/export clearing the right state, continuous pending spinner behavior, and 15-second success banner behavior.
- [ ] Validate remote/Docker graph URI handling for Cell and Agent Inspectors. Completed: viewer rewrites local, private, or container-internal MASE `/cells/...` and `/agents/...` resource URLs to the configured browser-facing server base before fetching. Remaining: manual validation in Docker and any non-local deployment shape.
- [ ] Define the server/scenario metadata contract for optimal routes parsed from creator `#Correct plan` comments, then replace [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) when the server exposes that data.
- [ ] Add completed-agent summaries derived from archived movement events without deleting transaction history.
- [ ] Revisit table virtualization only after manual runs show that repeated Load More creates a real rendering problem.

## Surprises & Discoveries

- Observation: The viewer has separate snapshot and live projection paths.
  Evidence: [src/routes/+page.server.ts](src/routes/+page.server.ts) fetches `/admin/maze`, [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) receives WebSocket events, and [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) applies runtime canvas updates through a listener path.

- Observation: Event-history offloading helps only when the live UI stops retaining and rendering unbounded arrays.
  Evidence: [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) keeps bounded hot arrays, while [src/lib/eventArchive.ts](src/lib/eventArchive.ts) stores accepted events in IndexedDB for later paging and export.

- Observation: Reset is both a completed server operation and a viewer state transition.
  Evidence: [../mase-server/README.md](../mase-server/README.md) documents the completed server reset endpoint and replay-buffer behavior, while [src/routes/+page.svelte](src/routes/+page.svelte) handles export/discard/cancel, page invalidation, inspector closure, and canvas remounting.

- Observation: Browser code cannot silently write exported logs into the repository or Docker container filesystem.
  Evidence: [src/lib/eventArchive.ts](src/lib/eventArchive.ts) uses browser-initiated file save where available and falls back to a normal browser download.

- Observation: Dockerized viewer deployments need separate internal and browser-facing server URLs.
  Evidence: [src/routes/+page.server.ts](src/routes/+page.server.ts) can fetch the initial snapshot through an internal URL, while the browser uses public HTTP and WebSocket URLs returned to [src/routes/+page.svelte](src/routes/+page.svelte).

- Observation: Inspector dereferencing needs browser-facing URLs even when RDF resource URIs are authored with local or container-internal hosts.
  Evidence: [src/routes/+page.svelte](src/routes/+page.svelte) resolves `/cells/...` and `/agents/...` resource URLs through the configured public server base when the resource host looks internal.

- Observation: Creator-generated TriG files already carry parseable optimal route comments.
  Evidence: [MazeTrigSerializer.java](../mase-creator/app/src/main/java/org/mase/creator/trig/MazeTrigSerializer.java) writes a `#Correct plan` section, and [MazeTrigParser.java](../mase-creator/app/src/main/java/org/mase/creator/trig/MazeTrigParser.java) parses that section back into `MazeModel.optimalRoute()`.

- Observation: Exit-cell cleanup should not delete diagnostic transaction history.
  Evidence: The viewer archives accepted events continuously in [src/lib/eventArchive.ts](src/lib/eventArchive.ts), so completed-agent cleanup can stay focused on canvas resources and summaries rather than destructive transaction-row pruning.

## Decision Log

- Decision: Keep the server and RDF repository authoritative.
  Rationale: The viewer is a projection and diagnostic surface. It should not invent simulation state or treat browser storage as the source of truth.

- Decision: Keep the runtime canvas update path independent from table rendering.
  Rationale: A slow table, large archive, or expensive search must not block agent markers, UI overlays, or cell background updates.

- Decision: Keep `Cell Updates` as the user-facing table label.
  Rationale: The label is clearer for users than exposing the `TRANSACTION` event implementation detail.

- Decision: Use NDJSON as the viewer log export format.
  Rationale: Transaction traces can contain nested rule diffs, request bodies, merge deltas, and future nested event fields. NDJSON preserves one complete JSON object per event without flattening or losing structure.

- Decision: Keep 50 recent agent movement rows and 50 recent Cell Updates rows in reactive memory.
  Rationale: The live UI needs a small hot window for immediate inspection. Older accepted events belong in IndexedDB and export paths.

- Decision: Reset Store must ask whether to export logs, discard logs, or cancel before resetting.
  Rationale: Reset starts a new experiment run. The viewer should not silently destroy diagnostic logs, and it should not keep stale canvas, inspector, or hot-log state attached to the fresh run after the user chooses export or discard.

- Decision: Clear Tables must not delete archived logs.
  Rationale: Users need a quick way to reduce visible table noise without losing experiment evidence. Log deletion is reserved for Reset Store with Discard Logs, or future explicit archive-management controls.

- Decision: Archived table paging loads 100 rows per page and switches to scroll continuation after the first click.
  Rationale: The first click is a deliberate opt-in to cold log browsing. After that, scrolling to the table bottom can append older archived rows without repeated button clicks.

- Decision: Export keeps all event types selected by default but lets the user deselect noisy types such as `UI_UPSERT`.
  Rationale: Full export remains lossless by default, while targeted export can produce agent-focused NDJSON without UI overlay churn.

- Decision: Use split runtime URL configuration for viewer-to-server traffic.
  Rationale: In Docker, the SvelteKit node process can reach `mase-server` by service name, while the browser usually needs `localhost` or another host-reachable URL.

- Decision: Do not prune archived transaction rows when an agent reaches the exit.
  Rationale: Transaction events can describe shared environment or cell updates that remain relevant after one agent completes.

- Decision: Treat optimal route extraction as server/scenario metadata work, not frontend file parsing.
  Rationale: The creator's `#Correct plan` comments are parseable, but the browser should receive route metadata from an admin or scenario endpoint rather than reading TriG files directly.

## Outcomes & Retrospective

The viewer baseline now includes bounded hot arrays, IndexedDB archival, NDJSON export with event-type selection, archive counts, search-aware archived Load More, loaded/available row metadata, archived-row labels, runtime server URL configuration, reset invalidation, export/reset pending spinners, and 15-second success confirmations.

The main remaining gaps are richer archive browsing controls, focused checks around export/reset behavior, Docker/remote validation for inspector URL rewriting, server-owned optimal route metadata, and completed-agent summaries. Table virtualization remains conditional; it should be added only if large visible tables become a measured problem after repeated archive loading.

## Context and Orientation

The `mase-viewer` module is a SvelteKit application using Svelte 5, Vite, Tailwind, and Konva. The server module is [mase-server](../mase-server/), which owns the RDF store and WebSocket event stream. The viewer starts from a server snapshot and then applies compact runtime events.

Important files are:

- [README.md](README.md) documents user-facing setup and behavior.
- [package.json](package.json) defines the SvelteKit, Svelte 5, Tailwind, Vite, and Konva dependencies.
- [src/routes/+page.server.ts](src/routes/+page.server.ts) loads the initial admin snapshot through the configured internal MASE server URL.
- [src/routes/+page.svelte](src/routes/+page.svelte) composes the main page, inspectors, log filters, export controls, reset dialog, and live event tables.
- [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) owns WebSocket connectivity, event buffering, bounded live arrays, replay de-duplication, reset coordination, and archive coordination.
- [src/lib/eventArchive.ts](src/lib/eventArchive.ts) owns IndexedDB storage, archive run ids, NDJSON export, browser file save, and download fallback.
- [src/lib/mazeServerConfig.ts](src/lib/mazeServerConfig.ts) resolves internal and browser-facing MASE server URLs for local and Dockerized runs.
- [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) renders the maze, runtime UI overlay, agent markers, tooltips, and optimal route overlay.
- [src/lib/components/AgentEventLog.svelte](src/lib/components/AgentEventLog.svelte) renders the Agent Movements table.
- [src/lib/components/CellEventLog.svelte](src/lib/components/CellEventLog.svelte) renders the Cell Updates table, which is backed by transaction trace events.
- [src/lib/types.ts](src/lib/types.ts) defines the admin snapshot and maze layout types shared by the route and canvas.
- [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts) contains frontend-only optimal route overlays keyed by scenario name.

The startup flow is:

1. [src/routes/+page.server.ts](src/routes/+page.server.ts) fetches `/admin/maze` from the configured MASE server.
2. The page receives a `MazeAdminSnapshot`, including the maze layout, current UI snapshot, and optional scenario name.
3. [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) draws maze cells, walls, labels, UI snapshot, and optional optimal route overlay.
4. [src/routes/+page.svelte](src/routes/+page.svelte) calls `mazeState.connect()` on mount.
5. [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) opens a WebSocket to the configured browser-facing WebSocket URL.
6. Incoming WebSocket events are validated, timestamped in the browser, queued, archived, and flushed once per animation frame.
7. Runtime canvas events are emitted to registered listeners. The canvas updates directly from this listener path instead of depending on table arrays.

The server event types currently understood by the viewer are:

- `AGENT_MOVED`: append to movement logs and update the agent marker layer.
- `UI_UPSERT`: update the UI overlay layer and optionally export as a stored event.
- `UI_DELETE`: remove a UI overlay node and optionally export as a stored event.
- `TRANSACTION`: append to the Cell Updates data path.

The current log model has two layers. The hot layer is the latest 50 rows each for Agent Movements and Cell Updates, held in Svelte state for fast live diagnosis. The cold layer is an IndexedDB archive written by [src/lib/eventArchive.ts](src/lib/eventArchive.ts), which supports archive counts, 100-row paging, search-aware loading, visible loaded/available metadata, archived-row labels, clearing, and NDJSON export by selected event type.

Reset Store is explicit. Cancel does nothing. Export logs writes selected NDJSON event types before reset. Discard logs skips export. After Export or Discard, the viewer calls the configured reset endpoint, clears hot and IndexedDB logs, starts a new archive run, invalidates page data, remounts the canvas, closes selected inspectors, clears the filter, and shows a temporary success or failure message with a close button.

Clear Tables is separate from log deletion. It clears visible rows and leaves the IndexedDB archive intact. Export Logs is also separate from reset and exports selected event types without changing the server store.

Known issues and constraints are:

- Cell and Agent Inspector fetches use server-authored graph URIs directly. Non-local deployments still need graph URIs that the browser can reach.
- Browser timestamps are assigned on receipt, not by the server.
- Runtime UI event storage should stay bounded or intentionally archived; rendering uses the current UI node map.
- Large full transaction traces can be heavy even before rows are expanded.
- Optimal route data is still static frontend data and duplicated across scenario keys.

## Plan of Work

Improve archive browsing first. Extend [src/routes/+page.svelte](src/routes/+page.svelte) and the event table components so users can see what is hot, what has been loaded from the archive, how many records are available by type, and what run/session they are browsing. Keep the current IndexedDB API in [src/lib/eventArchive.ts](src/lib/eventArchive.ts) unless the UI need exposes a missing query shape.

Add focused tests or checks around the retention contract. The highest-value checks are that event-type filtered export writes only selected event types, Clear Tables preserves IndexedDB data, Reset Store export/discard clears the expected state, and the 15-second banner auto-closes while remaining manually dismissible.

Validate inspector URL handling for remote and Docker deployments. The viewer rewrites local, private, or container-internal MASE cell and agent resource URLs to the configured browser-facing server base before fetching. If future deployments expose non-local canonical graph URIs that still need rewriting, prefer a server-emitted browser-facing dereference URL over broader client-side guessing.

Move optimal route ownership toward the server scenario metadata path. The creator already writes and parses `#Correct plan` comments in TriG files. The viewer should consume this data from a server-admin snapshot or scenario metadata endpoint, then retire [src/lib/optimalRoutes.ts](src/lib/optimalRoutes.ts).

Add completed-agent summaries from archived movement events. The summary can include agent id, start cell, exit cell, move count, and final timestamp. It should not delete archived movement or transaction records.

Keep table virtualization as a measured follow-up. Add it only if repeated Load More interactions create large visible tables that noticeably hurt rendering or scrolling.

Keep RDF-backed agent location rendering in the backlog. If implemented, normalize authoritative `maze:contains` changes into the same compact movement command shape used by the current canvas listener, so table history never becomes the source of truth for marker placement.

## Concrete Steps

For documentation-only changes, validate with:

    git diff --check -- mase-viewer/MASE-VIEWER.md

For viewer implementation work, run these commands from `mase-viewer` when the user wants automated validation:

    npm run check
    npm run lint

Expected result is that Svelte type checking and linting finish without errors. The user currently handles hot-reload manual testing, so do not run npm commands unless requested.

Manual validation for the current reset/export behavior requires a running MASE server. Open the viewer, generate movement and Cell Update events, export selected logs, exercise Reset Store with Cancel, Export logs, and Discard logs, then verify that old agent markers, UI overlays, inspectors, hot rows, and filters do not leak into the fresh run after reset.

## Validation and Acceptance

For archive browsing, acceptance is that users can distinguish hot rows from archived rows, load older rows in 100-row batches, continue loading by scrolling after the first opt-in click, see counts that explain what is available, and export selected event types as valid NDJSON.

For reset behavior, acceptance is that Reset Store opens Export logs / Discard logs / Cancel. Cancel performs no reset. Export writes NDJSON for selected event types or reports cancellation/failure without resetting. Export and Discard call `POST /admin/maze/reset`; on success, hot logs, IndexedDB logs, selected inspectors, filter text, and canvas runtime state are cleared, page data is invalidated, and the success message remains visible for 15 seconds unless manually closed.

For canvas behavior, acceptance is that maze cells, walls, labels, UI overlays, cell backgrounds, agent markers, tooltips, and optimal route overlays still render. UI upsert/delete events update the correct Konva layer, path rasterization fallback handles unsupported or invalid path data, and cell and agent inspectors still open from double-click interactions.

For deployment configuration, acceptance is that local npm runs and Dockerized runs can target the MASE server without source edits. The SvelteKit server-side load path uses an internal HTTP URL when configured, while browser-side reset and WebSocket connections use public browser-reachable URLs.

## Idempotence and Recovery

Archive writes should be safe to attempt repeatedly. If IndexedDB fails because of quota, private browsing restrictions, or browser limitations, live canvas updates should continue and the UI should surface the persistence failure without breaking runtime rendering.

Reset invalidation must be idempotent. Receiving a reset notification twice, or clicking reset twice while an operation is pending, must not duplicate archive sessions, corrupt the canvas, or leave the page between two snapshots. The reset button should stay disabled while export or reset is pending.

Do not make destructive archive decisions implicit. Reset deletion requires the Discard Logs choice. Clear Tables must not clear IndexedDB. Export Logs must not reset the server store.

If a reset POST succeeds but the snapshot refetch fails, the viewer should show a clear disconnected or stale-state indication and allow retry.

## Artifacts and Notes

### Log Storage Model

The viewer uses a hot/cold model:

- Hot `agentEvents`: latest 50 movement events.
- Hot `transactionEvents`: latest 50 Cell Update events.
- Cold archive: IndexedDB records for accepted event types, tagged by archive run id and exported as NDJSON.
- Default table view: hot rows.
- Archived table access: 100-row pages loaded from IndexedDB, with scroll continuation after the user first clicks Load More.
- Export: selected event types, all selected by default.

[log/README.md](log/README.md) keeps a repository-local `log` folder available for users who want to choose `mase-viewer/log` in browsers that support file save. Browser save dialogs cannot be forced to open an arbitrary repository path, but [src/lib/eventArchive.ts](src/lib/eventArchive.ts) uses a stable picker id so supported browsers can remember the last selected folder. Browsers without the File System Access API use normal downloads. Dockerized deployments use the same browser-side export path; no container write access is required.

### Future Backlog

Near-term work is the unchecked `Progress` list: archive browsing UI, focused export/reset/config checks, inspector URL strategy, server-owned optimal route metadata, completed-agent summaries, and conditional virtualization.

Medium-term work includes RDF-backed agent location rendering investigation and possibly storing transaction summary rows separately from full transaction details if full traces remain heavy.

Long-term work includes a server-supported event history or trace endpoint if long-running audit trails become core, plus performance benchmarks for high-volume movement and transaction streams.

### Workpackage: Agent Location Rendering from Cell Containment Triples

Goal: determine whether live agent markers can be rendered from authoritative `maze:contains` triples in cell graphs instead of relying only on frontend projection from `AGENT_MOVED` events.

Review the server-side movement source of truth in [PostHandler.java](../mase-server/src/main/java/org/maze/application/PostHandler.java), [move.rq](../mase-server/src/main/resources/rules/Global/move.rq), and [move_start.rq](../mase-server/src/main/resources/rules/Global/move_start.rq).

Determine how the viewer could receive containment state: admin snapshot fields, a SPARQL query, transaction trace deltas, a new compact WebSocket event, or a server-derived `AGENT_MOVED` event produced from RDF updates.

Compare the RDF-backed path with the current canvas path in [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) and [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte), especially for latency, event ordering, reconnect replay, trace mode `off`, and exit-cell handling.

Prototype a normalization layer that turns containment changes into the same canvas movement command shape used by the current `AGENT_MOVED` listener so rendering stays fast and table history does not become the canvas source of truth.

Decide whether `AGENT_MOVED` should remain the compact rendering event, become derived from RDF containment changes on the server, or be replaced by an RDF-backed location event.

### Guidance for Future Codex Sessions

Read [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) before changing live event behavior. It is the central coordination point.

Read [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) before changing runtime visuals. Canvas state is intentionally separate from table state.

Keep the runtime canvas listener path fast and bounded. Avoid making table history the source of truth for canvas rendering.

When changing log retention, update Clear Tables, Reset Store log deletion behavior, export behavior, and this document. When adding persistent storage, handle quota failures and private browsing failures gracefully.

Do not assume `TRANSACTION` events are enabled. The server can run trace mode `off`, `summary`, or `full`. Do not assume full transaction detail exists, because summary mode intentionally omits request bodies and triple-level diffs.

Before changing reset controls or automatic reset handling, read the completed server reset contract in [../mase-server/README.md](../mase-server/README.md), [MazeResetService.java](../mase-server/src/main/java/org/maze/application/MazeResetService.java), and [MazeBroadcaster.java](../mase-server/src/main/java/org/maze/api/websocket/MazeBroadcaster.java). Viewer reset behavior depends on the server endpoint, stale replay cleanup, and reset notification contract.

## Interfaces and Dependencies

The viewer depends on `GET /admin/maze` returning a `MazeAdminSnapshot` with layout, UI snapshot, and optional scenario name. The corresponding TypeScript type is defined in [src/lib/types.ts](src/lib/types.ts). The SvelteKit server-side fetch uses `MASE_SERVER_INTERNAL_HTTP_URL` when present.

The viewer depends on browser-facing WebSocket events from `PUBLIC_MASE_SERVER_WS_URL` or its derived default. Existing event types consumed by [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) are `AGENT_MOVED`, `UI_UPSERT`, `UI_DELETE`, and `TRANSACTION`.

The reset UI depends on the completed server admin reset contract. `POST /admin/maze/reset` returns a fresh admin snapshot, the server clears stale WebSocket replay messages, and the server broadcasts an `ADMIN_RESET` notification. The viewer should use that endpoint only after the user chooses Export logs or Discard logs.

[src/lib/eventArchive.ts](src/lib/eventArchive.ts) exposes stable archive operations for appending events, paging recent or older events by type, counting events, clearing the archive, and exporting NDJSON. The current implementation uses IndexedDB, but the interface should not prevent later server-backed storage.

[../docker-compose.ccrs.yml](../docker-compose.ccrs.yml) is the repository-level Docker entry point that must remain compatible with the viewer URL configuration.
