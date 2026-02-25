# Example Agent: `bob` (DFS)

This project ships with a runnable sample agent: `org.maze.examples.BobLeftWallFollowerAgent`.

## Run

1. Start the server (example):

```shell
gradle runMase --args="sim-SmallMaze"
```

2. In another terminal, run the sample agent:

```shell
gradle runBobAgent
```
## Docker

1. Run Docker Compose 

2. Run agent with Docker (server must already be running):
```powershell
docker compose exec mase-server sh -lc "java -cp '/opt/mase/install/mase-server/lib/*' org.maze.examples.SampleDfsAgentBob"
```

## How it operates

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

## Required behavior assumptions

- Agent must always send the same identity in the `Authorization` header (`bob`).
- Agent can only perceive/interact with its current cell; movement must use `dyn:entersFrom` from current location.
- Moves are valid only if the connection exists in RDF (adjacent and not blocked by missing edge/lock).

## Console trace

The agent logs explicit DFS actions (`VISIT`, `MOVE`, `SKIP`, `BLOCKED`, `BACKTRACK`, `LOCKED`, `UNLOCKED`, `EXIT`) with step/depth to make behavior easy to follow.
