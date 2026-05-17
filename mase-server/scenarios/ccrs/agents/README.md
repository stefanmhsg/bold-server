# Agents

This package owns the CCRS agent sources:

- [CcrsAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/CcrsAgent.java)
- [KeyHolderAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/KeyHolderAgent.java)
- [A2AKeySeekerAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/A2AKeySeekerAgent.java)

Start the server with this scenario package:

```shell
./gradlew runMase --args="--scenario scenarios/ccrs"
```

Run the CCRS infrastructure agents as independent Java processes after the server has started:

```shell
./gradlew runCcrsAgent
./gradlew runKeyHolderAgent
./gradlew runA2AKeySeekerAgent
```

Scenario-owned launch defaults are kept in [scenario.properties](../scenario.properties). The current Gradle JavaExec tasks translate those package-local properties into the environment variables read by the agent classes.
