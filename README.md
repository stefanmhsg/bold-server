# MASE

Run MASE Server and MASE Viewer together with Docker Compose.

## Prerequisites
- Docker Desktop (or Docker Engine with Compose)

## Start
```powershell
docker compose up --build
```

- Server (entry point): http://127.0.1.1:8080/maze
- Viewer: http://127.0.1.1:3000/ or http://localhost:3000

## Choose maze scenario
Default scenario is `sim-UnsafeMaze`.

```powershell
$env:TASKNAME="sim-MidMaze"
docker compose up --build
```

## Maze + ruleset example (Stigmergy)
Pass both values in `TASKNAME` (equivalent to Gradle `--args="sim-UnsafeMaze Stigmergy"`).

```powershell
$env:TASKNAME="sim-UnsafeMaze Stigmergy"
docker compose up --build
```

## Example Agent

Run sample dfs agent (name: `bob`) against a running server:
```shell script
gradle runBobAgent
```

Run the same agent with Docker (server must already be running):
```powershell
docker compose exec mase-server sh -lc "java -cp '/opt/mase/install/mase-server/lib/*' org.maze.examples.SampleDfsAgentBob"
```

The sample agent always sends `Authorization: bob`, starts with `GET /maze`, enters the discovered start cell with `dyn:entersFrom`, then navigates in depth-first-search ordered by `west, north, east, south`. If the current cell is locked and `dyn:needsAction` requires a key type that was previously observed via `GET`, it posts `dyn:keyValue` to unlock and re-checks the cell. It finishes when it reaches `/cells/999` via `maze:exit`.

Detailed example-agent documentation: [mase-server/src/main/java/org/maze/examples/README.md](mase-server/src/main/java/org/maze/examples/README.md)

## Stop
```powershell
docker compose down
```
