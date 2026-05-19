package org.mase.creator.scenario;

import java.nio.file.Path;
import java.util.List;

public record ScenarioPackageExportResult(
        Path packageRoot,
        List<String> generatedFiles
) {

    public ScenarioPackageExportResult {
        packageRoot = packageRoot.toAbsolutePath().normalize();
        generatedFiles = List.copyOf(generatedFiles);
    }
}
