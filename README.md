# Linked Data MASE - A Maze-Based Multi-Agent Systems Environment for Testing and Visualizing Hypermedia Agents

Run [MASE server](mase-server/README.md) and [MASE viewer](mase-viewer/README.md) together to simulate interactive maze scenarios that agents access through HTTP GET and POST.

The current run model is scenario-based. Built-in scenarios live in [mase-server/scenarios](mase-server/scenarios); each scenario directory bundles its runtime properties, RDF data, SPARQL rules, disabled-rule holding area, validation assets, documentation, and agent notes. Docker and Gradle runs select a scenario with `--scenario <folder>` or `MASE_SCENARIO_DIR`.

## Prerequisites
- Docker Desktop (or Docker Engine with Compose)
- Java 21 for local Gradle runs
- Node.js 20 for running the viewer outside Docker

## Available Scenarios

- [smallmaze](mase-server/scenarios/smallmaze): compact maze for quick local runs and the sample DFS agent.
- [midmaze](mase-server/scenarios/midmaze): larger maze with the same scenario contract.
- [bigmaze](mase-server/scenarios/bigmaze): large maze dataset.
- [masecreator](mase-server/scenarios/masecreator): scenario generated from the creator workflow.
- [ccrs](mase-server/scenarios/ccrs): CCRS demonstration scenario with scenario-local agent notes.

## Quick Start with Docker Compose

From the repository root:

```powershell
docker compose -f docker-compose.starter.yml up --build
```

This starts the [smallmaze](mase-server/scenarios/smallmaze) scenario and the viewer. The Compose file passes `--scenario scenarios/smallmaze` to the server container.

- Server entry point: http://localhost:8080/maze
- Viewer: http://localhost:3000

## Run a Scenario with Docker

Build the server image and pass the scenario path as the container command:

```powershell
cd .\mase-server
docker build -t mase-server .
docker run --rm -p 8080:8080 mase-server bin/mase-server --scenario scenarios/midmaze
```

Replace `scenarios/midmaze` with any scenario directory that exists inside the image, such as `scenarios/smallmaze`, `scenarios/bigmaze`, `scenarios/masecreator`, or `scenarios/ccrs`.

## Run a Scenario with Gradle

Start the server from the scenario path:

```powershell
cd .\mase-server
gradle runMase --args="--scenario scenarios/smallmaze"
```

The built-in server and agent tasks are registered in [mase-server/build.gradle](mase-server/build.gradle), so `gradle <task>` works from [mase-server](mase-server) when Gradle is installed. If you rely on the wrapper, replace `gradle` with `.\gradlew.bat` on Windows or `./gradlew` on macOS/Linux.

Run the viewer separately when you are not using Docker Compose:

```powershell
# new terminal
cd .\mase-viewer
npm install
npm run dev
```

The server runs at `http://localhost:8080`. The viewer development server defaults to `http://localhost:5173/`; the Docker viewer runs at `http://localhost:3000/`. In both modes the viewer targets `http://localhost:8080` for API calls and `ws://localhost:8080/ws` for live updates unless configured otherwise.

## Scenario Runtime Contract

A scenario is selected by its directory. The server loads [scenario.properties](mase-server/scenarios/smallmaze/scenario.properties) from the selected scenario and resolves scenario-local paths relative to that directory.

- `scenario.properties` defines the dataset, transaction trace mode, rule execution order, and scenario-owned launch defaults.
- `mase.init.dataset` points to the scenario-local RDF data file.
- Every `.rq` file under `rules/` is active and loaded recursively.
- Files under `rules-disabled/` are ignored and can hold deactivated or work-in-progress rules.
- Transaction trace broadcasting is configured with `mase.transaction.trace`. Shipped scenarios use `summary`, which emits lightweight transaction headers without per-triple logging.

### Transaction Trace Modes

`mase.transaction.trace` supports three modes:

- `off`: no `TRANSACTION` debug events are broadcast.
- `summary`: broadcast committed/rolled-back transaction headers with transaction id, time, trigger, agent, graph, status, error, and number of executed rules. This is the default middle ground for multi-agent runs.
- `full`: additionally include request bodies, merge triples, and per-rule RDF diffs. Use this for debugging only because it snapshots repository state around rule execution.

The maze dataset is an RDF file that defines the maze structure and the initial state of the environment. Rules are SPARQL Update queries that run after initialization and during each HTTP POST handled by the MASE server. They customize the environment and evolve it in response to agent operations. For example, stigmergy rules can add traffic markers to maze cells.

## Navigating MASE

MASE navigation is controlled by HTTP semantics plus RDF validation logic.

- **Authorization header activates access control:** send `Authorization: <agentName>` (or `Authorization: Agent <agentName>`) so requests are validated against the agent's current maze location.
- **No header = unrestricted access (debug mode):** if no `Authorization` header is provided, all resources remain accessible to support runtime debugging and inspection.
- **First entry rule:** an agent without a known location must enter by POSTing to the maze entrance cell (the `xhv:start` target) with exactly one `dynmaze:entersFrom` triple that points to `/maze`.
- **Adjacency constraints for movement:** movement POSTs are detected by `dynmaze:entersFrom`; MASE enforces exactly one source cell, requires that source to match the agent's actual current cell, and allows movement only when an RDF edge (`maze:north|south|east|west|exit`) exists from source to target.
- **Local perception and action:** authenticated agents can only `GET` and non-movement `POST` on their current cell.
- **Agent named graph creation:** on first valid maze entry, MASE creates a named graph for the agent IRI and inserts an `a maze:Agent` triple.
- **Transactional request pipeline:** MASE parses RDF payloads, then validates access, merges triples, executes rules, and checks core movement postconditions inside one repository transaction.
- **Scoped concurrency:** POST handling uses per-resource request queues for the same target graph, movement source graph, or agent identity. Independent graphs can proceed concurrently where their core request scopes do not overlap.
- **Scenario-specific behavior stays in rules:** Java enforces embodiment, adjacent movement requests, and local-only interaction. Unlocking, switches, UI materialization, and other scenario effects are encoded in SPARQL rules.

### Example Request Flow

1. Agent `bob` sends `GET: http://127.0.1.1:8080/maze` with header `Authorization: bob` to discover the maze entrance cell (e.g., `/cells/0/0`).

2. Bob sends `POST: http://127.0.1.1:8080/cells/0/0` with header `Authorization: bob` and body `<http://127.0.1.1:8080/agents/bob> <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#entersFrom> <http://127.0.1.1:8080/maze> .` to enter the maze. The scenario-local movement rules materialize the movement by adding a containment triple for the agent in the target cell's graph and removing any previous containment triple. See the selected scenario's `rules/` directory for movement rules.

3. Bob is now allowed to `GET` and `POST` on `/cells/0/0`. Remember that Bob can only perceive the current cell and can only request a move to an adjacent cell. If Bob tries to move to a non-adjacent cell or tries to `GET` or `POST` on a different cell, the request will be rejected by the server.

4. If the current cell is locked and Bob has previously observed a key of the required type, it can `POST` to that current cell a triple like `<http://127.0.1.1:8080/cells/0/1> <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#keyValue> "redkey" .` to unlock it. Non-movement POSTs are local interactions and are rejected if sent to a cell other than the agent's current cell. The ruleset materializing the unlocking is defined in the selected scenario's rules directory, for example [unlock-redkey.rq](mase-server/scenarios/smallmaze/rules/scenario/unlock-redkey.rq).

5. Consider the maze solved when Bob moved to the exit cell (`/cells/999`) via the `maze:exit` edge.

## Scenario Agents

Scenarios can include agent source and launch notes under their `agents/` directory. Start the server first, then follow the scenario-specific agent documentation:

- [SmallMaze agents README.md](mase-server/scenarios/smallmaze/agents/README.md)
- [CCRS agents README.md](mase-server/scenarios/ccrs/agents/README.md)

Run the built-in SmallMaze sample agent against a running SmallMaze server:

```powershell
cd .\mase-server
gradle runSmallMazeBobAgent
```

The sample SmallMaze DFS agent sends `Authorization: bob`, starts with `GET /maze`, enters the discovered start cell with `dyn:entersFrom`, then navigates in depth-first-search order. If the current cell is locked and `dyn:needsAction` requires a key type that was previously observed via `GET`, it posts `dyn:keyValue` to unlock and re-checks the cell.

## Overview of the MASE scenarios

### SmallMaze

<img src="mase-server/docs/maze-SmallMaze_keys.png" width="">

### MidMaze

<img src="mase-server/docs/maze_MidMaze_keys_solution.png" width="">

### BigMaze

<img src="mase-server/docs/maze_BigMaze_keys_solution.png" width="">

---
# Further References and Related Projects

- https://github.com/wintechis/agents-llm-affordances

```bibtex
@inproceedings{schmid_2025,
    address = {Cham},
    title = {Adaptive {Planning} on the {Web}: {Using} {LLMs} and {Affordances} for {Web} {Agents}},
    isbn = {978-3-031-81221-7},
    shorttitle = {Adaptive {Planning} on the {Web}},
    doi = {10.1007/978-3-031-81221-7_7},
    abstract = {We investigate the adaption of agents using plans on the Web despite its large and dynamic nature, as well as agents’ constrained perception. Based on Semantic Web technologies and affordances, we compare how agents choose appropriate actions to adapt to their environment by condition-action rules or suggested actions of large language models. We conduct experiments on execution cost and plan stability distance to see whether agents choose appropriate actions to adapt their plans. We find that cost and stability of rule-based and LLMs for adaptation with affordances are close together, while performance differs greatly.},
    language = {en},
    booktitle = {Knowledge {Graphs} and {Semantic} {Web}},
    publisher = {Springer Nature Switzerland},
    author = {Schmid, Sebastian and Freund, Michael and Harth, Andreas},
    editor = {Tiwari, Sanju and Villazón-Terrazas, Boris and Ortiz-Rodríguez, Fernando and Sahri, Soror},
    year = {2025},
    keywords = {Adaption, Dynamic Environments, Web Agents},
    pages = {93--108},
}
```

In particular the scenario datasets ([SmallMaze.trig](mase-server/scenarios/smallmaze/data/SmallMaze.trig), [MidMaze.trig](mase-server/scenarios/midmaze/data/MidMaze.trig), [BigMaze.trig](mase-server/scenarios/bigmaze/data/BigMaze.trig)) and key-lock mechanism (e.g., [unlock-redkey.rq](mase-server/scenarios/smallmaze/rules/scenario/unlock-redkey.rq)).

---
- https://github.com/bold-benchmark/bold-server

```bibtex
@misc{kafer_2023,
    title = {{BOLD}: {A} {Benchmark} for {Linked} {Data} {User} {Agents} and a {Simulation} {Framework} for {Dynamic} {Linked} {Data} {Environments}},
    shorttitle = {{BOLD}},
    url = {http://arxiv.org/abs/2307.09114},
    doi = {10.48550/arXiv.2307.09114},
    abstract = {The paper presents the BOLD (Buildings on Linked Data) benchmark for Linked Data agents, next to the framework to simulate dynamic Linked Data environments, using which we built BOLD. The BOLD benchmark instantiates the BOLD framework by providing a read-write Linked Data interface to a smart building with simulated time, occupancy movement and sensors and actuators around lighting. On the Linked Data representation of this environment, agents carry out several specified tasks, such as controlling illumination. The simulation environment provides means to check for the correct execution of the tasks and to measure the performance of agents. We conduct measurements on Linked Data agents based on condition-action rules.},
    urldate = {2025-09-24},
    publisher = {arXiv},
    author = {Käfer, Tobias and Charpenay, Victor and Harth, Andreas},
    month = jul,
    year = {2023},
    note = {arXiv:2307.09114 [eess]},
    keywords = {Computer Science - Artificial Intelligence, Computer Science - Systems and Control, Electrical Engineering and Systems Science - Systems and Control},
}
```

In particular as a reference for [Configurator.java](mase-server/src/main/java/org/maze/Configurator.java), scenario-local [scenario.properties](mase-server/scenarios/smallmaze/scenario.properties), and [LinkedDataDereferenceResource.java](mase-server/src/main/java/org/maze/api/ld/LinkedDataDereferenceResource.java).

---
- https://github.com/amee-project/maze-server
