# Agents

This scenario owns the CCRS agent sources:

- [CcrsAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/CcrsAgent.java)
- [KeyHolderAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/KeyHolderAgent.java)
- [A2AKeySeekerAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/A2AKeySeekerAgent.java)

Start the server with this scenario from `mase-server`:

```shell
gradle runMase --args="--scenario scenarios/ccrs"
```

Run the CCRS infrastructure agents as independent Java processes after the server has started:

```shell
gradle runCcrsAgent
gradle runKeyHolderAgent
gradle runA2AKeySeekerAgent
```

The built-in tasks are registered in [build.gradle](../../../build.gradle), so `gradle <task>` works when Gradle is installed. If you rely on the wrapper, replace `gradle` with `.\gradlew.bat` on Windows or `./gradlew` on macOS/Linux.

Scenario-owned launch defaults are kept in [scenario.properties](../scenario.properties). The current Gradle JavaExec tasks translate those scenario-local properties into the environment variables read by the agent classes.
