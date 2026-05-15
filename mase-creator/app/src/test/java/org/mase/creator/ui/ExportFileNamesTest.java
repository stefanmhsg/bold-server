package org.mase.creator.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportFileNamesTest {

    @TempDir
    Path tempDir;

    @Test
    void normalizesTrigFileNames() {
        assertEquals("Custom.trig", ExportFileNames.normalizeTrigFileName(" Custom "));
        assertEquals("Already.trig", ExportFileNames.normalizeTrigFileName("Already.trig"));
        assertEquals("UPPER.TRIG", ExportFileNames.normalizeTrigFileName("UPPER.TRIG"));
    }

    @Test
    void rejectsBlankNamesAndPaths() {
        assertThrows(IllegalArgumentException.class, () -> ExportFileNames.normalizeTrigFileName(" "));
        assertThrows(IllegalArgumentException.class, () -> ExportFileNames.normalizeTrigFileName("../Maze.trig"));
        assertThrows(IllegalArgumentException.class, () -> ExportFileNames.normalizeTrigFileName("nested\\Maze.trig"));
    }

    @Test
    void suggestsNextAvailableFileName() throws IOException {
        Files.writeString(tempDir.resolve("MaseCreator.trig"), "existing");
        Files.writeString(tempDir.resolve("MaseCreator-1.trig"), "existing");

        assertEquals(
                "MaseCreator-2.trig",
                ExportFileNames.nextAvailableFileName(tempDir, "MaseCreator.trig")
        );
    }
}
