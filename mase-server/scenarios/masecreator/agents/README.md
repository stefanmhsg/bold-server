# MaseCreator Agents

This package owns the DFS sample agent source for MaseCreator:
[SampleDfsAgentBob.java](src/main/java/org/maze/scenarios/masecreator/agents/SampleDfsAgentBob.java).

Start the server with this scenario package:

```shell
./gradlew runMase --args="--scenario scenarios/masecreator"
```

In another terminal, run the agent:

```shell
./gradlew runMaseCreatorBobAgent
```

The agent sends `Authorization: bob`, bootstraps from `GET /maze`, enters the discovered start cell with `dyn:entersFrom`, and then runs depth-first search with direction preference `west, north, east, south`. If it observes a matching key for a locked cell, it posts `dyn:keyValue`, re-reads the cell, and continues.
