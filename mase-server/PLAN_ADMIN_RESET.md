# PLAN_ADMIN_RESET: Reset the RDF store from the configured scenario

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

No `PLANS.md` or `.agent/PLANS.md` guide is currently checked into this repository. This plan follows the local `PLAN_<SCOPE>.md` convention from the Codex exec-plan guidance.

## Purpose / Big Picture

After this work is implemented, a developer running `mase-server` can rerun a maze experiment without restarting the full Java server process. An admin HTTP request will clear the in-memory RDF4J store, reload the scenario TriG file configured by `mase.init.dataset`, run the same startup rules used on boot, and leave `/admin/maze` reporting the fresh starting state again.

This matters for CCRS and other simulation runs because agents can mutate the RDF store substantially during an experiment. Resetting the store through the server should be faster and less disruptive than rebuilding containers or restarting the server, while preserving the existing scenario configuration and rule ordering.

## Progress

- [x] (2026-05-17 13:12Z) Created this executable plan and captured the agreed implementation direction.
- [x] (2026-05-17 13:19Z) Cross-linked the reset plan with the viewer planning notes and documented the reset/data invalidation dependency in both files.
- [ ] Add a server-side reset service that can clear and reload the RDF4J repository in one controlled operation.
- [ ] Add an admin endpoint, expected as `POST /admin/maze/reset`, that invokes the reset service and returns a machine-readable result.
- [ ] Ensure the reset operation serializes safely against agent POST handling and other write operations.
- [ ] Clear or reset WebSocket replay state so old experiment events are not replayed after a reset.
- [ ] Add focused server tests for successful reset, failure rollback, and reset concurrency behavior.
- [ ] Document the endpoint in `mase-server/README.md`.
- [ ] Defer `mase-viewer` data invalidation and remount behavior. Record frontend impact considerations in this plan only.

## Surprises & Discoveries

- Observation: The current server repository is an RDF4J `MemoryStore`, so a reset does not need to delete on-disk RDF data.
  Evidence: `mase-server/src/main/java/org/maze/infrastructure/rdf/RepositoryFactory.java` creates a `MemoryStore` and wraps it in `MazeNotifyingSail`.

- Observation: Startup already performs most of the desired reset sequence, but the code is embedded in `Configurator.main`.
  Evidence: `mase-server/src/main/java/org/maze/Configurator.java` loads `config.getInitDataset()`, creates `MazeRuleService`, and runs `TransactionTraceContext.forStartup(...)` before starting Jetty.

- Observation: The current `DataLoader` commits each loaded file using its own connection.
  Evidence: `mase-server/src/main/java/org/maze/infrastructure/rdf/DataLoader.java` opens `repository.getConnection()` in `loadData(...)`, then `loadFile(...)` calls `conn.begin()`, `conn.add(ds)`, and `conn.commit()`.

- Observation: The viewer already fetches its baseline state from the admin snapshot endpoint, but canvas state is also held in Konva objects and local maps.
  Evidence: `mase-viewer/src/routes/+page.ts` fetches `http://localhost:8080/admin/maze`, and `mase-viewer/src/lib/components/MazeCanvas.svelte` stores runtime agents and UI nodes in maps such as `agentPositions`, `agents`, and `uiNodes`.

- Observation: Viewer reset invalidation depends on both the server reset contract and the viewer event-retention strategy.
  Evidence: [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md) now records that reset can mean clearing live state, archiving the previous run, preserving logs for audit, remounting the canvas, refreshing inspectors, or some combination of those behaviors.

## Decision Log

- Decision: Implement reset first as a `mase-server` admin capability, not as a viewer-driven state-management change.
  Rationale: The RDF store is authoritative. The server must be able to reset correctly before the viewer decides how to invalidate or remount client-side data.
  Date/Author: 2026-05-17 / Codex

- Decision: Reuse the configured scenario file from `mase.init.dataset` rather than accepting an arbitrary dataset path in the reset request.
  Rationale: The reset operation should restore the same scenario that the running server booted with. Accepting request-supplied paths would add avoidable safety and authorization concerns.
  Date/Author: 2026-05-17 / Codex

- Decision: Treat viewer data invalidation as deferred work.
  Rationale: The user stated that invalidation depends on other viewer changes. This plan records impact considerations but does not require implementing SvelteKit invalidation, canvas remounting, or log clearing in the current reset feature.
  Date/Author: 2026-05-17 / Codex

- Decision: Make reset/viewer invalidation a cross-plan dependency.
  Rationale: The server must define an atomic reset endpoint, replay-buffer cleanup, and a reset notification contract. The viewer must then decide how that contract interacts with hot logs, archives, Konva canvas remounting, and selected inspectors, as described in [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md).
  Date/Author: 2026-05-17 / Codex

## Outcomes & Retrospective

This plan has only been initiated. No server or viewer code has been changed yet.

## Context and Orientation

The `mase-server` module is a Java 21 Gradle application. The main class is `org.maze.Configurator` in `mase-server/src/main/java/org/maze/Configurator.java`. At startup, it reads a `sim-*.properties` file through `mase-server/src/main/java/org/maze/infrastructure/config/ServerConfiguration.java`. For CCRS, `mase-server/sim-CcrsMaze.properties` sets `mase.init.dataset = data/CcrsMazeV1.trig` and defines rule execution order.

RDF4J is the RDF store library used by the server. The repository is currently an in-memory RDF4J `MemoryStore`, created by `mase-server/src/main/java/org/maze/infrastructure/rdf/RepositoryFactory.java`. The store is wrapped by `MazeNotifyingSail`, which observes statement changes and lets `MazeUpdateListener` broadcast WebSocket events after commits.

The admin HTTP resource is `mase-server/src/main/java/org/maze/api/admin/MazeAdminResource.java`. It currently exposes `GET /admin/maze`, which creates a `MazeLayoutService`, builds the layout and UI snapshot, and returns a `MazeAdminSnapshotDto`.

The normal agent mutation path is `mase-server/src/main/java/org/maze/application/PostHandler.java`. It validates agent access, adds incoming RDF, runs rules, validates postconditions, and commits. It has a per-resource lock registry, but it does not currently have a global write lock that would stop every agent mutation while a full reset is in progress.

The `mase-viewer` module is a SvelteKit application. It loads `/admin/maze` in `mase-viewer/src/routes/+page.ts`, then `MazeCanvas.svelte` applies the initial UI snapshot and live WebSocket events. Because the canvas stores runtime state outside the loaded page data, a future viewer reset button will need a deliberate invalidation and remount strategy. That strategy is not part of this server plan. The linked viewer planning document, [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md), tracks that dependency from the frontend side.

## Milestones

Milestone 1 adds the server reset primitive without changing HTTP behavior. Create a service class that can clear the repository, parse the configured TriG dataset, add it in a transaction, run startup rules, and commit. The milestone is complete when focused tests can call the service directly and observe that a mutated repository returns to the configured starting scenario.

Milestone 2 exposes the primitive through the admin API. Extend `MazeAdminResource` with `POST /admin/maze/reset`, retrieve the reset service from the servlet context, and return either a fresh `MazeAdminSnapshotDto` or a compact JSON result. The milestone is complete when an HTTP client can mutate state, call the endpoint, and then see `/admin/maze` report the reset layout and UI snapshot.

Milestone 3 hardens runtime behavior. Add a global mutation lock that reset and write operations share, clear the WebSocket replay buffer during reset, and verify that old pre-reset events are not replayed to newly connected viewers. The milestone is complete when tests or a manual run show that a reset cannot interleave with a POST transaction and that subsequent WebSocket clients do not receive stale movement or UI deltas.

Milestone 4 documents frontend impact without implementing viewer invalidation. Record how a viewer button could call the endpoint later, and list the state that must be invalidated, archived, preserved, or remounted. The milestone is complete when documentation makes clear that viewer invalidation is intentionally deferred and linked to the viewer planning work in [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md).

## Plan of Work

First, extract the startup reset sequence into application code. A suitable class name is `org.maze.application.MazeResetService`, located at `mase-server/src/main/java/org/maze/application/MazeResetService.java`. Its constructor should receive the `SailRepository`, `ServerConfiguration`, `MazeRuleService`, and base RDF URI used when parsing TriG. It should expose a method like `public MazeResetResult resetToInitialDataset()` or `public MazeAdminSnapshotDto resetToInitialDataset()`.

Refactor `DataLoader` so reset can load files inside an existing transaction. The current `loadData(...)` method can remain for startup compatibility, but add an API that accepts a `RepositoryConnection` and dataset pattern, parses all matching files, and calls `conn.add(...)` without starting or committing a nested transaction. This avoids partial reset state when one file parse fails.

Introduce a global write coordinator for reset and agent mutations. One conservative option is an application-level `ReentrantReadWriteLock`: normal POST handling takes the read lock for the duration of `PostHandler.performPost(...)`, while reset takes the write lock for the full clear, load, rule execution, and commit sequence. This preserves current per-resource serialization while preventing a full-store clear from interleaving with active writes. If SPARQL update endpoints can mutate the store independently, they must use the same coordinator before this plan is considered complete.

Wire the reset service into Jetty/Jersey setup in `mase-server/src/main/java/org/maze/infrastructure/web/WebServerFactory.java`. Store the service in the servlet context with a new constant, for example `MAZE_RESET_SERVICE_SERVLET_ATTRIBUTE`. `MazeAdminResource` should retrieve the service the same way it retrieves `SAIL_REPOSITORY_SERVLET_ATTRIBUTE`.

Add `@POST @Path("/reset")` to `MazeAdminResource`, with JSON response. A practical response is the same `MazeAdminSnapshotDto` returned by `GET /admin/maze`, because clients can immediately render the fresh authoritative state. If returning the snapshot is too expensive for large mazes, return a compact object with fields such as `status`, `dataset`, and `scenario`, and keep `/admin/maze` as the authoritative fetch.

Clear stale WebSocket replay data during reset. Add a method such as `MazeBroadcaster.clearRecentMessages()` in `mase-server/src/main/java/org/maze/api/websocket/MazeBroadcaster.java`. Call it after a successful reset commit and before broadcasting any reset notification. A reset notification can be a simple JSON event such as `{"type":"ADMIN_RESET"}`; the current viewer will ignore unknown event types, which is acceptable until viewer invalidation is designed.

Update CORS for browser POSTs. `mase-server/src/main/java/org/maze/api/ld/CorsFilter.java` currently advertises `GET, OPTIONS, HEAD`; it should include `POST` so a future viewer button can call `/admin/maze/reset` from the Vite/SvelteKit dev origin.

Do not implement SvelteKit invalidation or `MazeCanvas` remounting as part of this plan. The viewer impact should be documented only. The cross-plan dependency is explicit: this server plan should define the reset endpoint, response body, replay-buffer cleanup, and reset notification; [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md) should decide whether the viewer uses `invalidateAll()`, a keyed component remount, full page reload, or a store-level reset API after log/archive semantics are clear.

## Concrete Steps

Work from the repository root unless a command says otherwise.

1. Inspect current server wiring:

        rg -n "loadData|forStartup|createServer|MazeAdminResource|POST_HANDLER" mase-server/src/main/java

   Expect to see `Configurator` handling startup loading, `WebServerFactory` registering `MazeAdminResource`, and `PostHandler` owning normal POST mutations.

2. Add the reset service, loader refactor, and write coordinator. Keep edits scoped to `mase-server/src/main/java/org/maze/application`, `mase-server/src/main/java/org/maze/infrastructure/rdf`, `mase-server/src/main/java/org/maze/infrastructure/web`, and `mase-server/src/main/java/org/maze/api/admin`.

3. Add or update tests under `mase-server/src/test/java`. Prefer focused unit tests that construct a `SailRepository`, load `data/SmallMaze.trig` or `data/CcrsMazeV1.trig`, mutate it, reset it, and assert that known starting triples are restored while mutation triples are gone.

4. Run compilation:

        cd mase-server
        ./gradlew compileJava

   On Windows PowerShell, if the shell cannot execute `./gradlew`, use:

        .\gradlew.bat compileJava

   Expected result is `BUILD SUCCESSFUL`.

5. Run tests:

        cd mase-server
        ./gradlew test

   Expected result is `BUILD SUCCESSFUL`. New reset tests should fail before the reset implementation and pass after it.

6. Manually exercise the endpoint after implementation:

        cd mase-server
        ./gradlew runMase --args="sim-CcrsMaze"

   In another shell, fetch the current admin snapshot:

        curl http://localhost:8080/admin/maze

   After mutating the maze through an agent or RDF POST, call:

        curl -X POST http://localhost:8080/admin/maze/reset

   Then call:

        curl http://localhost:8080/admin/maze

   The final snapshot should reflect the starting CCRS scenario from `data/CcrsMazeV1.trig`, including starting counter and UI state after startup rules.

## Validation and Acceptance

The server feature is accepted when `POST /admin/maze/reset` restores the repository to the scenario configured in the active `sim-*.properties` file without restarting the server process. For `sim-CcrsMaze`, that means reloading `mase-server/data/CcrsMazeV1.trig` unless the property file has been changed.

The reset must be atomic from the perspective of other writers. A concurrent agent POST must either complete before reset begins or wait until reset finishes. It must not observe an empty or half-loaded store, and reset must not erase changes committed after reset completion.

The WebSocket replay buffer must not replay old experiment events to a viewer that connects after reset. If a reset notification is broadcast, it should be emitted only after the reset commit succeeds.

Compilation and tests must pass with:

    cd mase-server
    ./gradlew compileJava
    ./gradlew test

If Gradle wrapper execution is blocked by the local environment, record the exact error in this plan under `Artifacts and Notes` and run the closest available compile or test command.

Viewer invalidation is not part of acceptance for this plan. The current viewer may continue showing stale canvas state after an external reset until a later viewer task defines and implements invalidation. This is an explicitly accepted gap for this phase, and [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md) records the related frontend state surfaces and bottlenecks.

## Idempotence and Recovery

Calling reset more than once should produce the same starting repository state each time. The reset service should use a single transaction for clear, load, and startup rule execution. If parsing the TriG file or running startup rules fails, the transaction should roll back and the endpoint should return an error without leaving a partially loaded store.

The implementation should not delete or rewrite scenario files. It should only read the configured TriG files and mutate the in-memory RDF repository.

If a reset attempt fails while the server is running, inspect server logs for the exception, verify that `mase.init.dataset` points to an existing file, and retry the endpoint after fixing the configuration or data file. A server restart remains a fallback but should not be required for normal failed reset recovery.

## Artifacts and Notes

Current CCRS configuration:

    mase-server/sim-CcrsMaze.properties
    mase.init.dataset = data/CcrsMazeV1.trig
    mase.rules.execution.order= normalize_maze_locks*, ccrs*, unlock*, cleanup*, move*

Current admin snapshot fetch in the viewer:

    mase-viewer/src/routes/+page.ts
    fetch('http://localhost:8080/admin/maze')

Viewer invalidation considerations for a future plan:

The viewer has at least four independent state surfaces affected by reset. Page load data holds `maze`, `uiSnapshot`, and `scenarioName`. `MazeCanvas.svelte` holds Konva objects and maps for agents, agent positions, UI nodes, path render caches, and cell background state. `mazeState.svelte.ts` holds WebSocket-derived event logs and runtime listener delivery. The page holds selected cell and agent inspector state. A future viewer reset implementation must decide which of these should be cleared, preserved for audit, or refreshed from `/admin/maze`.

The safest simple viewer behavior may be a full page reload after a successful reset POST, because it remounts `MazeCanvas` and reruns `+page.ts`. A smoother behavior may use SvelteKit invalidation plus a keyed remount of `MazeCanvas`, but that needs care because updating page data alone will not automatically destroy all Konva runtime state. This plan does not choose between those options.

See [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md) for the frontend side of this dependency. That file also records why reset behavior intersects with event hot windows, cold archives, replay de-duplication, and inspector state.

## Interfaces and Dependencies

At the end of implementation, `mase-server/src/main/java/org/maze/application/MazeResetService.java` should provide one public reset method that performs the full clear, reload, startup rule execution, WebSocket replay cleanup, and result creation. The exact result type may be a small DTO or `MazeAdminSnapshotDto`, but the endpoint must return JSON.

`mase-server/src/main/java/org/maze/api/admin/MazeAdminResource.java` should expose:

    POST /admin/maze/reset

The endpoint should consume no request body and produce `application/json`.

`mase-server/src/main/java/org/maze/infrastructure/web/WebServerFactory.java` should create and store the reset service in the servlet context so the JAX-RS resource can access it.

`mase-server/src/main/java/org/maze/infrastructure/rdf/DataLoader.java` should expose a transaction-friendly loading method that can add configured RDF files to an existing connection without committing.

`mase-server/src/main/java/org/maze/api/websocket/MazeBroadcaster.java` should expose a replay-buffer clearing method so stale pre-reset events are not delivered after reset.

Revision note, 2026-05-17: Initiated this plan from the reset endpoint analysis. The user agreed with the server-side approach and explicitly deferred `mase-viewer` data invalidation, so the plan records viewer impact considerations without making frontend invalidation part of the current work.

Revision note, 2026-05-17: Added the cross-plan dependency with [MASE-VIEWER.md](../mase-viewer/MASE-VIEWER.md). The server reset plan now explicitly owns the reset endpoint and notification contract, while the viewer plan owns later invalidation, remounting, and log/archive behavior.
