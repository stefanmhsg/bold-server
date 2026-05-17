# SmallMaze Scenario

Run this scenario from `mase-server` with:

    gradle runMase --args="--scenario scenarios/smallmaze"

The built-in tasks are registered in [build.gradle](../../build.gradle), so `gradle <task>` works when Gradle is installed. If you rely on the wrapper, replace `gradle` with `.\gradlew.bat` on Windows or `./gradlew` on macOS/Linux.

All active SPARQL rules are under [rules](rules). Files under [rules-disabled](rules-disabled) are not loaded and can be used for deactivated or work-in-progress rules.
