package org.mase.creator.autosave;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeModel;
import org.mase.creator.model.PathStroke;
import org.mase.creator.trig.MazeTrigParser;
import org.mase.creator.trig.MazeTrigSerializer;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSaveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsRestorableTrigSnapshot() throws Exception {
        AutoSaveService autoSaveService = new AutoSaveService(
                tempDir.resolve("autosave/MaseCreator-autosave.trig"),
                new MazeTrigParser(),
                new MazeTrigSerializer()
        );
        MazeModel model = MazeModel.blank(3, 3);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        PathStroke stroke = model.beginPath(first);
        model.continuePath(stroke, second);

        autoSaveService.save(model);
        MazeModel loaded = autoSaveService.load().orElseThrow();

        assertTrue(Files.exists(autoSaveService.autoSavePath()));
        assertEquals(second, loaded.cell(first).orElseThrow().connection(Direction.EAST).orElseThrow());
        assertEquals(first, loaded.cell(second).orElseThrow().connection(Direction.WEST).orElseThrow());
    }
}
