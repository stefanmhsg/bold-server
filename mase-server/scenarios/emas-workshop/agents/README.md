# EMAS Workshop Agents

This scenario reuses the CCRS agent classes for the larger workshop showcase:

- [CcrsAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/CcrsAgent.java)
- [KeyHolderAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/KeyHolderAgent.java)
- [A2AKeySeekerAgent.java](src/main/java/org/maze/scenarios/ccrs/agents/A2AKeySeekerAgent.java)

The copied sources are kept here as scenario-local reference material. The current Gradle tasks still run the CCRS agent main classes and can read this package's launch defaults through `-PmaseScenario=scenarios/emas-workshop`.

Start the server with this scenario from `mase-server`:

```shell
gradle runMase --args="--scenario scenarios/emas-workshop"
```

Run the CCRS infrastructure agents as independent Java processes after the server has started. Pass `-PmaseScenario=scenarios/emas-workshop` so the JavaExec tasks read this package's launch defaults:

```shell
gradle -PmaseScenario=scenarios/emas-workshop runCcrsAgent
gradle -PmaseScenario=scenarios/emas-workshop runKeyHolderAgent
gradle -PmaseScenario=scenarios/emas-workshop runA2AKeySeekerAgent
```

The built-in tasks are registered in [build.gradle](../../../build.gradle), so `gradle <task>` works when Gradle is installed. If you rely on the wrapper, replace `gradle` with `.\gradlew.bat` on Windows or `./gradlew` on macOS/Linux.

Scenario-owned launch defaults are kept in [scenario.properties](../scenario.properties). The current Gradle JavaExec tasks translate those scenario-local properties into the environment variables read by the agent classes.
