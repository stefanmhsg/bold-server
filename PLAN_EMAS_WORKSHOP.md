# PLAN_EMAS_WORKSHOP: Prepare the EMAS workshop guide, pitch slide, and controlled demo

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

No `PLANS.md` or `.agent/PLANS.md` guide is currently checked into this repository. This document follows the local `PLAN_<SCOPE>.md` structure from the Codex `exec-plan-guidance` skill.

## Purpose / Big Picture

MASE will be presented at the EMAS Workshop. The repository needs workshop-facing material that lets an attendee or future presenter understand the motivation, start the current server and viewer, run the existing scenario, observe agent requests and server-side effects, and recover from common demo failures without relying on prior chat context.

The visible deliverables are a root-level `Workshop.md`, a one-slide concept for a 1-minute opening pitch, and, a presenter-controlled demo agent exposed through the MASE viewer. The desired observable result is that a presenter can start the CCRS scenario, open the viewer, step through an agent action, show the HTTP request and response, and point to the existing Cell Updates table to explain the RDF state changes caused by that action.

## Progress

- [x] (2026-05-19 18:43Z) Inspected the current repository-level docs, `mase-server/PLAN_MASE_SERVER.md`, `mase-server/AGENTS.md`, `mase-viewer/MASE-VIEWER.md`, the current CCRS docs, viewer event tables, and the scenario-owned Java agents.
- [x] (2026-05-19 18:43Z) Located the readable public EMAS paper on OpenReview and compared its claims with the current repository baseline.
- [x] (2026-05-19 18:43Z) Created this root-level plan as the coordination artifact for the workshop preparation.
- [ ] Drafted `Workshop.md` as a self-contained workshop script and participant guide.
- [ ] Created `WORKSHOP_PITCH_SLIDE.md` with the 1-minute pitch slide concept
- [x] (2026-05-19 19:00Z) Added root `AGENTS.md` guidance so future sessions discover `PLAN_EMAS_WORKSHOP.md`.
- [x] (2026-05-19 19:20Z) Decided the presenter-controlled demo feature is required because the existing viewer did not expose one request/response transcript per agent step.
- [x] (2026-05-19 19:20Z) Implemented the presenter-controlled demo agent in the server and viewer.
- [x] (2026-05-19 19:05Z) Validated server compilation with `.\gradlew.bat compileJava`.
- [x] (2026-05-19 19:10Z) Validated viewer typing with `npm run check`.
- [x] (2026-05-19 19:25Z) Validated the server test suite with `.\gradlew.bat test`.
- [x] (2026-05-19 19:17Z) Reworked `Workshop.md` from prose-heavy guide into a chaptered checklist runbook.
- [x] (2026-05-19 19:30Z) Tightened `Workshop.md` into a terse reference checklist by removing presenter narration and generic logistics.
- [x] (2026-05-19 19:40Z) Changed the viewer Demo Agent UI from an always-visible panel to a button-opened inline view with an `x` close control.
- [x] (2026-05-19 19:45Z) Reverted the modal overlay approach because it blocked simultaneous canvas and table inspection.
- [x] (2026-05-19 20:05Z) Reframed `Workshop.md` as a participant-friendly live session guide with Docker-first SmallMaze startup, numbered flow, paired PowerShell/Bash command tables, and no pitch-slide material.
- [x] (2026-05-19 20:30Z) Added a dedicated `emas-workshop` scenario package copied from CCRS, defaulting to `CcrsMazeV2.trig`, plus `docker-compose.emas-workshop.yml` for the workshop showcase.
- [x] (2026-05-19 20:35Z) Validated `docker-compose.emas-workshop.yml` with `docker compose --profile viewer config` and validated server packaging with `./gradlew installDist`.
- [x] (2026-05-19 20:45Z) Removed the background admin URL from `Workshop.md`, cleaned unused Turtle prefixes in the Demo Agent transcript view, and fixed the SmallMaze key UI projection rule.
- [x] (2026-05-19 20:50Z) Validated the Demo Agent panel with `npm run check` and forced the focused SmallMaze scenario startup test with `./gradlew test --tests org.maze.infrastructure.scenario.ScenarioPackageStartupTest --rerun-tasks`.
- [ ] Validate the full workshop path with a fresh run of server, viewer, EMAS workshop CCRS scenario reset, and at least one demo-agent step.

## Surprises & Discoveries

- Observation: The attached local PDF exists at `C:/Users/stefa/Downloads/EMAS_2026_Demo_Paper (13).pdf`, but local PDF text extraction tools such as `pdftotext`, `qpdf`, `mutool`, Python `pypdf`, `PyPDF2`, `pdfplumber`, and PyMuPDF are not installed in this environment.
  Evidence: Command checks found the file but no usable local extractor. The readable public copy is available on OpenReview at `https://openreview.net/forum?id=lNF8HCzvN6`.

- Observation: The paper was published on 2026-03-30 and last modified on 2026-04-29, while the working tree contains later repository changes through 2026-05-19.
  Evidence: The OpenReview page lists the publication and modification dates. The repo now has scenario-folder startup, admin reset, viewer log archive/export, and CCRS Docker composition that are not fully reflected in the paper narrative.

- Observation: The paper describes MASE as a lightweight Linked Data maze environment that customizes dynamics through SPARQL, enforces embodiment and interaction constraints, attributes state changes to requests and rules, and visualizes changes through an event-driven front end.
  Evidence: The OpenReview paper abstract and Section 3 describe those points. Current repository docs and code implement them through scenario-local rules, authorized GET/POST, transaction traces, WebSocket events, and the Svelte viewer.

- Observation: The user mentioned `mase:green`, while the current repository uses `maze:green` for the green route signifier.
  Evidence: `mase-server/docs/maze.ttl`, `mase-server/scenarios/ccrs/data/CcrsMazeV1.trig`, `mase-server/scenarios/ccrs/rules/scenario/ccrs.rq`, and `mase-server/scenarios/ccrs/rules/global/ui.rq` use `maze:green`.

- Observation: The current CCRS agent is not a clean demo DFS agent.
  Evidence: `mase-server/scenarios/ccrs/agents/src/main/java/org/maze/scenarios/ccrs/agents/CcrsAgent.java` launches many configured agents, uses hardcoded guided coordinate lists, has key shortcuts for some locks, and runs without presenter step control.

- Observation: The viewer already contains most of the observability needed for the demo, but not a controlled agent-step transcript.
  Evidence: `mase-viewer/src/lib/components/AgentEventLog.svelte` shows movement events. `mase-viewer/src/lib/components/CellEventLog.svelte` expands transaction details and can show request bodies only when server trace mode is `full`; the current CCRS `scenario.properties` uses `summary`.

- Observation: `npm run lint` is currently blocked before ESLint by the repository's Prettier/Svelte formatting setup, including existing files not touched by this work.
  Evidence: `npm run lint` reported formatting warnings for existing config/source files and `TypeError: getVisitorKeys is not a function or its return value is not iterable` while checking multiple existing `.svelte` files.

## Decision Log

- Decision: Name the root plan `PLAN_EMAS_WORKSHOP.md`.
  Rationale: The scope spans server, viewer, scenario docs, and presentation artifacts, so a root-level plan is more discoverable than a module-local plan.
  Date/Author: 2026-05-19 / Codex

- Decision: Treat `mase-server` and `mase-viewer` as the primary work areas and `mase-creator` as secondary for this workshop.
  Rationale: The user's current priority is a reliable workshop path through the server, viewer, and existing scenario. Creator-generated scenario authoring can be mentioned as future or optional material but should not control the live demo path.
  Date/Author: 2026-05-19 / Codex

- Decision: Use the current repository behavior, not the paper wording, as the executable workshop truth when the two differ.
  Rationale: The paper is the motivation and public framing, but workshop participants will run the current repository. Discrepancies must be called out explicitly in `Workshop.md`.
  Date/Author: 2026-05-19 / Codex

- Decision: Refer to the current trace setting as `full`, while noting that older or informal text may call this "detailed".
  Rationale: The implemented configuration values are `off`, `summary`, and `full`. The viewer only shows request bodies and per-rule triple diffs from full transaction trace events.
  Date/Author: 2026-05-19 / Codex

- Decision: If the demo feature is implemented, make it a server-owned demo session controlled by viewer buttons rather than a standalone Java process controlled from a terminal.
  Rationale: A server-side session can reuse RDF4J parsing and existing server dependencies, issue real HTTP GET/POST requests through the same linked-data API, preserve request causality in transaction traces, and return one request/response transcript per `Next` click to the viewer.
  Date/Author: 2026-05-19 / Codex

- Decision: The demo agent should prefer the current `maze:green` signifier when present and otherwise fall back to DFS.
  Rationale: The green signifier is the workshop-friendly bridge from the paper's affordance/signifier motivation to visible agent behavior. Falling back to DFS keeps the agent useful in cells or scenarios without green guidance.
  Date/Author: 2026-05-19 / Codex

- Decision: Make the live `Workshop.md` path start with the Docker starter stack and SmallMaze, keeping CCRS as optional deeper material.
  Rationale: SmallMaze is the clearest starter scenario for participants, and Docker starter startup is the lowest-friction path for a workshop room. CCRS remains useful for showing the larger thesis scenario after the core MASE mechanics are understood.
  Date/Author: 2026-05-19 / Codex

- Decision: Create a dedicated `emas-workshop` scenario package by copying CCRS and defaulting it to `CcrsMazeV2.trig`.
  Rationale: The workshop needs a reliable CCRS showcase without depending on A2A keyholder timing for the main path. The original CCRS package remains intact, while the workshop copy can be tuned for live demonstration and scenario-customization teaching.
  Date/Author: 2026-05-19 / Codex

- Decision: Implement the presenter-controlled demo feature before a manual dry run.
  Rationale: Static code inspection showed the current viewer had no way to trigger one agent request per click or show the matching request/response transcript, which is the central demo requirement.
  Date/Author: 2026-05-19 / Codex

## Outcomes & Retrospective

The first documentation slice is complete: `Workshop.md` now gives the current executable workshop path as a terse chaptered reference checklist, and `WORKSHOP_PITCH_SLIDE.md` defines the opening one-slide concept and 60-second script. Root `AGENTS.md` guidance now points future sessions to this plan.

The presenter-controlled demo feature has been implemented. The server exposes admin demo-agent endpoints, and the viewer includes a `Demo Agent` panel that resets the demo session, advances one request at a time, and displays the latest HTTP request and response. Automated server compilation, server tests, and viewer type checking pass. Full viewer lint remains blocked by an existing Prettier/Svelte tooling issue.

The Demo Agent panel is now hidden by default behind a `Demo Agent` button in the Live Events controls. Opening the button expands an inline view above the event tables with request/response controls and an `x` close icon, while the maze canvas and event tables remain accessible.

The latest `Workshop.md` revision is no longer organized as a prose-heavy reader or a checkbox runbook. It now follows the intended live session sequence: MASE overview, Docker-first SmallMaze startup, viewer orientation, controlled demo-agent stepping, Cell Updates inspection, SPARQL rules, reset, embodiment and access control, action effects, scenario customization, and optional creator/CCRS material.

The workshop now has a dedicated CCRS-derived showcase package under `mase-server/scenarios/emas-workshop` and a Docker Compose file at `docker-compose.emas-workshop.yml`. The guide uses paper-aligned terminology such as hypermedia agents, read-write Linked Data, RDF named graphs, embodiment constraints, affordances/signifiers, and environment dynamics, with short workshop explanations beside the terms.

Validation confirmed that `docker-compose.emas-workshop.yml` parses with the viewer profile enabled and that the Gradle distribution includes `scenarios/emas-workshop/scenario.properties`. The first sandboxed `./gradlew installDist` attempt could not download the Gradle wrapper because network access was restricted; the escalated rerun completed successfully.

The current cleanup pass removed a presenter-irrelevant admin URL from `Workshop.md`, made `DemoAgentPanel.svelte` strip unused `@prefix` declarations like the cell inspector does, and fixed the SmallMaze key UI rule by adding explicit prefixes, scheduling `ui-keys*` in the scenario rule order, and asserting that startup rules project green and red key UI markers.

## Context and Orientation

MASE stands for Linked Data Multi-Agent Systems Environment. In this repository it is a maze-like Linked Data environment: cells are RDF named graphs, links between cells are RDF predicates, agents perceive resources with HTTP GET, and agents change state with HTTP POST. The server enforces embodiment, meaning an agent has a current cell and can only perceive, interact with, or move according to that location and the RDF links available from it.

The EMAS paper frames MASE as a tool/testbed for hypermedia multi-agent systems. The key workshop message should be that MASE makes Web-agent behavior inspectable: an agent sends a concrete HTTP request, the server validates it against RDF state and embodiment rules, SPARQL Update rules materialize changes, and the viewer shows the resulting movement, UI updates, and transaction diagnostics.

Important repository files and modules:

- `README.md` is the current root entry point. It explains scenario-based startup, Docker and Gradle runs, trace modes, navigation rules, and scenario agents.
- `Step-by-Step-Guide.md` is older and still references legacy command names such as `sim-SmallMaze` and `runBobAgent`. Do not use it as the workshop truth without updating it.
- `CCRS-SCENARIO.md` is the current root CCRS run guide for Docker and Gradle.
- `docker-compose.ccrs.yml` starts the CCRS server and viewer and can recreate the CCRS and Keyholder agents.
- `docker-compose.emas-workshop.yml` starts the workshop-safe CCRS copy and viewer, with optional CCRS and Keyholder agents.
- `mase-server/PLAN_MASE_SERVER.md` records the accepted server baseline.
- `mase-viewer/MASE-VIEWER.md` records the accepted viewer baseline.
- `mase-server/scenarios/ccrs/scenario.properties` selects `data/CcrsMazeV1.trig`, names the scenario "CCRS Maze", and currently sets `mase.transaction.trace = summary`.
- `mase-server/scenarios/emas-workshop/scenario.properties` selects `data/CcrsMazeV2.trig`, names the scenario "EMAS Workshop CCRS Maze", and currently sets `mase.transaction.trace = summary`.
- `mase-server/scenarios/ccrs/rules/scenario/ccrs.rq` changes route signifiers, key UI elements, and stigmergy behavior in the CCRS scenario.
- `mase-server/scenarios/ccrs/rules/global/ui.rq` turns RDF UI descriptions into viewer-visible arrows, lock markers, and cell styling.
- `mase-server/scenarios/smallmaze/agents/src/main/java/org/maze/scenarios/smallmaze/agents/SampleDfsAgentBob.java` is the cleanest existing DFS implementation.
- `mase-server/scenarios/ccrs/agents/src/main/java/org/maze/scenarios/ccrs/agents/CcrsAgent.java` is the current CCRS infrastructure agent implementation, but it is tuned for multi-agent runs rather than explanatory stepping.
- `mase-server/src/main/java/org/maze/api/admin/MazeAdminResource.java` exposes the existing `/admin/maze` and `/admin/maze/reset` endpoints.
- `mase-server/src/main/java/org/maze/infrastructure/web/WebServerFactory.java` registers JAX-RS resources and servlet context services.
- `mase-viewer/src/routes/+page.svelte` composes the current viewer, reset/export controls, canvas, inspectors, and event tables.
- `mase-viewer/src/lib/mazeState.svelte.ts` owns WebSocket event handling, bounded hot tables, IndexedDB archive coordination, and reset state.
- `mase-viewer/src/lib/components/CellEventLog.svelte` renders the Cell Updates table and can expand full transaction traces.
- `mase-viewer/src/lib/components/AgentEventLog.svelte` renders the Agent Movements table.

Terms used in this plan:

An RDF named graph is a graph with its own IRI. MASE uses each cell URI, such as `http://127.0.1.1:8080/cells/12/5`, as the name of the RDF graph that describes that cell.

An embodiment constraint is the server rule that an agent's current location limits what it can read or change. In MASE, the `Authorization` header names the agent.

A signifier is RDF guidance that indicates an affordance, such as a green arrow marking a recommended direction. The current repository uses the predicate `maze:green` from `https://kaefer3000.github.io/2021-02-dagstuhl/vocab#`.

DFS means depth-first search. A DFS agent keeps a stack of the path it has taken, visits an unvisited neighbor when one exists, and backtracks when it reaches a dead end.

A transaction trace is the server's WebSocket diagnostic event for a startup, reset, or POST transaction. With trace mode `summary`, it contains a header and rule count. With trace mode `full`, it also contains the request body, merge delta, and per-rule triple changes.

## Milestones

Milestone 1 is to align the workshop story with the current repository. Read this plan, the current root README, `CCRS-SCENARIO.md`, `mase-server/PLAN_MASE_SERVER.md`, `mase-viewer/MASE-VIEWER.md`, and the OpenReview paper. The result is a short "paper versus current repo" note inside `Workshop.md` so participants are not confused by wording such as detailed trace mode, legacy command names, or paper-era scenario details.

Milestone 2 is to author `Workshop.md` as a terse workshop runbook. It should use visually separated chapters and checklist rows, not reader-style explanation or presenter narration. It should cover scope, ports, exact startup commands, reset, pitch, the controlled demo-agent path, viewer evidence, CCRS concepts, optional multi-agent runs, troubleshooting, and closing references. It should make clear that `mase-creator` is secondary for this workshop.

Milestone 3 is to prepare the 1-minute pitch slide concept. The output can be a root-level `WORKSHOP_PITCH_SLIDE.md` file or a dedicated section in `Workshop.md`. It should specify the slide title, visual layout, key text, and spoken timing. The slide should motivate the workshop by showing that MASE turns Web-agent interaction into something participants can see: request, rule execution, RDF state change, and viewer update.

Milestone 4 is a dry run with the current implementation. Start the CCRS server and viewer, set trace mode to `full` if request bodies are needed, use the existing reset flow, run an existing agent or manual POST, and decide whether the current viewer provides enough presenter control. If it is enough, do not implement the demo feature; instead, document the exact presenter actions in `Workshop.md`.

Milestone 5 is the conditional presenter-controlled demo feature. If the dry run shows the audience cannot follow the live agent behavior clearly, implement a demo agent session in the server and a control panel in the viewer. Each click on `Next` should execute exactly one HTTP request on behalf of the demo agent, return the request and response transcript, and leave the existing viewer tables to show the resulting movement and Cell Updates.

Milestone 6 is final validation and rehearsal. A presenter should be able to follow `Workshop.md` on a fresh checkout, deliver the pitch from the slide concept in about 60 seconds, run the CCRS scenario, and either step the demo agent or follow the documented manual/current-agent fallback.

## Plan of Work

First, create `Workshop.md` at the repository root as a clean runbook. Keep it operational: exact commands, URLs, click targets, visible evidence, and recovery actions. Avoid conversational setup text, paper-summary paragraphs, presenter cues, and generic logistics that do not affect the workshop run.

The workshop guide should be organized as chapters with checkboxes and compact tables. It should show the demo order, which buttons or commands to use, and what evidence confirms each point. When a participant sees the viewer, they should know which panel, table, or inspector to use and what success looks like.

Use current commands, not legacy commands. The safe local Gradle path is:

    cd mase-server
    .\gradlew.bat runMase --args="--scenario scenarios/ccrs"

The safe local viewer path is:

    cd mase-viewer
    npm install
    npm run dev

The Docker CCRS path from the root is:

    docker compose -f docker-compose.ccrs.yml --profile viewer up --build -d mase-viewer

Document that the Docker viewer is at `http://localhost:3000`, the local dev viewer is normally at `http://localhost:5173`, and the server is at `http://localhost:8080` for browser access. The canonical RDF base inside data remains `http://127.0.1.1:8080/`.

For transaction traces, document the current values exactly. In `mase-server/scenarios/ccrs/scenario.properties`, `mase.transaction.trace = summary` is the current default. To show request bodies and per-rule triple changes in the Cell Updates expansion, use:

    mase.transaction.trace = full

Do not call the value `detailed` in executable instructions unless the code is changed to accept that alias.

Next, draft the pitch slide concept. The recommended one-slide framing is:

Title: `MASE: See Web Agents Act`

Visual: a single horizontal flow from left to right. On the left, show an agent issuing `GET` or `POST` against a maze cell URI. In the center, show the MASE server applying embodiment checks and SPARQL rules. On the right, show the viewer with a moved agent, a highlighted green signifier, and a Cell Updates row. The visual should use one real viewer screenshot from the current repo rather than an abstract diagram if possible.

Minimal slide text:

    Web agents need testbeds that are inspectable.
    MASE makes each action visible: HTTP request -> RDF rules -> live state update.
    In this workshop, you will run, inspect, and adapt a hypermedia MAS scenario.

Spoken timing for 60 seconds:

    0-10s: Web agents are hard to debug because behavior is distributed across requests, state, and environment dynamics.
    10-25s: MASE turns the environment into linked RDF resources that agents access through normal HTTP.
    25-40s: The server enforces embodiment and attributes each state change to the request and rules that caused it.
    40-55s: The viewer lets us watch the maze, inspect RDF, and read transaction evidence while agents run.
    55-60s: The workshop is a hands-on path from first run to controlled scenario behavior.

Then perform the dry run before implementing new code. The dry run should answer these questions:

1. Can a presenter explain one agent action clearly using only the current viewer, existing agents, and full transaction traces?
2. Can the current CCRS agent run reliably enough for a workshop room?
3. Is the current multi-agent CCRS run too noisy for an opening explanation?
4. Does the audience need a `Next` button to see the request/response boundary?

If the answer to question 4 is yes, implement the demo feature in small, additive slices.

For the server slice, add a demo agent service under `mase-server/src/main/java/org/maze/application/demo/`. It should maintain an in-memory session for one presenter-controlled demo agent. The session state should include agent name, current cell URI, DFS stack, visited cells, remembered key values by type, pending action phase, step number, completion status, and the most recent request and response transcript. The service should use Java `HttpClient` to send actual requests to the MASE linked-data API so the normal access validator, POST handler, rules, and transaction tracing are exercised.

For the API slice, add a JAX-RS resource under `mase-server/src/main/java/org/maze/api/admin/`, for example `DemoAgentResource.java`, and register it in `WebServerFactory`. The endpoints should be under `/admin/demo-agent` because they are presenter/debug controls, not public maze resources:

    POST /admin/demo-agent/reset
    POST /admin/demo-agent/next
    GET  /admin/demo-agent/state

The reset endpoint should initialize the demo session without resetting the RDF store. Store reset remains the existing viewer Reset Store action. The next endpoint should execute one HTTP request and return a JSON transcript. The state endpoint should let the viewer refresh without changing simulation state.

For the agent behavior, start with the clean SmallMaze `SampleDfsAgentBob.java` algorithm and the CCRS parsing additions from `CcrsAgent.java`. The agent should:

1. On the first step, GET `/maze` with `Authorization: <agent>`, parse `xhv:start`, and report the discovered start cell.
2. On the next step, POST `dynmaze:entersFrom </maze>` to the start cell.
3. On later GET steps, parse the current cell for directions, `maze:exit`, `maze:green`, lock state, hydra operations, and keys.
4. If a `maze:green` target exists and is a traversable target, choose it before normal DFS. If it is absent, use DFS direction order.
5. If the current cell is locked and a matching key is known, POST the needed `dynmaze:keyValue` triple to the lock target and then re-read the cell on the next step.
6. If `maze:exit` exists, POST movement to the exit and mark the session complete after reaching `/cells/999`.
7. If no unvisited neighbor is available, backtrack using the DFS stack.

The service should also be tolerant of the user phrase `mase:green`. In the current code, use `maze:green`. If a future `mase:green` alias is added to `MazeVocab`, the parser can check both predicates. Do not invent a new namespace just for the workshop unless the vocabulary decision is made elsewhere.

For the viewer slice, add a small demo control panel near the event logs in `mase-viewer/src/routes/+page.svelte` or as a new component under `mase-viewer/src/lib/components/DemoAgentPanel.svelte`. The panel should include:

1. Agent name, with default `demo-agent`.
2. A toggle named `Prefer green signifiers`.
3. `Reset Demo Agent` and `Next Request` buttons.
4. Current cell, status, step number, and decision summary.
5. A request block showing method, URL, headers, and Turtle body when present.
6. A response block showing HTTP status, content type, and body.
7. A compact error state that does not clear the existing viewer tables.

Do not make the demo panel the source of truth for maze rendering. The canvas and tables must still update from the normal server snapshot and WebSocket event stream.

Update docs after implementation. `Workshop.md` should explain both paths: the preferred controlled-demo path and the fallback path using current agent tasks and Cell Updates. `README.md` should only be updated if the new demo feature is general enough for ordinary users; otherwise keep it workshop-local.

## Concrete Steps

Start from the repository root:

    cd S:\dev\ma\mase

Check existing work and avoid reverting unrelated changes:

    git status --short

Create or update the workshop guide:

    code Workshop.md

The final `Workshop.md` should include these sections:

1. Scope and demo thread.
2. Preflight ports and files.
3. Docker and local startup commands.
4. Trace mode selection.
5. Reset checklist.
6. Opening pitch checklist.
7. Controlled demo-agent steps.
8. Viewer evidence checklist.
9. CCRS concept table.
10. Optional multi-agent commands.
11. Troubleshooting and recovery.
12. Closing references.

Create or update the pitch artifact:

    code WORKSHOP_PITCH_SLIDE.md

If the demo feature is implemented, add server tests around the demo agent state machine where practical:

    cd mase-server
    .\gradlew.bat test --tests "*DemoAgent*"

Then compile the server:

    cd mase-server
    .\gradlew.bat classes

Validate the viewer after UI changes:

    cd mase-viewer
    npm run check
    npm run lint

Run the manual demo path:

    cd S:\dev\ma\mase
    docker compose -f docker-compose.ccrs.yml --profile viewer up --build -d mase-viewer

Open the viewer at `http://localhost:3000`. Use Reset Store before each rehearsal. If full transaction evidence is needed, set `mase.transaction.trace = full` in the CCRS scenario properties and restart the server before the rehearsal.

For a quick current-agent fallback, run:

    cd mase-server
    .\gradlew.bat runCcrsAgent

If the Keyholder A2A path is part of the workshop, also run:

    cd mase-server
    .\gradlew.bat runKeyHolderAgent

At each stopping point, update this plan's `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` sections.

## Validation and Acceptance

`Workshop.md` is accepted when a person who has not read the paper can follow it from a clean checkout, start the current server and viewer, reset the scenario, inspect one cell, inspect one agent, understand what the Cell Updates table means, and recover from stale state by using Reset Store.

The pitch slide concept is accepted when it fits on one slide, uses a real MASE visual or a faithful screenshot-based composition, and supports a 60-second spoken pitch without requiring the speaker to explain more than one diagram.

The optional demo feature is accepted only if these behaviors work:

1. The viewer shows a demo panel with `Reset Demo Agent` and `Next Request`.
2. Each `Next Request` click issues exactly one HTTP request on behalf of the demo agent.
3. The panel shows the exact request method, URL, headers, body, response status, and response body for the latest step.
4. When the current cell has a `maze:green` target, the next movement chooses that target before DFS.
5. When no `maze:green` target exists, the agent falls back to DFS with a stable direction order.
6. The normal canvas, Agent Movements table, and Cell Updates table update through existing WebSocket events.
7. With `mase.transaction.trace = full`, expanding the corresponding Cell Updates row shows request body and RDF changes caused by that POST.
8. Reset Store clears the RDF scenario state; Reset Demo Agent clears only the demo agent session state.

Documentation validation for plan and workshop files:

    git diff --check -- PLAN_EMAS_WORKSHOP.md Workshop.md WORKSHOP_PITCH_SLIDE.md

Server validation after code changes:

    cd mase-server
    .\gradlew.bat test
    .\gradlew.bat classes

Viewer validation after UI changes:

    cd mase-viewer
    npm run check
    npm run lint

Expected result for automated validation is no Markdown whitespace errors, Gradle reports `BUILD SUCCESSFUL`, and Svelte check/lint finish without errors.

## Idempotence and Recovery

Documentation steps are safe to repeat. When revising `Workshop.md`, keep the live commands current and remove legacy commands rather than leaving contradictory alternatives.

The dry run is recoverable through the existing viewer Reset Store flow. Reset Store asks whether to export or discard logs, calls `POST /admin/maze/reset`, clears viewer hot logs and archive state, refreshes the snapshot, and remounts the canvas.

If the demo feature is implemented, the demo session reset must not reset the RDF store. It should only clear the in-memory demo-agent stack, visited set, keyring, step counter, and last transcript. If the presenter wants to restart the whole scenario, they must use Reset Store first and then Reset Demo Agent.

If a `Next Request` fails, keep the latest failed request and response visible. The next click should either retry the same phase safely or require Reset Demo Agent, depending on whether the request may have mutated state. Failed GETs are safe to retry. Failed POSTs should be treated carefully because the status code and transaction trace are the evidence of whether the server committed or rejected the request.

Avoid a global simulation clock or background auto-run for the demo. The goal is presenter control, not throughput.

## Artifacts and Notes

Paper source for framing:

- Local attachment path: `C:/Users/stefa/Downloads/EMAS_2026_Demo_Paper (13).pdf`
- Public paper page: `https://openreview.net/forum?id=lNF8HCzvN6`
- Public PDF: `https://openreview.net/attachment?id=lNF8HCzvN6&name=pdf`

The paper-facing message to preserve in workshop prose is:

MASE is a lightweight Linked Data maze environment for controlled experimentation with hypermedia agents. It keeps the maze abstraction simple while making interaction concrete: agents use HTTP, RDF graphs represent resources, server-side rules evolve the environment, embodiment constraints are enforced, and the viewer makes request-caused state changes inspectable.

Current repository discrepancies to explain:

- The current run model is scenario-folder based.
- The current CCRS scenario is under `mase-server/scenarios/ccrs`.
- The current viewer has reset/export and IndexedDB archive behavior beyond the paper snapshot.
- The current transaction trace setting for detailed request and rule evidence is `full`, not `detailed`.
- The current route signifier predicate is `maze:green`.
- `mase-creator` exists but is secondary for this workshop.

Suggested workshop arc:

1. Start with the pitch slide and the problem: Web-agent behavior is hard to inspect.
2. Show the viewer and explain that the maze is a graph of HTTP-accessible RDF resources.
3. Run or step one agent request.
4. Connect the visible canvas move to the request and response.
5. Expand a Cell Updates row to show transaction evidence.
6. Show how CCRS extends the base maze with signifiers, locks, keys, and stigmergy rules.
7. Close by pointing to scenario folders as the customization unit.

## Interfaces and Dependencies

The server demo service, if implemented, should expose JSON DTOs with stable fields rather than returning ad hoc maps from resource methods. Suggested DTOs:

    package org.maze.api.dto;

    public record DemoAgentResetRequest(
        String agentName,
        boolean preferGreenSignifiers
    ) {}

    public record DemoAgentStepResponse(
        int step,
        String status,
        String agent,
        String currentCell,
        String decision,
        DemoHttpTranscript transcript
    ) {}

    public record DemoHttpTranscript(
        String method,
        String url,
        Map<String, String> requestHeaders,
        String requestBody,
        int responseStatus,
        Map<String, List<String>> responseHeaders,
        String responseBody
    ) {}

The server resource should use `jakarta.ws.rs` like `MazeAdminResource`. Register the resource in `WebServerFactory` with the existing Jersey `ResourceConfig`.

The server service should use existing RDF dependencies already present in `mase-server/build.gradle`: RDF4J `Model`, `Rio.parse`, and `SimpleValueFactory`. Do not add a new RDF parser unless the existing RDF4J APIs cannot support the needed parsing.

The viewer should call the new endpoints through the configured public server base already provided by `mase-viewer/src/lib/mazeServerConfig.ts`. It should not hardcode `localhost:8080`.

The viewer component should use the existing style vocabulary of the page and should not change the existing event archive contract in `mase-viewer/src/lib/eventArchive.ts`.

Revision note, 2026-05-19: Created the root plan to coordinate EMAS workshop documentation, the 1-minute pitch slide concept, and the optional presenter-controlled demo agent feature.

Revision note, 2026-05-19: Added the first workshop documentation slice, the pitch slide concept, and root AGENTS guidance; updated progress and outcomes accordingly.

Revision note, 2026-05-19: Implemented the server/viewer demo-agent stepper, updated workshop docs to use it, and recorded validation results plus the remaining manual rehearsal task.

Revision note, 2026-05-19: Reworked `Workshop.md` into a cleaner runbook/checklist format with visually separated chapters and less background prose.

Revision note, 2026-05-19: Tightened `Workshop.md` again by removing presenter narration, run-mode chatter, and generic setup text while preserving the workshop commands and evidence checklist.

Revision note, 2026-05-19: Updated the viewer demo-agent UI to open from a button and close from the inline panel header; updated the workshop checklist to match.

Revision note, 2026-05-19: Replaced the modal Demo Agent overlay with an inline collapsible view so the presenter can keep the maze canvas and event tables visible.

Revision note, 2026-05-19: Reframed `Workshop.md` around the requested participant-friendly live session flow, with SmallMaze as the starter path, Docker as the guaranteed run mode, numbered steps instead of checkboxes, and compact tables for shell commands and concepts.

Revision note, 2026-05-19: Added the dedicated `emas-workshop` CCRS-derived scenario package and Docker Compose file, and revised `Workshop.md` to use MASE-paper terminology, a flexible demo-agent moment table, one access-control failure example, and a required CCRS showcase section.

Revision note, 2026-05-19: Removed the server admin URL from the workshop preflight table, cleaned unused prefixes in Demo Agent request/response bodies, fixed the SmallMaze key UI projection rule, and added focused startup-test coverage for the key UI markers.
