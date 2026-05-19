package org.mase.creator.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioPackageNamesTest {

    @TempDir
    Path tempDir;

    @Test
    void normalizesPackageNamesAndDerivesScenarioIds() {
        assertEquals("MaseCreator", ScenarioPackageNames.normalizePackageDirectoryName(" MaseCreator "));
        assertEquals("my-custom-maze", ScenarioPackageNames.scenarioIdFromPackageName("My Custom_Maze"));
        assertEquals("My Maze.trig", ScenarioPackageNames.trigFileNameFromPackageName("My Maze"));
    }

    @Test
    void rejectsBlankNamesPathsAndUnsafeCharacters() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioPackageNames.normalizePackageDirectoryName(" "));
        assertThrows(IllegalArgumentException.class, () -> ScenarioPackageNames.normalizePackageDirectoryName("../Maze"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioPackageNames.normalizePackageDirectoryName("nested\\Maze"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioPackageNames.normalizePackageDirectoryName("Maze#1"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioPackageNames.normalizePackageDirectoryName("Maze."));
    }

    @Test
    void suggestsNextAvailablePackageName() throws IOException {
        Files.createDirectories(tempDir.resolve("MaseCreator"));
        Files.createDirectories(tempDir.resolve("MaseCreator-1"));

        assertEquals(
                "MaseCreator-2",
                ScenarioPackageNames.nextAvailablePackageName(tempDir, "MaseCreator")
        );
    }
}
