# Agents

Run the CCRS infrastructure agents as independent Java processes after the server has started:

    ./gradlew runCcrsAgent
    ./gradlew runKeyHolderAgent
    ./gradlew runA2AKeySeekerAgent

The Java classes still live in [../../src/main/java/org/maze/examples](../../src/main/java/org/maze/examples). This package records the launch contract; dynamic Java loading from scenario packages is intentionally not part of the first package implementation.

Scenario-owned launch defaults are kept in [scenario.properties](../scenario.properties). The current Gradle JavaExec tasks translate those package-local properties into the environment variables read by the legacy example-agent classes.
