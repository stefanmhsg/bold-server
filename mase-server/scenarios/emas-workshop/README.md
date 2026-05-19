# EMAS Workshop Scenario

This is the workshop-safe copy of the CCRS scenario. It keeps the CCRS data and SPARQL rule set, but [scenario.properties](scenario.properties) defaults to [CcrsMazeV2.trig](data/CcrsMazeV2.trig) so the red key is present in the maze and the live showcase does not depend on A2A keyholder timing.

Run this scenario from `mase-server` with:

    gradle runMase --args="--scenario scenarios/emas-workshop"

The built-in tasks are registered in [build.gradle](../../build.gradle), so `gradle <task>` works when Gradle is installed. If you rely on the wrapper, replace `gradle` with `.\gradlew.bat` on Windows or `./gradlew` on macOS/Linux.

This scenario contains the CCRS maze datasets and the scenario-local copies of the global and CCRS SPARQL rules needed by the workshop. Agent launch details are kept in [agents/README.md](agents/README.md).

All active SPARQL rules are under [rules](rules). Files under [rules-disabled](rules-disabled) are not loaded and can be used for deactivated or work-in-progress rules.
