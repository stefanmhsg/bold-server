package org.maze.infrastructure.scenario;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Resolved scenario-package file set.
 */
public record ScenarioPackage(
        Path root,
        String id,
        Path propertiesFile,
        Optional<Path> manifestFile,
        String resolvedDatasetPattern,
        List<Path> dataFiles,
        List<Path> ruleFiles,
        List<Path> validationFiles,
        Optional<Path> agentsDirectory) {

    public ScenarioPackage {
        root = root.toAbsolutePath().normalize();
        propertiesFile = propertiesFile.toAbsolutePath().normalize();
        manifestFile = manifestFile.map(path -> path.toAbsolutePath().normalize());
        dataFiles = List.copyOf(dataFiles);
        ruleFiles = List.copyOf(ruleFiles);
        validationFiles = List.copyOf(validationFiles);
        agentsDirectory = agentsDirectory.map(path -> path.toAbsolutePath().normalize());
    }
}
