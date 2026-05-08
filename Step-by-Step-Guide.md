# Detailed Guide for using MASE

(Work in progress)

## Motivation

- Get started quickly with prototyping your agents
    - Immediate traceability and visualization.
    - Start with toy example (sim-SmallMaze).

- Web analogy in the form of RDF Knowledge Graphs (KG) 
    - A "cell" in the maze corresponds to a named RDF graph. The maze thus representing a graph of resources.
    - "Connections" between cells as RDF links, analogous to hyperlinks.
    - Interaction via HTTP GET and POST.
    - For more, see [Charpenay et al. (2022)](https://doi.org/10.1007/978-3-030-97457-2_3)

- Maze simulation provides intuitive startingpoint to build custom scenarios on top
    - Requires a form of embodiment of agents in the maze. 
    - Limited observability requires agents to discover the maze cell by cell.
    - The MASE-server enforces embodiment with an "access control".
        - Perceive (GET) only for agent's current cell.
        - Interaction (POST) only for agent's current cell.
        - Navigation (POST) only to adjacent cells.
    - The MASE environment is made interactive by executing SPARQL Queries to materialize condition-action rules.

- Scale the MASE-scenario to more complex KGs (e.g. sim-MidMaze) and introduce additional elements via SPARQL Queries.

---

## Toy Example


Start MASE Server
```shell
cd .\mase-server\
gradle runMase --args="sim-SmallMaze"
```

Start MASE Viewer
```shell
cd .\mase-viewer\
npm run dev
```
View at: http://localhost:5173/

- Inspect a cell
    - Cardinal directions: Cells or Walls
    - Keys and Locks


### Run example agent

- [Example Agent README](/mase-server/src/main/java/org/maze/examples/README.md)

```shell
gradle runBobAgent
```

- See traces in Viewer (default to summary)

#### How it operates

- Uses `Authorization: bob` on **every** `GET` and `POST`.
- Bootstraps with `GET /maze`, reads `xhv:start`, then enters by posting:
  - `<.../agents/bob> dyn:entersFrom <.../maze>` to the discovered start cell.
- Runs a depth-first search (DFS):
  - Direction preference for new neighbors: **west, north, east, south**.
  - Only advances to unvisited reachable neighbors.
  - Backtracks to parent when no unvisited neighbor exists.
- If a cell is locked:
  - Reads lock requirements from the cell graph,
  - Posts `dyn:keyValue` with a previously discovered key,
  - Re-reads and continues.
- If `maze:exit` is present, moves to that target and finishes on `/cells/999`.

#### Required behavior assumptions

- Agent must always send the same identity in the `Authorization` header (`bob`).
- Agent can only perceive/interact with its current cell; movement must use `dyn:entersFrom` from current location.
- Moves are valid only if the connection exists in RDF (adjacent and not blocked by missing edge/lock).

---

## Custom Example

[Custom Example README](/CCRS-SCENARIO.md)

- UI elements
- Signifiers
