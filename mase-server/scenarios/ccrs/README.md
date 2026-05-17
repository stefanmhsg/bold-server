# CCRS Scenario Package

Run this package from `mase-server` with:

    ./gradlew runMase --args="--scenario scenarios/ccrs"

This package contains the CCRS maze dataset and the package-local copies of the Global and CCRS SPARQL rules needed by the scenario. Agent launch details are kept in [agents/README.md](agents/README.md).

All active SPARQL rules are under [rules](rules). Files under [rules-disabled](rules-disabled) are not loaded and can be used for deactivated or work-in-progress rules.
