package org.mase.creator.trig;

import org.junit.jupiter.api.Test;
import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeModel;
import org.mase.creator.model.PathStroke;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeTrigSerializerTest {

    @Test
    void serializesCellsInCoordinateOrderWithFixedDirectionOrder() {
        MazeModel model = MazeModel.blank(3, 3);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        CellCoordinate third = new CellCoordinate(2, 1);
        model.createCell(third);
        PathStroke stroke = model.beginPath(first);
        model.continuePath(stroke, second);

        model.placeStart(first);
        model.placeExit(third);

        String trig = new MazeTrigSerializer().serialize(model);
        List<String> cellLines = trig.lines()
                .filter(line -> line.startsWith("</cells/") && !line.startsWith("</cells/999>"))
                .toList();

        assertEquals("</cells/1/1>", cellLines.get(0).split(" ")[0]);
        assertEquals("</cells/1/2>", cellLines.get(1).split(" ")[0]);
        assertEquals("</cells/2/1>", cellLines.get(2).split(" ")[0]);

        String firstLine = cellLines.get(0);
        assertTrue(firstLine.indexOf(Direction.NORTH.predicate()) < firstLine.indexOf(Direction.WEST.predicate()));
        assertTrue(firstLine.indexOf(Direction.WEST.predicate()) < firstLine.indexOf(Direction.SOUTH.predicate()));
        assertTrue(firstLine.indexOf(Direction.SOUTH.predicate()) < firstLine.indexOf(Direction.EAST.predicate()));

        assertTrue(trig.contains("xhv:start </cells/1/1> ."));
        assertTrue(trig.contains("</cells/2/1> { </cells/2/1> a maze:Cell"));
        assertTrue(trig.contains("; maze:exit </cells/999> . }"));
        assertTrue(trig.contains("</cells/999> { </cells/999> a maze:Cell"));
    }

    @Test
    void eachCoordinateCellIsRenderedOnOneLine() {
        MazeModel model = MazeModel.blank(2, 2);
        model.createCell(new CellCoordinate(1, 1));

        String trig = new MazeTrigSerializer().serialize(model);
        String cellLine = trig.lines()
                .filter(line -> line.startsWith("</cells/1/1> {"))
                .findFirst()
                .orElseThrow();

        assertTrue(cellLine.endsWith(". }"));
        assertTrue(cellLine.contains("maze:north maze:Wall;") || cellLine.contains("maze:north maze:Wall ;"));
    }
}
