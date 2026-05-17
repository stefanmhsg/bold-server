# PLAN_MASE_SERVER: Scenario-package-only server

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

No `PLANS.md` or `.agent/PLANS.md` guide is currently checked into this repository. This plan follows the local `PLAN_<SCOPE>.md` convention from the Codex exec-plan guidance.

This is the directory-scope plan for `mase-server`. Feature-specific server plans may exist beside it, such as [PLAN_ADMIN_RESET.md](PLAN_ADMIN_RESET.md), and should be referenced here when their work affects the server-wide architecture. The creator-side feature plan for package export is [../mase-creator/MASE-CREATOR.md](../mase-creator/MASE-CREATOR.md), especially WP6 and WP8.

## Purpose / Big Picture

`mase-server` now treats a scenario folder as the unit of runtime ownership. A developer or scenario author can add, run, reset, and rerun a maze scenario by placing one folder under [scenarios](scenarios) or by passing any scenario folder path at startup.

The folder contains scenario data, runtime properties, SPARQL rules, documentation, metadata, validation assets, and agent launch notes. Adding a new scenario should not require editing Java server startup code, adding root-level runtime files, or relying on hidden shared rule folders.

The current server flow is:

    cd mase-server
    ./gradlew runMase

or:

    cd mase-server
    ./gradlew runMase --args="--scenario scenarios/ccrs"

With no arguments, `Configurator` checks `MASE_SCENARIO_DIR` and otherwise defaults to `scenarios/smallmaze`.

## Progress

- [x] (2026-05-17 15:41+02:00) Investigated creator WP6/WP8 notes, existing server docs, startup configuration, data loading, rule loading, Docker entry points, and example-agent coupling.
- [x] (2026-05-17 15:41+02:00) Created this `mase-server` directory-scope ExecPlan and cross-linked the feature-specific creator and admin reset plans.
- [x] (2026-05-17 16:51+02:00) Clarified the rule package contract: every `.rq` file under `rules/` is active and loaded recursively; `rules-disabled/` is ignored and reserved for deactivated or work-in-progress rules.
- [x] (2026-05-17 17:19+02:00) Defined and documented package mode in [README.md](README.md): `--scenario <folder>`, `MASE_SCENARIO_DIR`, recursive active `rules/`, ignored `rules-disabled/`, and built-in [scenarios](scenarios).
- [x] (2026-05-17 17:19+02:00) Added `ScenarioPackage` and `ScenarioPackageResolver` for package-local `scenario.properties`, manifest, data validation, recursive active rules, validation assets, and agents directory detection.
- [x] (2026-05-17 17:19+02:00) Confirmed `mase.server.protocol = ldp` had no runtime effect and removed it from configuration APIs, docs, and package examples.
- [x] (2026-05-17 17:19+02:00) Refactored `MazeRuleLoader` and `MazeRuleService` so package mode loads explicit filesystem `.rq` files from the selected scenario package.
- [x] (2026-05-17 17:19+02:00) Added built-in scenario packages for SmallMaze, MidMaze, BigMaze, MaseCreator, and CCRS.
- [x] (2026-05-17 17:19+02:00) Added package-mode tests for resolver errors, recursive rules, disabled rules, package configuration, built-in SmallMaze startup, and CCRS rule ordering.
- [x] (2026-05-17 18:06+02:00) Checked active rule discovery for cross-platform concerns against the implementation and official Java file-system API docs.
- [x] (2026-05-17 18:31+02:00) Simplified the scenario contract by removing package-local `.env.example` files and moving scenario-owned launch defaults into `scenario.properties`.
- [x] (2026-05-17) Added canonical RDF resource IRI resolution for Linked Data dereferencing so browser-facing hosts such as `localhost` can still look up graphs loaded under the stable `MASE_SERVER_BASE_URI`.
- [x] (2026-05-17) After user confirmation that package mode works, removed legacy startup parsing, root-level runtime assets, and shared resource-rule loading.
- [x] (2026-05-17) Moved scenario-specific Java agents into package-owned source trees and updated package manifests, package README files, and Gradle JavaExec tasks.
- [x] (2026-05-17) Revalidated package-only server build, tests, and distribution after the agent move and cleanup.
- [ ] Update creator WP6 package export expectations to match the final server package contract.
- [ ] Investigate whether active rule sorting should normalize separators and case for byte-for-byte stable rule traces across Windows, macOS, and Linux.
- [ ] Investigate [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java) layout selection: compare the generic layout and CCRS layout, then decide whether the CCRS layout can safely cover all scenarios so the type-based split can be removed.
- [ ] Expand [maze.ttl](docs/maze.ttl) so the checked-in vocabulary covers the domain, dynamic maze, UI, event, error, scenario package, and A2A-related terms that now appear in code, rules, and data.
- [ ] Serve [maze.ttl](docs/maze.ttl) as a dereferenceable vocabulary from the server with RDF content negotiation.

## Surprises & Discoveries

- Observation: Scenario package support was feasible without changing RDF4J, HTTP resources, or the linked-data API shape. The main coupling was startup file resolution and rule discovery.
  Evidence: [Configurator.java](src/main/java/org/maze/Configurator.java) wires configuration, data loading, `MazeRuleService`, and web startup in one place.

- Observation: Package-local SmallMaze data and rules can initialize the in-memory store and produce the expected 5x5 layout with UI snapshot data.
  Evidence: `ScenarioPackageStartupTest.packagedSmallMazeLoadsDataAndRunsStartupRules` loads [scenarios/smallmaze](scenarios/smallmaze), runs startup rules, and passes.

- Observation: Package-local CCRS rule ordering matches the previous ordering intent while using package-relative rule names.
  Evidence: `ScenarioPackageStartupTest.packagedCcrsUsesPackageLocalRulesInExpectedOrder` observes `rules/global/normalize_maze_locks`, `rules/scenario/ccrs`, `rules/scenario/unlock-keys`, `rules/scenario/cleanup-move-success-events`, and `rules/global/move` in the expected order.

- Observation: Active package rule discovery is mostly insulated from Java glob separator differences because package mode derives an active root and walks it with `Files.walk`, then filters by the `.rq` extension.
  Evidence: [ScenarioPackageResolver.java](src/main/java/org/maze/infrastructure/scenario/ScenarioPackageResolver.java) resolves the active root and calls `discoverFilesByExtension`; [MazeRuleLoader.java](src/main/java/org/maze/infrastructure/storage/MazeRuleLoader.java) also uses `Files.walk` for package rules.

- Observation: Package rule membership is robust across Windows, macOS, and Linux for normal directories, but exact ordering and duplicate-name behavior still need a portability pass if rule trace order must be byte-for-byte stable across platforms.
  Evidence: Package rule names are normalized with `replace('\\', '/')` when loading, while discovery currently sorts paths before that normalization.

- Observation: Maze layout selection is controlled by RDF data, not by package properties. The service checks whether `<http://127.0.1.1:8080/maze>` has RDF type `maze:CcrsMaze`; changing `mase.scenario.id` or other `scenario.properties` values will not affect the layout algorithm.
  Evidence: [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java) calls `isCcrsMaze(conn)` before choosing `calculateCcrsLayout` or `calculateLayout`, and `getMazeScenarioName()` also derives the visible scenario name from RDF types.

- Observation: Public transport hosts and RDF graph identity must be decoupled for Docker.
  Evidence: [ResourceIriResolver.java](src/main/java/org/maze/infrastructure/web/ResourceIriResolver.java) maps Linked Data `/maze`, `/cells/...`, and `/agents/...` request paths back to the canonical RDF base before graph lookup.

## Decision Log

- Decision: Treat this file as the `mase-server` umbrella plan, not as a replacement for feature-specific plans.
  Rationale: Scenario packaging, admin reset, transaction tracing, agent launch behavior, and viewer integration are related but not the same deliverable. A server-scope plan gives future sessions one entry point and can point to narrower plans for implementation detail.
  Date/Author: 2026-05-17 / Codex

- Decision: All `.rq` files under `rules/` are active and loaded recursively; `rules-disabled/` is the standard non-loaded holding area.
  Rationale: Scenario authors should be free to organize active rules directly under `rules/` or in nested subdirectories without changing server configuration. Rules that should be deactivated or kept as work in progress belong under `rules-disabled/`, which package mode ignores by default.
  Date/Author: 2026-05-17 / Codex

- Decision: Use `scenario.properties` as the authoritative runtime configuration file inside a package, with `manifest.json` reserved for metadata, generated-file lists, warnings, and creator provenance.
  Rationale: The server already uses Java properties for runtime values such as dataset, trace mode, and rule order. Keeping runtime configuration in properties minimizes parser changes and keeps package startup easy to inspect.
  Date/Author: 2026-05-17 / Codex

- Decision: Do not carry `mase.server.protocol` into the package contract.
  Rationale: The audit found no runtime behavior. Removing the flag reduces scenario boilerplate and avoids implying that other protocol modes exist.
  Date/Author: 2026-05-17 / Codex

- Decision: Scenario-owned Java agents remain separate Java processes and are compiled from package-owned source directories by Gradle.
  Rationale: The server should not compile or load arbitrary scenario Java at runtime. Package-owned source keeps constants and launch notes near the scenario while preserving normal Java compile/test checks.
  Date/Author: 2026-05-17 / Codex

- Decision: Retire legacy startup and split runtime assets after user-confirmed package behavior.
  Rationale: The user confirmed package mode works, so the server can now enforce the package contract rather than preserving a parallel compatibility surface.
  Date/Author: 2026-05-17 / Codex

- Decision: Keep `MASE_SERVER_BASE_URI` as the canonical RDF identity base and allow browser-facing HTTP hosts to vary.
  Rationale: RDF graph names, rules, and scenario data need one stable identity base, while Docker and local browsers may need different transport hosts.
  Date/Author: 2026-05-17 / Codex

## Outcomes & Retrospective

`mase-server` is now package-only for scenario startup. `ServerConfiguration` loads the selected package's `scenario.properties`, `Configurator` rejects legacy task-name arguments with a clear message, package-local rules are loaded from filesystem paths, and the distribution copies package folders rather than split runtime asset trees.

The server ships built-in package copies for SmallMaze, MidMaze, BigMaze, MaseCreator, and CCRS. `mase.server.protocol = ldp` was removed end to end after the audit showed it had no runtime effect. Scenario-specific Java agents now live under the scenario packages and can be run through package-specific Gradle JavaExec tasks.

## Current Scenario Contract

A scenario package has this shape:

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

Paths in `scenario.properties` are resolved relative to the package root. A normal package configuration is:

    mase.scenario.id = ccrs
    mase.init.dataset = data/CcrsMazeV1.trig
    mase.rules.execution.order = normalize_maze_locks*, ccrs*, unlock*, cleanup*, move*
    # Transaction trace mode: off, summary headers/rule count, or full per-triple debug diffs
    mase.transaction.trace = summary

The `rules/` directory is the active rule tree. Every `.rq` file below `rules/` is loaded recursively, whether it is directly in `rules/` or in any nested subdirectory. Subdirectories inside `rules/` are only for organization, not activation control. A package may keep draft, alternative, deactivated, or work-in-progress rules in `rules-disabled/`; package mode ignores that directory by default.

The `manifest.json` is not required to start the server. It carries package metadata, creator/export provenance, generated-file lists, warnings, and optional agent descriptors. Scenario-local runtime and agent launch parameters belong in `scenario.properties`.

## Concrete Next Steps

1. Update creator export expectations:

        rg -n "WP6|scenario package|rules-disabled|scenario.properties|manifest" mase-creator

   Align [../mase-creator/MASE-CREATOR.md](../mase-creator/MASE-CREATOR.md) with the package-only server contract.

2. Investigate layout algorithm consolidation:

        rg -n "isCcrsMaze|calculateCcrsLayout|calculateLayout|getMazeScenarioName" mase-server/src/main/java/org/maze/application/MazeLayoutService.java mase-server/src/test/java

   Compare the generic and CCRS layout paths across ordinary grids, disconnected components, one-way links, incoming-only references, and coordinate collisions.

3. Expand the vocabulary source:

        rg -n "MazeVocab|MAZE_NS|DYNMAZE_NS|UI_NS|MASE_NS|a2a:|ui:|dyn:|stig:" mase-server/src mase-server/scenarios mase-server/docs

   Update [maze.ttl](docs/maze.ttl) with terms discovered in code, rules, and data.

4. Serve the vocabulary dereferenceably:

        rg -n "ResourceConfig|LinkedDataDereferenceResource|Produces|text/turtle|RDFValueFormats" mase-server/src/main/java

   Add a route for the vocabulary IRI or a stable local vocabulary endpoint that returns [maze.ttl](docs/maze.ttl) as `text/turtle`, with content negotiation for other RDF serializations if the existing RDF format helpers make that cheap.

5. Normalize rule sorting if traces must be identical across platforms:

        rg -n "Comparator.comparing\\(Path::toString\\)|replace\\\\\\\\|relativize" mase-server/src/main/java/org/maze/infrastructure

   Prefer sorting by slash-normalized package-relative names before rule loading.

## Validation and Acceptance

The server package feature is accepted when a new scenario can be added as a folder under [scenarios](scenarios) and started without editing Java server startup code or adding runtime files outside the package. The server must load the package-local `scenario.properties`, data files, and rule files. Missing package files must produce messages that include the scenario root and the missing relative path.

Package startup is accepted when:

    cd mase-server
    ./gradlew runMase --args="--scenario scenarios/smallmaze"

boots a packaged SmallMaze copy and `GET /admin/maze` returns a valid admin snapshot.

Rule isolation is accepted when package mode runs with duplicated package-local rules and no implicit shared rule lookup. A package can organize active rules into subfolders, but all loaded rule files must be under the selected package's `rules/` tree.

Rule deactivation is accepted when draft or temporarily disabled rules can be kept in `rules-disabled/` without being loaded accidentally. The server does not support partial activation inside `rules/` in the first package contract; activation is controlled by moving files between `rules/` and `rules-disabled/`.

Reset compatibility is accepted when `POST /admin/maze/reset` reloads the selected package-local data and package-local rules.

Agent packaging is accepted for this phase when scenario docs and Gradle JavaExec tasks can run agents as separate Java processes against the selected server. Full dynamic Java agent loading from scenario folders is not required and should not be implemented in the server runtime until there is a concrete security and build model.

Vocabulary work is accepted when [maze.ttl](docs/maze.ttl) includes the public RDF terms currently emitted or consumed by server code, scenario rules, package metadata, viewer events, and RDF error responses, and when the running server can dereference the vocabulary with an RDF response.

Layout consolidation is accepted only if focused tests show that one algorithm preserves current behavior for ordinary mazes and CCRS mazes, including disconnected components and incoming references. If the CCRS algorithm becomes the only layout path, the RDF type should no longer decide layout behavior.

## Idempotence and Recovery

Scenario package loading should be read-only. Starting the server in package mode must not rewrite `scenario.properties`, `manifest.json`, `.rq` files, or data files. Repeated starts with the same package should produce the same repository state, subject only to runtime mutations after startup.

If package startup fails, the error should identify whether the failure came from missing `scenario.properties`, missing or invalid `mase.init.dataset`, no active rule files, invalid SPARQL, or RDF parsing failure. Fix the package file and restart. No rollback of source files should be necessary.

If a package rule behaves differently from an earlier run, compare the final ordered rule list in startup logs. Rule names should be stable enough to show whether a duplicated rule is missing, ordered differently, or loaded from the wrong path.

## Artifacts and Notes

Current package-owned agent tasks:

    runSmallMazeBobAgent
    runMidMazeBobAgent
    runBigMazeBobAgent
    runMaseCreatorBobAgent
    runCcrsAgent
    runKeyHolderAgent
    runA2AKeySeekerAgent

Current scenario-owned launch defaults:

    scenarios/ccrs/scenario.properties
    CCRS_AGENT_DISPATCH_INTERVAL_MS = 5000
    CCRS_AGENT_REQUEST_TIMEOUT_SECONDS = 60
    MASE_KEYHOLDER_PORT = 8095
    MASE_KEYHOLDER_BIND_HOST = 0.0.0.0
    MASE_KEYHOLDER_PUBLIC_BASE_URL = http://127.0.0.1:8095
    KEYHOLDER_BASE_URL = http://127.0.0.1:8095

Cross-platform rule discovery note:

    Official Java docs confirm that Path is system-dependent and FileSystem glob matching works on the Path string representation. The active package rule loader avoids the riskiest part of that API by walking the active rules directory and filtering `.rq` files. Keep slash-normalized rule names for diagnostics, and consider slash-normalized sorting if exact cross-platform trace order matters.

External references checked:

    Oracle Java 21 FileSystem.getPathMatcher docs: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)
    Oracle Java 21 Files.walk docs: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#walk(java.nio.file.Path,java.nio.file.FileVisitOption...)
    Oracle Java 21 Path docs: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html
    Apple APFS FAQ: https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/APFS_Guide/FAQ/FAQ.html

Validation evidence from the package-mode milestone:

    cd mase-server
    ./gradlew compileJava
    BUILD SUCCESSFUL

    cd mase-server
    ./gradlew test --tests "org.maze.infrastructure.scenario.ScenarioPackageResolverTest" --tests "org.maze.infrastructure.storage.MazeRuleLoaderPackageTest" --tests "org.maze.infrastructure.config.ServerConfigurationTest"
    BUILD SUCCESSFUL

    cd mase-server
    ./gradlew test --tests "org.maze.infrastructure.scenario.ScenarioPackageStartupTest"
    BUILD SUCCESSFUL

    cd mase-server
    ./gradlew test
    BUILD SUCCESSFUL

    cd mase-server
    ./gradlew installDist
    BUILD SUCCESSFUL

Validation evidence from the package-only cleanup:

    cd mase-server
    .\gradlew.bat compileJava
    BUILD SUCCESSFUL

    cd mase-server
    .\gradlew.bat test
    BUILD SUCCESSFUL

    cd mase-server
    .\gradlew.bat installDist
    BUILD SUCCESSFUL

    Test-Path mase-server\build\install\mase-server\scenarios\smallmaze\scenario.properties
    True

    Test-Path mase-server\build\install\mase-server\data
    False

    Test-Path mase-server\build\install\mase-server\src\main\resources\rules
    False

Revision note, 2026-05-17: Initiated this server-scope plan after investigating creator WP6 and current `mase-server` feasibility.

Revision note, 2026-05-17: Added the requested `mase.server.protocol = ldp` audit/removal task and clarified that nested folders under the active `rules/` tree are loaded.

Revision note, 2026-05-17: Tightened the rule contract per user direction: every `.rq` file under `rules/` is loaded recursively, regardless of subdirectory structure, and `rules-disabled/` is the explicit deactivation and work-in-progress area.

Revision note, 2026-05-17: Implemented package mode in `mase-server`, added built-in package copies, removed dead protocol runtime configuration, documented package commands in [README.md](README.md), and recorded successful compile/test/installDist validation.

Revision note, 2026-05-17: Added cross-platform rule-discovery findings from official Java file-system docs, added the maze layout consolidation investigation, made agent migration explicit, and gated scenario-package-only cleanup on user-confirmed package behavior.

Revision note, 2026-05-17: Simplified the scenario contract by removing `.env.example` from packages, moved CCRS agent launch defaults into [scenario.properties](scenarios/ccrs/scenario.properties), added a Gradle bridge from those properties to agent environment variables, and added vocabulary expansion plus dereferenceable serving tasks for [maze.ttl](docs/maze.ttl).

Revision note, 2026-05-17: Added canonical RDF resource IRI resolution for Linked Data dereferencing.

Revision note, 2026-05-17: After user confirmation that scenario packages work, completed the package-only refactor by moving agents into scenario-owned source trees and removing the split legacy runtime structures.

Revision note, 2026-05-17: Revalidated package-only cleanup with compile, full tests, installDist, and distribution path checks.
