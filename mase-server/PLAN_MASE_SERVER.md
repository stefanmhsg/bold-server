# PLAN_MASE_SERVER: Server Baseline And Open Work

This is the compact server-scope ExecPlan for [mase-server](.). It records the current accepted architecture, the few decisions future work must preserve, and the open tasks that still need attention.

## Purpose / Big Picture

`mase-server` is scenario-folder based. A scenario can be added, run, reset, and rerun by placing one folder under [scenarios](scenarios) or by starting the server with any compatible scenario folder:

    cd mase-server
    gradle runMase
    gradle runMase --args="--scenario scenarios/ccrs"

With no argument, `Configurator` checks `MASE_SCENARIO_DIR` and otherwise defaults to [scenarios/smallmaze](scenarios/smallmaze).

## Progress

- [x] Scenario-folder startup is the default server model.
- [x] Legacy task-name startup, root-level runtime data, and shared resource-rule loading have been removed.
- [x] Built-in scenarios exist for SmallMaze, MidMaze, BigMaze, MaseCreator, and CCRS under [scenarios](scenarios).
- [x] Scenario-owned Java agents live under scenario folders and run as separate Java processes through Gradle tasks.
- [x] `POST /admin/maze/reset` reloads the active scenario data and rules without restarting the server.
- [x] Active rules are sorted by slash-normalized, scenario-relative, case-folded names for portable trace order.
- [x] [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java) uses one robust layout algorithm for all scenarios; there is no `maze:CcrsMaze` type switch.
- [x] [maze.ttl](docs/maze.ttl) has been expanded and is served from `/vocab` with RDF content negotiation.
- [x] Align creator WP6 export expectations with this final server contract.
- [ ] Add an HTTP-level smoke test for `/vocab` if API integration tests are introduced.
- [ ] Decide whether the current `https://example.org/...` namespaces for UI, MASE API, A2A, and stigmergy terms should become canonical project-owned IRIs.

## Surprises & Discoveries

- Scenario support did not require changing RDF4J storage or the linked-data API shape. The main coupling was startup file resolution and rule discovery.
- `mase.server.protocol = ldp` had no runtime effect and is intentionally gone.
- `MASE_SERVER_BASE_URI` must remain the canonical RDF identity base. Browser-facing and Docker-facing transport hosts may differ.
- The old generic layout was a subset of the CCRS layout. The retained algorithm covers outgoing links, incoming-only references, disconnected components, and occupied-position checks.
- Dynamic Java loading from scenario folders is not implemented. Agents are scenario-owned source files compiled by Gradle and run as separate processes.
- The creator WP6 package export can satisfy the server resolver with global rules under `rules/global/` and an empty `rules/scenario/` directory because the resolver loads every `.rq` file below `rules/` recursively.

## Decision Log

- Decision: A scenario folder is the runtime ownership unit.
  Rationale: Scenario data, runtime properties, rules, docs, validation assets, and agent launch notes should live together.
  Date/Author: 2026-05-17 / Codex

- Decision: Use `scenario.properties` for runtime configuration and `manifest.json` for metadata/provenance.
  Rationale: The server already consumes Java properties for startup values. Manifest data should not be required to boot.
  Date/Author: 2026-05-17 / Codex

- Decision: Load every `.rq` file under `rules/` recursively and ignore `rules-disabled/`.
  Rationale: Subdirectories inside `rules/` are for organization only. Deactivation is explicit by moving files out of the active tree.
  Date/Author: 2026-05-17 / Codex

- Decision: Keep scenario-owned Java agents outside the server runtime loader.
  Rationale: Runtime Java loading needs a security and build model. Gradle-compiled agent processes are simple, visible, and testable.
  Date/Author: 2026-05-17 / Codex

- Decision: Serve the checked-in vocabulary from `/vocab`.
  Rationale: The server should expose the vocabulary it emits and consumes. The distribution copies [docs](docs) so Docker serves the same file.
  Date/Author: 2026-05-17 / Codex

- Decision: Creator-generated packages target the scenario-folder contract directly.
  Rationale: The creator should emit a folder that `mase-server` can load without manually moving data, rules, config, and docs. The first WP6 slice includes global rules and reserves an empty `rules/scenario/` folder for future scenario-specific rule generation.
  Date/Author: 2026-05-19 / Codex

## Outcomes & Retrospective

The scenario-folder migration is complete and accepted as the current baseline. Future server work should assume scenario-folder-only startup, scenario-local rules, scenario-local data, and Gradle-run scenario agents.

The admin reset flow is complete: reset clears and reloads the active scenario dataset, runs startup rules, serializes against writes, clears stale WebSocket replay data, emits a reset event, and returns a fresh admin snapshot.

The creator WP6 export alignment is complete for the first package slice: `mase-creator` emits a server-ready scenario folder under its editor output directory, including data, `scenario.properties`, `manifest.json`, global rules, documentation stubs, and an empty scenario-rules directory.

## Context and Orientation

Current scenario folder shape:

    scenarios/<scenario-id>/
      README.md
      scenario.properties
      manifest.json
      data/
        <scenario-data>.trig
      rules/
        *.rq
        any-subdirectory/
          *.rq
      rules-disabled/
        README.md
        *.rq
      validation/
        README.md
        *.rq
      agents/
        README.md
        src/main/java/

Typical `scenario.properties` values:

    mase.scenario.id = ccrs
    mase.init.dataset = data/CcrsMazeV1.trig
    mase.rules.execution.order = normalize_maze_locks*, ccrs*, unlock*, cleanup*, move*
    # Transaction trace mode: off, summary headers/rule count, or full per-triple debug diffs
    mase.transaction.trace = summary

Current scenario-owned Gradle agent tasks:

    runSmallMazeBobAgent
    runMidMazeBobAgent
    runBigMazeBobAgent
    runMaseCreatorBobAgent
    runCcrsAgent
    runKeyHolderAgent
    runA2AKeySeekerAgent

Important endpoints:

    GET /admin/maze
    POST /admin/maze/reset
    GET /vocab
    GET/POST linked-data graph paths such as /maze, /cells/...
    GET/POST /sparql
    WebSocket /ws

## Plan of Work

Only open work remains:

1. Decide vocabulary namespace policy:

        rg -n "example.org|UI_NS|MASE_NS|STIGMERGY_NS|a2a:" src scenarios docs

   Either accept the `example.org` namespaces as local experimental IRIs or replace them with stable project-owned IRIs before treating `/vocab` as public API.

2. Add `/vocab` HTTP coverage if server API tests are broadened:

        rg -n "WebServerFactory|VocabularyResource|/vocab|Jersey|Jetty" src/test src/main

   Current validation parses [maze.ttl](docs/maze.ttl), compiles route registration, and verifies distribution copying. It does not start Jetty and issue an HTTP request.

## Concrete Steps

For ordinary server changes:

1. Read this plan and [AGENTS.md](AGENTS.md).
2. Keep scenario files inside the owning scenario folder.
3. Keep `rules/` and `rules-disabled/` semantics unchanged unless the scenario contract is explicitly revised.
4. Update this plan only when an open task is completed or a durable server-wide decision changes.

## Validation and Acceptance

Baseline validation commands:

    cd mase-server
    .\gradlew.bat test
    .\gradlew.bat installDist

Focused validation from the latest server-baseline work:

    cd mase-server
    .\gradlew.bat test --tests "org.maze.infrastructure.storage.MazeRuleLoaderPackageTest" --tests "org.maze.infrastructure.scenario.ScenarioPackageResolverTest" --tests "org.maze.application.MazeLayoutServiceTest" --tests "org.maze.infrastructure.scenario.ScenarioPackageStartupTest" --tests "org.maze.api.vocab.VocabularyDocumentTest"
    .\gradlew.bat installDist
    Test-Path build\install\mase-server\docs\maze.ttl

Expected result: Gradle reports `BUILD SUCCESSFUL`, and the final path check returns `True`.

## Idempotence and Recovery

Scenario loading is read-only. Starting the server must not rewrite `scenario.properties`, `manifest.json`, data files, or rule files.

If startup fails, fix the scenario folder and restart. Useful failure categories are missing `scenario.properties`, missing or invalid `mase.init.dataset`, no active `.rq` files under `rules/`, invalid SPARQL, and RDF parsing errors.

If rule behavior differs across platforms, compare the logged final rule order. Rule discovery and rule loading are intended to be stable across normal Windows, macOS, and Linux checkouts.

## Artifacts and Notes

- Server docs: [README.md](README.md)
- Root scenario docs: [../README.md](../README.md)
- CCRS scenario docs: [../CCRS-SCENARIO.md](../CCRS-SCENARIO.md)
- Vocabulary source: [maze.ttl](docs/maze.ttl)
- Vocabulary route: [VocabularyResource.java](src/main/java/org/maze/api/vocab/VocabularyResource.java)
- Scenario resolver: [ScenarioPackageResolver.java](src/main/java/org/maze/infrastructure/scenario/ScenarioPackageResolver.java)
- Rule loader: [MazeRuleLoader.java](src/main/java/org/maze/infrastructure/storage/MazeRuleLoader.java)
- Layout service: [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java)

## Interfaces and Dependencies

- [Configurator.java](src/main/java/org/maze/Configurator.java) owns scenario selection.
- [ServerConfiguration.java](src/main/java/org/maze/infrastructure/config/ServerConfiguration.java) reads scenario-local properties.
- [WebServerFactory.java](src/main/java/org/maze/infrastructure/web/WebServerFactory.java) wires admin, linked-data, SPARQL, vocabulary, and WebSocket services.
- [MazeResetService.java](src/main/java/org/maze/application/MazeResetService.java) owns reset.
- [ResourceIriResolver.java](src/main/java/org/maze/infrastructure/web/ResourceIriResolver.java) maps browser-facing request paths back to canonical RDF graph IRIs.

Revision note, 2026-05-17: Compacted this plan after the scenario-folder migration, admin reset, rule sorting, layout consolidation, and vocabulary serving work became the accepted baseline. Historical migration details were removed; open work and durable decisions remain.

Revision note, 2026-05-19: Marked creator WP6 package export alignment complete and recorded the first creator-generated package shape.
