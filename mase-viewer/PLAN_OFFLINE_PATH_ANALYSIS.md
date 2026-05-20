# PLAN_OFFLINE_PATH_ANALYSIS: Visualize archived and pasted agent paths

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

No repository-level `PLANS.md` or `.agent/PLANS.md` guide exists at creation time. This plan follows the local `PLAN_<SCOPE>.md` convention described by [../AGENTS.md](../AGENTS.md) and complements the broader viewer plan in [MASE-VIEWER.md](MASE-VIEWER.md).

## Purpose / Big Picture

After this work, a user can open the MASE viewer after an experiment and inspect the exact path an agent took on top of the maze canvas. The path can come from archived `AGENT_MOVED` records already stored in the viewer's IndexedDB archive, or from a pasted cell sequence produced by external experiment tooling such as CCRS-BDI reports.

The feature is for offline analysis. It must not change the MASE server RDF store, replay live movement, or mutate current agent marker state. A user should be able to paste a path such as `/cells/1/1`, `/cells/1/2`, `/cells/1/1`, render it, and clearly see the order, turns, and backtracking rather than a single ambiguous line.

## Progress

- [x] (2026-05-20) Created this MASE viewer implementation plan from the CCRS-BDI experiment-report requirements and the current viewer architecture.
- [x] (2026-05-20) Added a frontend path-analysis state model and parsing helpers.
- [x] (2026-05-20) Added archive queries for selecting movement paths by run id and agent id.
- [x] (2026-05-20) Added the Path Analysis UI panel in the viewer.
- [x] (2026-05-20) Added the Konva path-analysis overlay layer in the canvas.
- [x] (2026-05-20) Static validation passed with `npm run check`.
- [ ] Validate manually in a browser with pasted backtracking sequences and archived `AGENT_MOVED` records.

## Surprises & Discoveries

- Observation: The viewer already has the right boundary for this feature.
  Evidence: [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) emits compact runtime canvas events, [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) renders independent Konva layers, and [src/lib/eventArchive.ts](src/lib/eventArchive.ts) stores archived movement events in IndexedDB.

- Observation: The current archive API can page movement events by type, but it does not yet expose run-and-agent path selection.
  Evidence: [src/lib/eventArchive.ts](src/lib/eventArchive.ts) has `getEventsByType`, `countByType`, and `exportNdjson`, while the current IndexedDB indexes include `runId`, `type`, `timestamp`, `agent`, `graph`, and `transactionId`.

- Observation: A plain polyline is not enough for experiment analysis.
  Evidence: A path like A -> B -> A or A -> B -> A -> B collapses into one line if only cell centers are connected. The user cannot tell where the agent turned, which direction happened first, or how many times the edge was traversed.

- Observation: CCRS-BDI reports can produce paste-ready movement sequences.
  Evidence: The experiment report pipeline can emit `path-analysis-inputs/*.cells.txt` files containing one normalized `/cells/x/y` path per line. The viewer pasted-sequence input should accept that format without requiring metadata.

- Observation: The project lint command is currently blocked before ESLint completes.
  Evidence: `npm run lint` fails in Prettier with `TypeError: getVisitorKeys is not a function or its return value is not iterable` on Svelte files and also reports pre-existing formatting warnings. A targeted ESLint run on touched files only leaves existing `MazeCanvas.svelte` debt such as mutable `Map` usage, explicit `any`, and unused-expression dependency markers.

## Decision Log

- Decision: Implement this as a frontend-only overlay.
  Rationale: The path-analysis data is diagnostic evidence, not simulation state. The viewer already owns diagnostic overlays, archived logs, and canvas projection, while the MASE server remains the authoritative RDF store.
  Date/Author: 2026-05-20 / Codex

- Decision: Support two path sources in the first implementation: archived movement events and pasted sequences.
  Rationale: Archived mode is convenient when the run happened in the same browser profile. Pasted mode is necessary for external reports, headless experiment runs, shared result artifacts, and cases where the viewer archive was cleared.
  Date/Author: 2026-05-20 / Codex

- Decision: Normalize pasted cells to `/cells/x/y` when possible.
  Rationale: Full resource URIs can differ between localhost, container, and private-network hosts. The path segment is stable and matches how the viewer can locate cells in the current maze layout.
  Date/Author: 2026-05-20 / Codex

- Decision: Use numbered visit markers plus directed, offset transition arrows instead of a single route line.
  Rationale: Numbered markers preserve temporal order, directed arrows show movement direction, and offset lanes make opposite or repeated traversals between the same two cells visually distinct.
  Date/Author: 2026-05-20 / Codex

- Decision: Keep the overlay independent of live agent markers, optimal route, and server-provided UI overlays.
  Rationale: Offline analysis should be toggled or cleared without affecting live rendering, event tables, exported logs, or server state.
  Date/Author: 2026-05-20 / Codex

## Outcomes & Retrospective

The first implementation is in place. The viewer now has paste parsing, archive-backed movement path selection, a Path Analysis panel, and a dedicated Konva overlay layer with numbered visit markers, directed lane-offset arrows, a current-step slider, show/hide, clear, and fit-to-path controls. Reset clears the active overlay as part of the viewer state reset.

Validation completed on 2026-05-20:

- `npm run check` passed with 0 errors and 0 warnings.
- `npm run lint` did not complete because Prettier fails on Svelte files with `getVisitorKeys is not a function or its return value is not iterable`; this appears to be existing formatter/tooling state rather than a path-analysis type error.

Manual browser validation with live archived `AGENT_MOVED` records remains open.

## Context and Orientation

The `mase-viewer` project is a SvelteKit application using Svelte 5, Tailwind, and Konva. Konva is the canvas drawing library used by [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte). The viewer receives the maze layout from the MASE server through [src/routes/+page.server.ts](src/routes/+page.server.ts), then [src/routes/+page.svelte](src/routes/+page.svelte) mounts [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte) and connects [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts) to the WebSocket stream.

The relevant current files are:

- [src/routes/+page.svelte](src/routes/+page.svelte): main viewer page, header controls, reset/export dialogs, event tables, and canvas mount.
- [src/lib/components/MazeCanvas.svelte](src/lib/components/MazeCanvas.svelte): Konva stage and layers for maze cells, server-provided UI overlays, live agent markers, tooltips, and optimal route rendering.
- [src/lib/mazeState.svelte.ts](src/lib/mazeState.svelte.ts): WebSocket connection, event buffering, hot event arrays, IndexedDB archive coordination, reset coordination, and runtime canvas event emission.
- [src/lib/eventArchive.ts](src/lib/eventArchive.ts): browser IndexedDB archive for accepted event records, including `AGENT_MOVED`.
- [src/lib/routeOverlayStore.ts](src/lib/routeOverlayStore.ts): small existing store for showing or hiding the static optimal route overlay.
- [src/lib/types.ts](src/lib/types.ts): maze layout types. Each cell has `id`, `x`, `y`, `label`, `connections`, `items`, and optional `lock` data.

A movement event has type `AGENT_MOVED`, an `agent` resource URI, and a `cell` resource URI. The viewer archive wraps events with an archive `runId`, `archiveId`, and timestamps. Pasted path input should not require the archive wrapper. It should accept newline, comma, or whitespace separated values such as:

    /cells/1/1
    /cells/1/2
    http://127.0.1.1:8080/cells/1/1

The overlay should resolve each value to a current maze cell. For full URIs, extract `/cells/x/y` and match either exact cell id or cell id suffix. If a pasted cell cannot be resolved, keep the row in a validation list and do not draw that step. The UI should show a concise error count and avoid crashing the canvas.

## Milestones

Milestone 1 adds path parsing and state without canvas drawing. At the end, the viewer can parse pasted paths into ordered steps, report invalid cells, and hold the selected path in a small store. Validate this with a pasted sequence containing repeated cells and at least one invalid cell.

Milestone 2 adds archive-backed selection. At the end, the viewer can list movement sources from IndexedDB by archive run id and agent id, then load an ordered path for one agent. Validate this by running a small scenario, creating movement events, opening Path Analysis, selecting the archived agent, and seeing a step count and first/last cells.

Milestone 3 adds canvas rendering. At the end, the overlay draws visit markers and directed arrows on a dedicated Konva layer. Validate this with A -> B -> A -> B and confirm that opposite movements are visually separated.

Milestone 4 polishes interaction and reset behavior. At the end, the user can toggle, clear, fit to path, and use a step slider without affecting live markers, event tables, reset/export dialogs, or optimal-route visibility. Validate this manually in a running viewer and with `npm run check`.

## Plan of Work

Create a path-analysis store in `src/lib/pathAnalysisOverlayStore.ts`. Define plain TypeScript types for parsed steps and overlay state. The store should track whether the overlay is visible, the source label, the ordered steps, invalid input entries, the selected/current step, and any display options such as dimming future steps. Keep it independent from `mazeState` so archived logs and live WebSocket rendering remain separate.

Add parsing helpers in the same file or in `src/lib/pathAnalysis.ts`. The parser should accept pasted text and split on newlines, commas, semicolons, and whitespace. It should normalize each token by extracting `/cells/x/y` from full URIs when possible. It should resolve a normalized token against the current `MazeLayout` by exact cell id first, then by suffix match. The output should be an ordered list of path steps with step number, original token, normalized cell id, resolved cell id, and optional timestamp/agent metadata when the source is archived.

Extend `src/lib/eventArchive.ts` with movement-specific reads. Add a method that summarizes archived `AGENT_MOVED` records by run id and agent id, and another method that returns the ordered movement path for one run id and agent id. Use existing indexes first. If performance is acceptable, do not change the IndexedDB version. If the scan is visibly slow for repeated experiment logs, bump `DB_VERSION` and add a compound index such as `[runId, type, agent, archiveId]`.

Add a new component `src/lib/components/PathAnalysisPanel.svelte`. This panel should provide a source mode control for archived logs versus pasted sequence, selectors for run id and agent id when archive mode is active, a textarea for pasted input, and buttons for render, clear, fit to path, and show/hide. Keep visible text concise and diagnostic. Do not add a tutorial block inside the app.

Update `src/routes/+page.svelte` to add a `Path Analysis` button near `Reset Store` and `Show Optimal Route`. The page should mount `PathAnalysisPanel` when opened, pass the current maze layout into the panel, and keep existing reset/export/demo-agent flows unchanged. Reset should clear the active overlay because the canvas remount represents a fresh scenario state.

Update `src/lib/components/MazeCanvas.svelte` to add a separate Konva layer for path analysis. A sensible ordering is maze layer, UI layer, path-analysis layer, agent layer, tooltip layer. This keeps analysis visible above cell backgrounds and server UI while preserving live agent markers and tooltips. Subscribe to the path-analysis store or receive overlay props from the page; choose the path that causes the fewest reactive redraws.

Implement the drawing algorithm in `MazeCanvas.svelte` or a helper module. Compute each cell center from `cell.x`, `cell.y`, `CELL_SIZE`, and `PADDING`. For repeated visits to the same cell, place small numbered markers around the center using a ring or spiral offset so step numbers remain separable. For each consecutive pair of valid steps, draw a Konva Arrow from the marker position of the source step to the marker position of the target step. For opposing movements on the same edge, place them on opposite perpendicular offsets. For repeated same-direction movements, add additional parallel offsets up to a small cap and show a repeat badge when crowded. Use a sequential color ramp or opacity fade so early and late movement are distinguishable.

Add hover or selection detail only after the basic overlay works. The useful details are step number, agent id, source cell, target cell, timestamp, and whether the step came from archive or pasted input. Reuse the tooltip layer if that keeps behavior simple, or use a small fixed panel in `PathAnalysisPanel` for selected-step details.

## Concrete Steps

Work from the MASE viewer directory:

    cd S:\dev\ma\mase\mase-viewer

Create or update these files:

    src/lib/pathAnalysisOverlayStore.ts
    src/lib/pathAnalysis.ts
    src/lib/components/PathAnalysisPanel.svelte
    src/lib/components/MazeCanvas.svelte
    src/routes/+page.svelte
    src/lib/eventArchive.ts

After Milestone 1, run:

    npm run check

Expected result: Svelte type checking completes without new errors. If existing repository issues appear, record the exact failure in this plan before continuing.

After Milestone 2, run the viewer, generate or load archived movement events, and verify that Path Analysis can list at least one run/agent pair from the current browser archive. If no archived records exist, use pasted mode for Milestone 3 and come back to archive mode after creating movement events.

After Milestone 3, paste this sequence into the panel:

    /cells/1/1
    /cells/1/2
    /cells/1/1
    /cells/1/2
    /cells/2/2

Expected result: the overlay shows numbered visit markers, A -> B and B -> A on visibly different lanes, and a final arrow to `/cells/2/2`. Clearing the overlay removes only the path-analysis layer.

After Milestone 4, run:

    npm run check

If lint is known to pass in the current repository state, also run:

    npm run lint

If lint fails due to existing unrelated formatting/tooling issues, record that in `Outcomes & Retrospective` and rely on `npm run check` plus manual validation for this feature.

## Validation and Acceptance

The feature is accepted when a user can open the viewer, click `Path Analysis`, paste a newline-separated path, click render, and see an overlay on the maze. The overlay must make backtracking legible: A -> B -> A must show two directed arrows in opposite lanes and numbered markers that preserve order.

The archive path is accepted when a user can run or replay a scenario that creates `AGENT_MOVED` records, open `Path Analysis`, select an archive run and agent, render that path, and see the same overlay without manually pasting cells.

The external report path is accepted when a user can open a `.cells.txt` file produced by CCRS-BDI experiment reports, paste the full file content into Path Analysis, and render it without editing hostnames or deleting metadata. The `.cells.txt` file should contain only cell paths, one per line.

The overlay isolation is accepted when live agent markers still update, optimal route toggling still works, reset/export dialogs still work, and clearing the path overlay does not clear event tables or IndexedDB. Reset Store with discard or export may clear the active overlay because it represents a new scenario run.

Canvas quality is accepted when dense back-and-forth movement between two adjacent cells remains readable at normal zoom. If more than a few repeated transitions overlap, show a count badge or collapse excess lane offsets rather than letting labels and arrows become unreadable.

## Idempotence and Recovery

All implementation steps are additive and safe to repeat. The new overlay store can be reset to an empty path at any time. If pasted input contains invalid cells, the viewer should report the invalid count and draw the valid contiguous segments it can resolve, or decline to render with a clear error if no valid cells exist.

Do not delete or rewrite IndexedDB archive records during path analysis. The archive mode should read archived movement events only. Clear overlay must remove Konva path-analysis nodes and reset path-analysis state, not event logs.

If an IndexedDB read fails because browser storage is unavailable or blocked, keep pasted mode usable. Surface the archive error in the panel and avoid breaking the canvas or live WebSocket connection.

If path rendering throws due to unexpected geometry or a missing cell, catch the error, clear the partial overlay, and keep the viewer usable. The user should be able to correct the input and render again without reloading the page.

## Artifacts and Notes

The expected pasted path format is intentionally simple:

    /cells/1/1
    /cells/1/2
    /cells/1/1

The parser should also accept full cell URIs:

    http://127.0.1.1:8080/cells/1/1
    http://localhost:8080/cells/1/2

Both examples should normalize to path-like cell ids that the current maze layout can resolve.

Suggested overlay primitives:

    Visit marker: small circle or rounded label with step number inside the destination cell.
    Transition arrow: directed Konva Arrow between two visit marker points.
    Opposite lane: perpendicular offset based on sorted unordered cell pair and direction.
    Repeated lane: additional offset for repeated same-direction traversal, capped to avoid clutter.
    Current step: emphasized marker and arrow selected by a slider.

Do not draw the path through cells that are missing from the current maze layout. Missing cells usually mean the user pasted a path from a different scenario or dataset.

## Interfaces and Dependencies

In `src/lib/pathAnalysis.ts`, define types close to:

    export interface PathAnalysisStep {
        step: number;
        original: string;
        normalizedCell: string;
        cellId: string;
        agent?: string | null;
        timestamp?: number | null;
    }

    export interface PathAnalysisParseResult {
        steps: PathAnalysisStep[];
        invalid: Array<{ index: number; value: string; reason: string }>;
    }

    export function parsePathAnalysisInput(input: string, maze: MazeLayout): PathAnalysisParseResult;

In `src/lib/pathAnalysisOverlayStore.ts`, expose a Svelte store or equivalent Svelte 5 state facade with operations close to:

    setPath(steps, sourceLabel)
    clearPath()
    setVisible(visible)
    setCurrentStep(step)

In `src/lib/eventArchive.ts`, add movement archive methods close to:

    listMovementPathSources(): Promise<Array<{ runId: string; agent: string; count: number; firstTimestamp?: number; lastTimestamp?: number }>>
    getAgentMovementPath(runId: string, agent: string): Promise<AgentMovedEvent[]>

In `src/lib/components/MazeCanvas.svelte`, keep rendering bounded. Rebuild the path-analysis layer only when the path-analysis state changes, not on every WebSocket event. Existing runtime canvas events should continue to use the current fast listener path.

Revision note 2026-05-20 / Codex: Initial focused ExecPlan created for the MASE viewer offline path-analysis overlay requested from the CCRS-BDI experiment workflow.

Revision note 2026-05-20 / Codex: Implemented the offline path-analysis overlay in the viewer, including parser/store modules, IndexedDB movement path queries, panel UI, Konva rendering, reset clearing, and static validation notes.
