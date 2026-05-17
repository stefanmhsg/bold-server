# SmallMaze Agents

This scenario owns the DFS sample agent source for SmallMaze:
[SampleDfsAgentBob.java](src/main/java/org/maze/scenarios/smallmaze/agents/SampleDfsAgentBob.java).

Start the server with this scenario from `mase-server`:

```shell
gradle runMase --args="--scenario scenarios/smallmaze"
```

In another terminal, run the registered built-in agent task:

```shell
gradle runSmallMazeBobAgent
```

The built-in tasks are registered in [build.gradle](../../../build.gradle), so `gradle <task>` works when Gradle is installed. If you rely on the wrapper, replace `gradle` with `.\gradlew.bat` on Windows or `./gradlew` on macOS/Linux.

The agent sends `Authorization: bob`, bootstraps from `GET /maze`, enters the discovered start cell with `dyn:entersFrom`, and then runs depth-first search with direction preference `west, north, east, south`. If it observes a matching key for a locked cell, it posts `dyn:keyValue`, re-reads the cell, and continues.
