# PLAN_MASE_SERVER: Load self-contained scenario packages

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

No `PLANS.md` or `.agent/PLANS.md` guide is currently checked into this repository. This plan follows the local `PLAN_<SCOPE>.md` convention from the Codex exec-plan guidance.

This is the directory-scope plan for `mase-server`. Feature-specific server plans may exist beside it, such as [PLAN_ADMIN_RESET.md](PLAN_ADMIN_RESET.md), and should be referenced here when their work affects the server-wide architecture. The creator-side feature plan for package export is [../mase-creator/MASE-CREATOR.md](../mase-creator/MASE-CREATOR.md), especially WP6 and WP8.

## Purpose / Big Picture

After this work is implemented, a developer or scenario author can add, run, reset, and rerun a maze scenario by placing one scenario folder under `mase-server` or by passing a scenario folder path at startup. The folder contains the scenario data, runtime properties, SPARQL rules, documentation, metadata, validation assets, and agent launch notes needed for that scenario. Adding a new scenario should not require editing Java server code, copying a TriG file into one location and rule files into another, or relying on shared rule folders hidden in `src/main/resources`.

The user-visible result is that both of these flows work:

    cd mase-server
    ./gradlew runMase --args="sim-SmallMaze"

and:

    cd mase-server
    ./gradlew runMase --args="--scenario scenarios/smallmaze"

The first command is the current compatibility path. The second command is the new scenario package path. In package mode the server loads only files inside the selected scenario folder, plus normal server code and libraries. Scenario rules that used to live under `src/main/resources/rules/Global` are copied into each scenario package that needs them, because duplicated package-local rules are clearer than hidden shared runtime behavior.

## Progress

- [x] (2026-05-17 15:41+02:00) Investigated creator WP6/WP8 notes, existing server docs, startup configuration, data loading, rule loading, Docker entry points, and example-agent coupling.
- [x] (2026-05-17 15:41+02:00) Created this `mase-server` directory-scope ExecPlan and cross-linked the feature-specific creator and admin reset plans.
- [x] (2026-05-17 16:51+02:00) Clarified the rule package contract: every `.rq` file under `rules/` is active and loaded recursively; `rules-disabled/` is ignored and reserved for deactivated or work-in-progress rules.
- [x] (2026-05-17 17:19+02:00) Defined and documented package mode in [README.md](README.md): `--scenario <folder>`, `MASE_SCENARIO_DIR`, recursive active `rules/`, ignored `rules-disabled/`, and built-in [scenarios](scenarios).
- [x] (2026-05-17 17:19+02:00) Added `ScenarioPackage` and `ScenarioPackageResolver` for package-local `scenario.properties`, manifest, data validation, recursive active rules, validation assets, and agents directory detection.
- [x] (2026-05-17 17:19+02:00) Refactored `ServerConfiguration` and `Configurator` so legacy `sim-*` startup still works and package mode starts with `--scenario <folder>` or `MASE_SCENARIO_DIR`.
- [x] (2026-05-17 17:19+02:00) Confirmed `mase.server.protocol = ldp` had no runtime effect and removed it from configuration APIs, legacy properties, docs, and package examples.
- [x] (2026-05-17 17:19+02:00) Refactored `MazeRuleLoader` and `MazeRuleService` so package mode loads explicit filesystem `.rq` files from the selected scenario package.
- [x] (2026-05-17 17:19+02:00) Added built-in scenario packages for SmallMaze, MidMaze, BigMaze, MaseCreator, and CCRS while leaving legacy files in place.
- [x] (2026-05-17 17:19+02:00) Added package-mode tests for resolver errors, recursive rules, disabled rules, package configuration, built-in SmallMaze startup, and CCRS rule ordering.
- [x] (2026-05-17 17:19+02:00) Chose the first scenario-owned agent shape: package-local `agents/README.md`, package-local `scenario.properties` launch defaults, and manifest metadata describe independent Java process launches; no dynamic Java loading.
- [x] (2026-05-17 18:06+02:00) Checked active rule discovery for cross-platform concerns against the implementation and official Java file-system API docs.
- [x] (2026-05-17 18:31+02:00) Simplified the scenario contract by removing package-local `.env.example` files and moving scenario-owned launch defaults into `scenario.properties`.
- [x] (2026-05-17) Added canonical RDF resource IRI resolution for Linked Data dereferencing so browser-facing hosts such as `localhost` can still look up graphs loaded under the stable `MASE_SERVER_BASE_URI`.
- [ ] Update creator WP6 package export expectations to match the server package contract once the server loader is implemented.
- [ ] Investigate whether active rule sorting should normalize separators and case for byte-for-byte stable rule traces across Windows, macOS, and Linux.
- [ ] Investigate [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java) layout selection: compare the generic layout and CCRS layout, then decide whether the CCRS layout can safely cover all scenarios so the type-based split can be removed.
- [ ] Migrate scenario-specific agent implementations and launch definitions away from the legacy [examples package](src/main/java/org/maze/examples) into scenario-package-owned artifacts or a clearly shared agent module.
- [ ] After the user has tested package mode and confirmed behavior, remove or retire legacy scenario assets and startup paths toward a scenario-package-only server.
- [ ] Expand [maze.ttl](docs/maze.ttl) so the checked-in vocabulary covers the domain, dynamic maze, UI, event, error, scenario package, and A2A-related terms that now appear in code, rules, and data.
- [ ] Serve [maze.ttl](docs/maze.ttl) as a dereferenceable vocabulary from the server with RDF content negotiation.

## Surprises & Discoveries

- Observation: Scenario package support is feasible without changing RDF4J, HTTP resources, or the linked-data API shape. The main coupling is startup file resolution and rule discovery.
  Evidence: [Configurator.java](src/main/java/org/maze/Configurator.java) wires configuration, data loading, `MazeRuleService`, and web startup in one place.

- Observation: The data loader already accepts direct filesystem paths and wildcard patterns, and the current reset work added transaction-friendly loading into an existing connection.
  Evidence: [DataLoader.java](src/main/java/org/maze/infrastructure/rdf/DataLoader.java) has `loadData(RepositoryConnection conn, String datasetPattern, URI baseUri)` and uses `FileUtils.listFiles(datasetPattern)`.

- Observation: Rule loading is the largest blocker for self-contained packages.
  Evidence: [MazeRuleLoader.java](src/main/java/org/maze/infrastructure/storage/MazeRuleLoader.java) uses `getResourceAsStream("/rules/" + filename)` for loading and discovers files by constructing `src/main/resources/rules/.../*.rq`.

- Observation: Current startup always derives a scenario-specific rules directory from the task name and always adds `Global` as the base additional ruleset.
  Evidence: `Configurator.extractMazeName("sim-CcrsMaze")` returns `CcrsMaze`, `Configurator.buildRulesetPaths(...)` always adds `Global`, and `MazeRuleService` discovers rules for both the maze name and additional rulesets.

- Observation: Current runtime properties are root-level `sim-*.properties` files, not scenario-local files.
  Evidence: `ServerConfiguration` opens `taskName + ".properties"` with `new FileInputStream(configFile)`.

- Observation: `mase.server.protocol` appears to be read by `ServerConfiguration` but not used elsewhere in the current Java code.
  Evidence: Searching for `getServerProtocol`, `mase.server.protocol`, and `SERVER_PROTOCOL` found only [ServerConfiguration.java](src/main/java/org/maze/infrastructure/config/ServerConfiguration.java) and the root `sim-*.properties` files.

- Observation: Package-local SmallMaze data and rules can initialize the in-memory store and produce the same expected 5x5 layout with UI snapshot data.
  Evidence: `ScenarioPackageStartupTest.packagedSmallMazeLoadsDataAndRunsStartupRules` loads [scenarios/smallmaze](scenarios/smallmaze), runs startup rules, and passes.

- Observation: Package-local CCRS rule ordering matches the legacy ordering intent while using package-relative rule names.
  Evidence: `ScenarioPackageStartupTest.packagedCcrsUsesPackageLocalRulesInExpectedOrder` observes `rules/global/normalize_maze_locks`, `rules/scenario/ccrs`, `rules/scenario/unlock-keys`, `rules/scenario/cleanup-move-success-events`, and `rules/global/move` in the expected order.

- Observation: Example agents are independent Java processes already, but their source and Gradle tasks are still compiled inside `mase-server` and several agents contain scenario-specific constants.
  Evidence: [build.gradle](build.gradle) defines `runCcrsAgent`, `runKeyHolderAgent`, and `runA2AKeySeekerAgent`; [CcrsAgent.java](src/main/java/org/maze/examples/CcrsAgent.java) and [KeyHolderAgent.java](src/main/java/org/maze/examples/KeyHolderAgent.java) contain hard-coded base URIs, guided coordinates, key values, target coordinates, and A2A defaults.

- Observation: The existing `.env.example` files added clutter without giving the server a stronger contract. The only non-empty built-in file held CCRS agent launch defaults, while the server already has a package-local `scenario.properties` file and `ServerConfiguration.getRawProperties()` can expose extra keys without breaking startup.
  Evidence: [scenario.properties](scenarios/ccrs/scenario.properties) now carries CCRS agent launch defaults; [build.gradle](build.gradle) maps those package-local keys into environment variables for the existing CCRS JavaExec tasks.

- Observation: Active package rule discovery is mostly insulated from Java glob separator differences because package mode derives an active root and walks it with `Files.walk`, then filters by the `.rq` extension. It does not pass `rules/**/*.rq` directly to `FileSystem.getPathMatcher`.
  Evidence: [ScenarioPackageResolver.java](src/main/java/org/maze/infrastructure/scenario/ScenarioPackageResolver.java) resolves the active root and calls `discoverFilesByExtension`; [MazeRuleLoader.java](src/main/java/org/maze/infrastructure/storage/MazeRuleLoader.java) also uses `Files.walk` for package rules. The Oracle Java 21 `FileSystem.getPathMatcher` docs describe glob matching against the `Path` string representation and show different Unix and Windows separator examples.

- Observation: Package rule membership is robust across Windows, macOS, and Linux for normal directories, but exact ordering and duplicate-name behavior still need a portability pass if rule trace order must be byte-for-byte stable across platforms. Sorting currently uses `Path::toString`, which can expose platform separators; case-insensitive filesystems such as common Windows and macOS volumes can also reject or merge names that Linux treats as distinct. Apple documents APFS as available in case-sensitive and case-insensitive variants on macOS, with case-insensitive as the default.
  Evidence: [ScenarioPackageResolver.java](src/main/java/org/maze/infrastructure/scenario/ScenarioPackageResolver.java) and [MazeRuleLoader.java](src/main/java/org/maze/infrastructure/storage/MazeRuleLoader.java) sort paths with `Comparator.comparing(Path::toString)`, while package rule names are later normalized with `replace('\\', '/')` when loading.

- Observation: Maze layout selection is controlled by RDF data, not by package properties. The service checks whether `<http://127.0.1.1:8080/maze>` has RDF type `maze:CcrsMaze`; changing `mase.scenario.id` or other `scenario.properties` values will not affect the layout algorithm.
  Evidence: [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java) calls `isCcrsMaze(conn)` before choosing `calculateCcrsLayout` or `calculateLayout`, and `getMazeScenarioName()` also derives the visible scenario name from RDF types.

- Observation: Public transport hosts and RDF graph identity must be decoupled for Docker.
  Evidence: Viewer requests to `http://localhost:8080/cells/...` reached the server, but repository lookup 404ed because the dataset was loaded under `http://127.0.1.1:8080/cells/...`. [ResourceIriResolver.java](src/main/java/org/maze/infrastructure/web/ResourceIriResolver.java) maps Linked Data `/maze`, `/cells/...`, and `/agents/...` request paths back to the canonical RDF base before graph lookup.

- Observation: The worktree contains reset-related implementation files even though `PLAN_ADMIN_RESET.md` still lists reset implementation tasks as pending.
  Evidence: `git status --short` shows uncommitted changes and untracked `MazeResetService.java`, `MazeMutationCoordinator.java`, and `MazeResetServiceTest.java`.

## Decision Log

- Decision: Treat this file as the `mase-server` umbrella plan, not as a replacement for feature-specific plans.
  Rationale: Scenario packaging, admin reset, transaction tracing, agent launch behavior, and viewer integration are related but not the same deliverable. A server-scope plan gives future sessions one entry point and can point to narrower plans for implementation detail.
  Date/Author: 2026-05-17 / Codex

- Decision: Scenario package mode must not implicitly load shared `Global` rules from `src/main/resources/rules`.
  Rationale: The requested target is a self-contained scenario folder where all scenario-related files are bundled together. Duplicating common movement, UI, lock, and cleanup rules inside each package is acceptable and clearer than invisible shared behavior.
  Date/Author: 2026-05-17 / Codex

- Decision: Keep legacy `sim-*` task startup during migration.
  Rationale: Existing demos, tests, Docker commands, and documentation depend on `sim-SmallMaze`, `sim-CcrsMaze`, and the current root-level data/rules layout. Package mode should be additive until the packaged scenarios are proven.
  Date/Author: 2026-05-17 / Codex

- Decision: Use `scenario.properties` as the authoritative runtime configuration file inside a package, with `manifest.json` reserved for metadata, generated-file lists, warnings, and creator provenance.
  Rationale: The server already uses Java properties for runtime values such as dataset, protocol, trace mode, and rule order. Keeping runtime configuration in properties minimizes parser changes and keeps package startup easy to inspect. JSON metadata remains useful for creator/export tooling and UI display.
  Date/Author: 2026-05-17 / Codex

- Decision: Do not carry `mase.server.protocol` into the package contract unless the implementation audit proves it still has a real runtime effect.
  Rationale: Package configuration should not preserve inert compatibility flags. If `mase.server.protocol = ldp` is unused, removing it end to end reduces scenario boilerplate and avoids implying that other protocol modes exist.
  Date/Author: 2026-05-17 / Codex

- Decision: All `.rq` files under `rules/` are active and loaded recursively; `rules-disabled/` is the standard non-loaded holding area.
  Rationale: Scenario authors should be free to organize active rules directly under `rules/` or in any nested subdirectories without changing server configuration. Rules that should be deactivated or kept as work in progress belong under `rules-disabled/`, which package mode ignores by default.
  Date/Author: 2026-05-17 / Codex

- Decision: Treat scenario-owned Java agents as package-adjacent executable artifacts in the first server milestone, not as Java classes dynamically compiled or loaded by the server at runtime.
  Rationale: The server should not compile arbitrary scenario Java during startup. Agents can already run as separate Java processes through Gradle tasks, direct `java -cp`, or Docker services. The first package contract should make agent launch reproducible while keeping the server runtime focused on loading RDF, rules, and HTTP behavior. Scenario-owned launch defaults belong in `scenario.properties`, not a separate `.env.example` file.
  Date/Author: 2026-05-17 / Codex

- Decision: Package mode is selected with `--scenario <folder>` or `MASE_SCENARIO_DIR`, and additional legacy ruleset arguments are rejected in package mode.
  Rationale: This preserves the existing `sim-*` command surface while keeping package mode self-contained. Loading extra legacy rulesets beside a package would reintroduce hidden shared behavior.
  Date/Author: 2026-05-17 / Codex

- Decision: Do not remove legacy `sim-*` files, root `data/`, resource rules, or legacy startup forms until package mode has been manually tested and the user has confirmed equivalent behavior for the scenarios they rely on.
  Rationale: Package mode is now implemented, but legacy removal changes operational workflows and rollback options. Keeping cleanup as a gated follow-up avoids deleting compatibility paths before real runs prove that package mode covers the needed behavior.
  Date/Author: 2026-05-17 / Codex

- Decision: Keep `MASE_SERVER_BASE_URI` as the canonical RDF identity base and allow browser-facing HTTP hosts to vary.
  Rationale: RDF graph names, rules, and scenario data need one stable identity base, while Docker and local browsers may need different transport hosts. Canonicalizing Linked Data request paths on the server avoids tying graph lookup to `localhost`, container service names, or platform-specific host resolution.
  Date/Author: 2026-05-17 / Codex

## Outcomes & Retrospective

Scenario package mode is implemented for `mase-server`. Legacy `sim-*` startup remains available, and package startup can now use `--scenario scenarios/smallmaze` or `MASE_SCENARIO_DIR=scenarios/smallmaze`. Package mode resolves data and active rules relative to the selected scenario folder, loads every `.rq` under `rules/` recursively, and ignores `rules-disabled/` by default.

The server now ships built-in package copies for SmallMaze, MidMaze, BigMaze, MaseCreator, and CCRS. `mase.server.protocol = ldp` was removed end to end after the audit showed it had no runtime effect. Validation passed with `./gradlew compileJava`, focused package tests, `./gradlew test`, `./gradlew installDist`, and a distribution check that confirmed `build/install/mase-server/scenarios/smallmaze/scenario.properties` exists.

## Context and Orientation

`mase-server` is a Java 21 Gradle application. Its main class is `org.maze.Configurator` in [Configurator.java](src/main/java/org/maze/Configurator.java). A legacy run starts with a task name such as `sim-SmallMaze` or `sim-CcrsMaze`. `Configurator` constructs `ServerConfiguration`, which loads [sim-SmallMaze.properties](sim-SmallMaze.properties), [sim-CcrsMaze.properties](sim-CcrsMaze.properties), or another root-level file with that task name. The legacy properties file contains `mase.init.dataset`, `mase.transaction.trace`, and `mase.rules.execution.order`.

A package run starts with `--scenario <folder>` or, when no command-line arguments are supplied, `MASE_SCENARIO_DIR=<folder>`. In package mode, `ServerConfiguration` loads `scenario.properties` from the selected folder and uses `ScenarioPackageResolver` to resolve package-local data, rules, manifest, validation files, and agent notes.

A scenario package means a directory that carries everything scenario-specific needed by the runtime. In this plan, "scenario-specific" includes the TriG RDF dataset, the runtime properties, all SPARQL rule files required for the scenario, package metadata, scenario documentation, validation assets, and agent launch information. It does not include generic server Java classes, RDF4J, Jetty, Jersey, or other server libraries.

The current server layout stores scenario pieces in separate places:

- Root `sim-*.properties` files choose datasets and rule execution order.
- [data](data) stores RDF datasets.
- [Global rules directory](src/main/resources/rules/Global) stores rules implicitly shared by all scenarios.
- `src/main/resources/rules/<ScenarioName>` stores scenario-specific rules.
- [examples package](src/main/java/org/maze/examples) stores Java example agents and CCRS infrastructure agents.
- [build.gradle](build.gradle) defines `runMase` and agent-specific JavaExec tasks.
- [Dockerfile](Dockerfile) starts `bin/mase-server ${TASKNAME}` with `TASKNAME` defaulting to an empty string.

The target package layout should be explicit and local. The recommended first format is:

    scenarios/<scenario-id>/
      README.md
      scenario.properties
      manifest.json
      data/
        <scenario-id>.trig
      rules/
        *.rq
        any-subdirectory/
          *.rq
          deeper-subdirectory/
            *.rq
      rules-disabled/
        README.md
        *.rq
      validation/
        README.md
        *.rq
      agents/
        README.md
        run.ps1
        run.sh

In package mode, paths in `scenario.properties` are resolved relative to the package root. For example:

    mase.scenario.id = ccrs
    mase.init.dataset = data/CcrsMazeV1.trig
    mase.rules.path = rules/**/*.rq
    mase.rules.execution.order = normalize_maze_locks*, ccrs*, unlock*, cleanup*, move*
    # Transaction trace mode: off, summary headers/rule count, or full per-triple debug diffs
    mase.transaction.trace = summary

The `rules/` directory is the active rule tree. Every `.rq` file below `rules/` is loaded recursively, whether it is directly in `rules/` or in any nested subdirectory. Subdirectories inside `rules/` are only for organization, not activation control. A package may keep draft, alternative, deactivated, or work-in-progress rules in `rules-disabled/`; package mode must ignore that directory by default. If a user wants to reactivate a disabled rule, they move or copy it back under `rules/`.

The `manifest.json` should not be required to start the server in the first milestone. It should carry package metadata, creator/export provenance, generated-file lists, warnings, and optional agent descriptors. This keeps runtime startup robust even when a human hand-edits a package.

Scenario-local runtime and agent launch parameters belong in `scenario.properties`. A scenario package should not include `.env.example` just to hold default values; if legacy agent classes still read environment variables, the launch layer should translate package properties into those variables.

The existing reset feature affects this plan. `MazeResetService`, if present, resets the in-memory repository from the configured dataset pattern and then runs startup rules. In package mode it must use the scenario package's resolved dataset paths and package-local `MazeRuleService`. See [PLAN_ADMIN_RESET.md](PLAN_ADMIN_RESET.md) for reset-specific behavior and keep that plan updated if reset contracts change.

## Milestones

Milestone 1 creates a read-only scenario package resolver. Add a small package model that represents the selected scenario root, the runtime properties file, resolved data files, resolved rule files, optional manifest, optional validation assets, and optional agent notes. This milestone does not change normal startup. It is complete when tests can create a temporary package folder, call the resolver, and observe normalized paths plus clear errors for missing `scenario.properties`, missing data files, and empty rule sets.

Milestone 2 adds package-aware startup while preserving legacy startup. Extend `Configurator` so `./gradlew runMase --args="sim-SmallMaze"` still uses `sim-SmallMaze.properties`, while `./gradlew runMase --args="--scenario scenarios/smallmaze"` loads `scenarios/smallmaze/scenario.properties`. Also support an environment variable such as `MASE_SCENARIO_DIR` for Docker. This milestone is complete when the server can boot a packaged copy of SmallMaze and `GET /admin/maze` returns the same layout as the legacy SmallMaze run.

Milestone 3 replaces implicit shared rule loading in package mode. Refactor `MazeRuleLoader` so it can load rules from explicit filesystem `Path` values as well as legacy classpath resource names. Refactor `MazeRuleService` so one constructor or factory receives preloaded `MazeRule` instances or explicit rule source paths. Package mode must discover rules under the scenario folder and must not automatically add `Global`. This milestone is complete when a packaged scenario with duplicated movement and unlock rules runs without reading `src/main/resources/rules`.

Milestone 4 migrates built-in scenarios into package folders. Create package copies for SmallMaze, MidMaze, BigMaze, MaseCreator, and CcrsMaze under `mase-server/scenarios/`. Each package gets its own `scenario.properties`, data file copy or explicit local data path, README, manifest, and duplicated rule files. Keep legacy files in place during this milestone. It is complete when tests or manual runs prove that a packaged SmallMaze and packaged CcrsMaze produce expected admin snapshots and rule behavior.

Milestone 5 defines scenario-owned agent execution. Start pragmatically: each scenario package may include `agents/README.md`, `run.ps1`, `run.sh`, and optional manifest entries that document Java main classes, environment variables, delays, and Docker Compose service snippets. Do not make the server compile or load Java agents dynamically. This milestone is complete when CCRS package docs can start the server, infrastructure agents, keyholder A2A agent, and key seeker agent as independent Java processes without editing server source.

Milestone 6 aligns creator export with the server package contract. Update [../mase-creator/MASE-CREATOR.md](../mase-creator/MASE-CREATOR.md) WP6 details only after the server package loader contract is validated. The creator's "Create Scenario Package" action should emit a folder that `mase-server` package mode can run directly. This milestone is complete when a generated package can be copied into `mase-server/scenarios/`, started with `--scenario`, reset through the admin reset endpoint, and validated with a package smoke test.

Milestone 7 is the post-package cleanup and consolidation stage. It starts only after package mode has been tested with the built-in scenarios, reset flows, viewer expectations, and agent launch flows, and after the user confirms the behavior. This milestone investigates replacing the layout type split with one general layout algorithm, migrates scenario-specific agents out of [examples package](src/main/java/org/maze/examples), and retires legacy `sim-*`, root data, and resource-rule paths if no longer needed.

Milestone 8 formalizes and serves the vocabulary. Expand [maze.ttl](docs/maze.ttl) so it describes the terms actually used by the server, package contract, rules, viewer events, UI model, errors, and A2A-related agent metadata. Then expose it from the running server as a dereferenceable vocabulary with Turtle as the primary representation and content negotiation for other RDF formats if practical. This milestone is complete when a client can request the vocabulary IRI and receive RDF that matches the checked-in source file.

## Plan of Work

First, add a scenario package model under [scenario package](src/main/java/org/maze/infrastructure/scenario). `ScenarioPackageResolver` accepts a `Path` root and returns normalized paths. It rejects missing required files with actionable messages that name the missing path and the selected scenario root.

Next, separate "where configuration came from" from the current `ServerConfiguration` shape. Keep a legacy constructor for task names, but add a package-aware factory that reads `scenario.properties` from a known folder. The configuration object remembers the scenario root when package mode is active so dataset and rule paths can be resolved relative to that root. Avoid resolving package-relative paths against the process working directory after the initial scenario root is known.

As part of the configuration refactor, audit `mase.server.protocol`. The audit found no runtime behavior beyond `ServerConfiguration` parsing, so `SERVER_PROTOCOL_KEY`, `getServerProtocol()`, legacy property entries, package contract examples, and README references were removed.

Then, refactor rule loading. `MazeRuleLoader` supports loading rule text from filesystem paths in addition to classpath resources. Existing tests such as [CcrsMazeRuleTest.java](src/test/java/org/maze/application/CcrsMazeRuleTest.java) continue to pass for legacy mode. Package tests prove that package mode loads rules from a temporary `rules/` directory recursively and that disabled rules are ignored. Rule names in package mode are stable relative paths under the scenario root, such as `rules/move.rq`, `rules/core/move.rq`, or `rules/custom/mechanics/redirect.rq`, because transaction traces and rule order logs use rule names for diagnostics.

After rule loading works, wire package mode into `Configurator`. Command parsing stays small: if the first argument is `--scenario`, use the second argument as the scenario root; otherwise preserve the legacy task-name behavior. If no arguments are passed, first check `MASE_SCENARIO_DIR`; if set, use that package, otherwise default to `TASKNAME` or `sim-SmallMaze` for compatibility. Docker can set either `TASKNAME=sim-CcrsMaze` or `MASE_SCENARIO_DIR=scenarios/ccrs`.

Update [MazeResetService.java](src/main/java/org/maze/application/MazeResetService.java) wiring only as needed. The reset service should not know about command-line parsing, but it must receive the resolved package-local dataset pattern and the package-local rule service created at startup. Repeated reset calls should keep reloading the same selected package.

Create package copies for at least SmallMaze and CcrsMaze before migrating every built-in scenario. SmallMaze is the low-risk smoke test. CcrsMaze exercises disconnected CCRS layout, dynamic locks, cleanup rules, infrastructure agents, and A2A keyholder behavior. Built-in package copies now exist for all legacy scenarios. For CcrsMaze, the current direct `Global` rules needed by `sim-CcrsMaze` are duplicated into the CCRS package with CCRS-specific rules in the same package. Package mode does not rely on `src/main/resources/rules/Global`.

Finally, document the contract in [README.md](README.md) and update creator-side WP6 after the server implementation validates the real package shape. The README now shows legacy and package commands, path-resolution rules, Docker usage, and rule activation semantics.

For the next pass, evaluate [MazeLayoutService.java](src/main/java/org/maze/application/MazeLayoutService.java) separately from package loading. The current CCRS switch is data-driven because it reads the RDF type from the loaded TriG data. The investigation should compare the pros and cons of `calculateLayout` and `calculateCcrsLayout`, including disconnected components, one-way links, incoming references, position collisions, ordinary grid mazes, and viewer expectations. If the CCRS layout handles every accepted case, replace the split with one layout path and cover that change with layout tests.

For agent migration, treat the current [examples package](src/main/java/org/maze/examples) as legacy scenario source. Keep reusable HTTP/A2A client code in a shared place only if it is genuinely scenario-neutral, but move CCRS-specific constants, launch scripts, and runnable scenario agent definitions into the CCRS package contract or a dedicated package-owned agent module.

For vocabulary work, audit [MazeVocab.java](src/main/java/org/maze/domain/vocab/MazeVocab.java), RDF datasets, rule files, WebSocket event DTOs, RDF error responses, and scenario package metadata. Use that audit to expand [maze.ttl](docs/maze.ttl), then add a small web resource that serves the vocabulary dereferenceably instead of leaving it as a static documentation-only artifact.

## Concrete Steps

Work from the repository root unless a command says otherwise.

1. Inspect current startup and rule wiring:

        rg -n "ServerConfiguration|extractMazeName|buildRulesetPaths|discoverRuleFiles|loadRule|mase.init.dataset|TASKNAME|runMase" mase-server

   Expect to see `Configurator` handling task parsing, `ServerConfiguration` loading root-level properties, and `MazeRuleLoader` reading from `/rules/`.

2. Audit the protocol flag:

        rg -n "getServerProtocol|SERVER_PROTOCOL|server protocol|protocol" mase-server/src/main/java mase-server/src/test/java mase-server/README.md
        rg -n "mase\\.server\\.protocol" mase-server -g "*.properties"

   If only `ServerConfiguration` and properties files reference `mase.server.protocol`, remove the property end to end as part of the configuration cleanup. If other runtime code uses it, document that behavior and keep it in the package contract.

3. Add scenario package tests before changing startup:

        cd mase-server
        ./gradlew test --tests "*ScenarioPackage*"

   Initially these tests will not exist. After adding them, expect missing-file tests to pass without starting Jetty or loading RDF4J.

4. Add package-aware rule loading tests:

        cd mase-server
        ./gradlew test --tests "*ScenarioRule*"

   The tests should create temporary `.rq` files and assert that `MazeRule` names, descriptions, rule types, and execution ordering are stable. Include a package with `rules/root.rq`, `rules/core/active.rq`, `rules/custom/nested/also-active.rq`, and `rules-disabled/inactive.rq`; assert that package mode loads all three files under `rules/` and does not load `rules-disabled/inactive.rq`.

5. Compile after each wiring milestone:

        cd mase-server
        ./gradlew compileJava

   Expected result is `BUILD SUCCESSFUL`.

6. Run the server in legacy mode:

        cd mase-server
        ./gradlew runMase --args="sim-SmallMaze"

   In another shell:

        curl http://localhost:8080/admin/maze

   Expect HTTP 200 and a JSON admin snapshot for the SmallMaze scenario.

7. Run the server in package mode after creating a packaged SmallMaze:

        cd mase-server
        ./gradlew runMase --args="--scenario scenarios/smallmaze"

   In another shell:

        curl http://localhost:8080/admin/maze

   Expect HTTP 200 and a JSON admin snapshot equivalent to the legacy SmallMaze run.

8. Exercise reset in package mode after the reset service is available:

        curl -X POST http://localhost:8080/admin/maze/reset

   Expect HTTP 200 and a fresh admin snapshot loaded from the package-local dataset and package-local rules.

9. Run the full server test suite:

        cd mase-server
        ./gradlew test

   Expected result is `BUILD SUCCESSFUL`. Package-mode tests should fail before the package loader exists and pass after implementation.

10. Investigate layout algorithm consolidation:

        rg -n "isCcrsMaze|calculateCcrsLayout|calculateLayout|getMazeScenarioName" mase-server/src/main/java/org/maze/application/MazeLayoutService.java mase-server/src/test/java

    Add or update focused layout tests that cover a normal grid maze, a CCRS maze with disconnected components, a maze with incoming-only references, and a case with possible coordinate collision. Accept one general layout algorithm only if those tests and the viewer snapshots still match expected behavior.

11. Plan agent migration out of the legacy examples package:

        rg -n "run.*Agent|org.maze.examples|BASE_URI|TARGET_COORDINATE|guided" mase-server/build.gradle mase-server/src/main/java/org/maze/examples mase-server/scenarios

    Identify which code is reusable agent infrastructure and which code is scenario-specific launch behavior. Move only after the scenario package agent contract is clear enough to run and rerun agents without editing server source.

12. Gate legacy cleanup behind user-confirmed package runs:

        rg -n "sim-\\*|sim-SmallMaze|sim-CcrsMaze|src/main/resources/rules|mase-server/data" mase-server/README.md mase-server/PLAN_MASE_SERVER.md mase-server/src/main/java mase-server/src/test/java

    Do not delete compatibility paths until package-mode runs, reset behavior, viewer snapshots, and agent launch flows have been tested and accepted for the scenarios the user cares about.

13. Expand the vocabulary source:

        rg -n "MazeVocab|MAZE_NS|DYNMAZE_NS|UI_NS|MASE_NS|a2a:|ui:|dyn:|stig:" mase-server/src mase-server/scenarios mase-server/docs

    Update [maze.ttl](docs/maze.ttl) with the terms discovered in code, rules, and data. Include classes and properties for cells, directions, contained agents, movement events, package/scenario metadata, UI elements, transaction/error terms, and A2A keyholder metadata where they are part of the public RDF surface.

14. Serve the vocabulary dereferenceably:

        rg -n "ResourceConfig|LinkedDataDereferenceResource|Produces|text/turtle|RDFValueFormats" mase-server/src/main/java

    Add a route for the vocabulary IRI or a stable local vocabulary endpoint that returns [maze.ttl](docs/maze.ttl) as `text/turtle`, with content negotiation for other RDF serializations if the existing RDF format helpers make that cheap.

## Validation and Acceptance

The server package feature is accepted when a new scenario can be added as a folder under `mase-server/scenarios/` and started without editing Java code, root-level `sim-*.properties`, root-level `data/`, or `src/main/resources/rules`. The server must load the package-local `scenario.properties`, data files, and rule files. It must report missing package files with messages that include the scenario root and the missing relative path.

Legacy startup remains accepted when:

    cd mase-server
    ./gradlew runMase --args="sim-SmallMaze"

still boots the current SmallMaze scenario and the existing tests for legacy rule discovery still pass.

Package startup is accepted when:

    cd mase-server
    ./gradlew runMase --args="--scenario scenarios/smallmaze"

boots a packaged SmallMaze copy and `GET /admin/maze` returns a valid admin snapshot.

Rule isolation is accepted when package mode can run with duplicated package-local rules and no implicit lookup in `src/main/resources/rules/Global`. A package can organize rules into subfolders, but all rule files used by that package must be under the package root.

Rule deactivation is accepted when draft or temporarily disabled rules can be kept in a package without being loaded accidentally. With the proposed contract, package mode loads every `.rq` file under `rules/`, including nested subdirectories, and ignores every `.rq` file under `rules-disabled/`. The server should not support partial activation inside `rules/` in the first package contract; activation is controlled by moving files between `rules/` and `rules-disabled/`.

Protocol cleanup is accepted when the audit either documents a real runtime behavior for `mase.server.protocol` or removes it completely. This implementation removed runtime property and getter references; remaining mentions are historical plan notes and tests that assert the property is absent.

Reset compatibility is accepted when `POST /admin/maze/reset` reloads the selected package-local data and package-local rules without switching back to legacy paths.

Agent packaging is accepted for the first phase when scenario docs and scripts can run agents as separate Java processes against the selected server. Full dynamic Java agent loading from scenario folders is not required and should not be implemented in the server runtime until there is a concrete security and build model.

Scenario contract simplification is accepted when package-local runtime and agent launch defaults are in `scenario.properties`, package examples do not include `.env.example` files just to hold defaults, and existing Gradle agent tasks can still receive the values needed by the legacy Java agent implementations.

Vocabulary work is accepted when [maze.ttl](docs/maze.ttl) includes the public RDF terms currently emitted or consumed by server code, scenario rules, package metadata, viewer events, and RDF error responses, and when the running server can dereference the vocabulary with an RDF response.

Layout consolidation is accepted only if focused tests show that one algorithm preserves current behavior for ordinary mazes and CCRS mazes, including disconnected components and incoming references. If the CCRS algorithm becomes the only layout path, the RDF type should no longer decide layout behavior.

Legacy cleanup is accepted only after package mode has been manually tested, reset and viewer behavior have been checked, scenario agents can be run and rerun through the package contract, and the user confirms that the legacy `sim-*` path is no longer needed as a compatibility fallback.

## Idempotence and Recovery

Scenario package loading should be read-only. Starting the server in package mode must not rewrite `scenario.properties`, `manifest.json`, `.rq` files, or data files. Repeated starts with the same package should produce the same repository state, subject only to runtime mutations after startup.

Migration should be additive. Copy built-in scenario assets into `mase-server/scenarios/` and leave legacy root-level assets until package mode has test coverage and documentation. Do not delete or move legacy files in the same change that introduces the resolver.

If package startup fails, the error should identify whether the failure came from missing `scenario.properties`, missing or invalid `mase.init.dataset`, no rule files matching `mase.rules.path`, invalid SPARQL, or RDF parsing failure. Fix the package file and restart. No rollback of source files should be necessary.

If a package rule behaves differently from the legacy rule set, compare the final ordered rule list in startup logs. Rule names should be stable enough to show whether a duplicated rule is missing, ordered differently, or loaded from the wrong path.

## Artifacts and Notes

Current legacy CCRS runtime configuration:

    sim-CcrsMaze.properties
    mase.init.dataset = data/CcrsMazeV1.trig
    mase.rules.execution.order= normalize_maze_locks*, ccrs*, unlock*, cleanup*, move*

Current legacy startup command:

    cd mase-server
    ./gradlew runMase --args="sim-CcrsMaze"

Current Docker entry point:

    Dockerfile
    CMD ["sh", "-lc", "bin/mase-server ${TASKNAME}"]

Current rule loader assumptions:

    MazeRuleLoader.loadRule(String filename)
    resourcePath = "/rules/" + filename
    getClass().getResourceAsStream(resourcePath)

Current example-agent constraints:

    SampleDfsAgentBob.java uses BASE_URI = "http://127.0.1.1:8080"
    CcrsAgent.java uses BASE_URI = "http://127.0.1.1:8080" and hard-coded guided coordinate lists
    KeyHolderAgent.java uses BASE_URI = "http://127.0.1.1:8080", TARGET_COORDINATE = "42/43", and an A2A port default of 8095

Current scenario-owned launch defaults:

    scenarios/ccrs/scenario.properties
    CCRS_AGENT_DISPATCH_INTERVAL_MS = 5000
    CCRS_AGENT_REQUEST_TIMEOUT_SECONDS = 60
    MASE_KEYHOLDER_PORT = 8095
    MASE_KEYHOLDER_BIND_HOST = 0.0.0.0
    MASE_KEYHOLDER_PUBLIC_BASE_URL = http://127.0.0.1:8095
    KEYHOLDER_BASE_URL = http://127.0.0.1:8095

Feasibility summary:

    Feasible: data paths, reset dataset reload, package metadata, package-local docs, Docker env selection.
    Moderate refactor: configuration source, package-relative path resolution, rule discovery and rule loading.
    Completed cleanup: mase.server.protocol had no runtime behavior and was removed from code, properties, docs, and package examples.
    Larger design choice: scenario-owned Java agents. Keep them out of server dynamic loading initially; run them as independent Java processes with package-local scripts and metadata.

Cross-platform rule discovery note:

    Official Java docs confirm that Path is system-dependent and FileSystem glob matching works on the Path string representation. The active package rule loader avoids the riskiest part of that API by walking the active rules directory and filtering `.rq` files. Keep slash-normalized rule names for diagnostics, and consider slash-normalized sorting if exact cross-platform trace order matters.

External references checked:

    Oracle Java 21 FileSystem.getPathMatcher docs: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)
    Oracle Java 21 Files.walk docs: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#walk(java.nio.file.Path,java.nio.file.FileVisitOption...)
    Oracle Java 21 Path docs: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html
    Apple APFS FAQ: https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/APFS_Guide/FAQ/FAQ.html

Validation evidence from 2026-05-17:

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

    Test-Path mase-server\build\install\mase-server\scenarios\smallmaze\scenario.properties
    True

## Interfaces and Dependencies

At the end of Milestone 1, define a package model similar to:

    package org.maze.infrastructure.scenario;

    public record ScenarioPackage(
            Path root,
            String id,
            Path propertiesFile,
            Optional<Path> manifestFile,
            List<Path> dataFiles,
            List<Path> ruleFiles,
            List<Path> validationFiles,
            Optional<Path> agentsDirectory) {
    }

    public final class ScenarioPackageResolver {
        public ScenarioPackage resolve(Path root) throws IOException;
    }

At the end of Milestone 2, `ServerConfiguration` should support both legacy and package sources. The exact API can vary, but it must expose the task or scenario id, port, init dataset pattern, transaction trace mode, rule execution order, and an optional scenario root used for resolving relative paths.

At the end of Milestone 3, `MazeRuleLoader` should support both legacy resource names and filesystem paths. A practical split is:

    public MazeRule loadRule(String resourceName) throws IOException;
    public MazeRule loadRule(Path ruleFile, Path ruleRoot) throws IOException;
    public List<Path> discoverRuleFiles(Path ruleRoot, String globOrRecursivePattern) throws IOException;

`MazeRuleService` should no longer force rule discovery from a maze name in every constructor. It should have a path where the caller supplies a precomputed ordered list of `MazeRule` instances or explicit rule paths. Legacy factories can keep the current `mazeName` plus additional ruleset behavior.

`Configurator` should support these startup forms:

    ./gradlew runMase
    ./gradlew runMase --args="sim-SmallMaze"
    ./gradlew runMase --args="sim-SmallMaze Stigmergy"
    ./gradlew runMase --args="--scenario scenarios/smallmaze"

In package mode, additional legacy ruleset arguments should either be rejected with a clear message or treated as package-local rule groups only if the package contract explicitly defines them. Do not silently load shared `Global` rules in package mode.

Revision note, 2026-05-17: Initiated this server-scope plan after investigating creator WP6 and current `mase-server` feasibility. The plan records a staged path for self-contained scenario folders while preserving legacy `sim-*` startup and pointing to [PLAN_ADMIN_RESET.md](PLAN_ADMIN_RESET.md) for reset-specific work.

Revision note, 2026-05-17: Added the requested `mase.server.protocol = ldp` audit/removal task and clarified that nested folders under the active rules path are loaded. The package contract now uses `rules-disabled/` as the safe place for deactivated or draft rules that should not match `mase.rules.path = rules/**/*.rq`.

Revision note, 2026-05-17: Tightened the rule contract per user direction: every `.rq` file under `rules/` is loaded recursively, regardless of subdirectory structure, and `rules-disabled/` is the explicit deactivation and work-in-progress area.

Revision note, 2026-05-17: Implemented package mode in `mase-server`, added built-in package copies, removed dead `mase.server.protocol` runtime configuration, documented package commands in [README.md](README.md), and recorded successful compile/test/installDist validation.

Revision note, 2026-05-17: Added cross-platform rule-discovery findings from official Java file-system docs, added the maze layout consolidation investigation, made agent migration from the legacy examples package explicit, and gated scenario-package-only cleanup on user-confirmed package behavior.

Revision note, 2026-05-17: Simplified the scenario contract by removing `.env.example` from packages, moved CCRS agent launch defaults into [scenario.properties](scenarios/ccrs/scenario.properties), added a Gradle bridge from those properties to legacy agent environment variables, and added vocabulary expansion plus dereferenceable serving tasks for [maze.ttl](docs/maze.ttl).

Revision note, 2026-05-17: Added canonical RDF resource IRI resolution for Linked Data dereferencing. Browser-facing requests to hosts such as `localhost` are now mapped to the stable `MASE_SERVER_BASE_URI` graph identity before repository lookup, and Docker CCRS startup sets that canonical base explicitly.
