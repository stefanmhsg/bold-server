package org.mase.creator.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeModelTest {

    @Test
    void drawPathCreatesCellsAndConnectsConsecutiveCells() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);

        PathStroke stroke = model.beginPath(first);
        model.continuePath(stroke, second);

        assertTrue(model.hasCell(first));
        assertTrue(model.hasCell(second));
        assertEquals(second, model.cell(first).orElseThrow().connection(Direction.EAST).orElseThrow());
        assertEquals(first, model.cell(second).orElseThrow().connection(Direction.WEST).orElseThrow());
    }

    @Test
    void newPathStartingInEmptyCellDoesNotAutoConnectToAdjacentExistingCell() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate existing = new CellCoordinate(1, 1);
        CellCoordinate adjacent = new CellCoordinate(1, 2);

        model.beginPath(existing);
        model.beginPath(adjacent);

        assertTrue(model.hasCell(existing));
        assertTrue(model.hasCell(adjacent));
        assertFalse(model.cell(existing).orElseThrow().connection(Direction.EAST).isPresent());
        assertFalse(model.cell(adjacent).orElseThrow().connection(Direction.WEST).isPresent());
    }

    @Test
    void pathStartingInsideExistingCellConnectsNextCell() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate existing = new CellCoordinate(1, 1);
        CellCoordinate next = new CellCoordinate(1, 2);

        model.beginPath(existing);
        PathStroke stroke = model.beginPath(existing);
        model.continuePath(stroke, next);

        assertEquals(next, model.cell(existing).orElseThrow().connection(Direction.EAST).orElseThrow());
        assertEquals(existing, model.cell(next).orElseThrow().connection(Direction.WEST).orElseThrow());
    }

    @Test
    void drawingThroughExistingAdjacentCellsOpensWallBetweenThem() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        model.createCell(first);
        model.createCell(second);

        PathStroke stroke = model.beginPath(first);
        model.continuePath(stroke, second);

        assertEquals(second, model.cell(first).orElseThrow().connection(Direction.EAST).orElseThrow());
        assertEquals(first, model.cell(second).orElseThrow().connection(Direction.WEST).orElseThrow());
    }

    @Test
    void drawWallRemovesConnectionFromBothCells() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        PathStroke stroke = model.beginPath(first);
        model.continuePath(stroke, second);

        model.drawWall(first, Direction.EAST);

        assertFalse(model.cell(first).orElseThrow().connection(Direction.EAST).isPresent());
        assertFalse(model.cell(second).orElseThrow().connection(Direction.WEST).isPresent());
    }

    @Test
    void deleteCellRemovesNeighborReferences() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        PathStroke stroke = model.beginPath(first);
        model.continuePath(stroke, second);

        model.deleteCell(second);

        assertTrue(model.hasCell(first));
        assertFalse(model.hasCell(second));
        assertFalse(model.cell(first).orElseThrow().connection(Direction.EAST).isPresent());
    }

    @Test
    void optimalRouteOnlyUsesExistingCellsAndDoesNotOpenWalls() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        CellCoordinate missing = new CellCoordinate(1, 3);
        model.createCell(first);
        model.createCell(second);

        PathStroke stroke = model.beginOptimalRoute(first).orElseThrow();
        model.continueOptimalRoute(stroke, second);
        model.continueOptimalRoute(stroke, missing);

        assertEquals(List.of(first, second), model.optimalRoute());
        assertFalse(model.hasCell(missing));
        assertFalse(model.cell(first).orElseThrow().connection(Direction.EAST).isPresent());
        assertFalse(model.cell(second).orElseThrow().connection(Direction.WEST).isPresent());
    }

    @Test
    void deletingCellRemovesItFromOptimalRoute() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        model.createCell(first);
        model.createCell(second);

        PathStroke stroke = model.beginOptimalRoute(first).orElseThrow();
        model.continueOptimalRoute(stroke, second);
        model.deleteCell(second);

        assertEquals(List.of(first), model.optimalRoute());
    }

    @Test
    void removingOptimalAtRemovesSelectedRouteCell() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        CellCoordinate third = new CellCoordinate(1, 3);
        CellCoordinate missing = new CellCoordinate(1, 4);
        model.createCell(first);
        model.createCell(second);
        model.createCell(third);

        PathStroke stroke = model.beginOptimalRoute(first).orElseThrow();
        model.continueOptimalRoute(stroke, second);
        model.continueOptimalRoute(stroke, third);

        assertTrue(model.removeOptimalAt(second));
        assertEquals(List.of(first, third), model.optimalRoute());
        assertFalse(model.removeOptimalAt(missing));
    }

    @Test
    void greenRouteOnlyUsesExistingCellsAndDoesNotOpenWalls() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        CellCoordinate missing = new CellCoordinate(1, 3);
        model.createCell(first);
        model.createCell(second);

        PathStroke stroke = model.beginGreenRoute(first).orElseThrow();
        model.continueGreenRoute(stroke, second);
        model.continueGreenRoute(stroke, missing);

        assertEquals(List.of(first, second), model.greenRoute());
        assertFalse(model.hasCell(missing));
        assertFalse(model.cell(first).orElseThrow().connection(Direction.EAST).isPresent());
        assertFalse(model.cell(second).orElseThrow().connection(Direction.WEST).isPresent());
    }

    @Test
    void deletingCellRemovesItFromGreenRoute() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        model.createCell(first);
        model.createCell(second);

        PathStroke stroke = model.beginGreenRoute(first).orElseThrow();
        model.continueGreenRoute(stroke, second);
        model.deleteCell(second);

        assertEquals(List.of(first), model.greenRoute());
    }

    @Test
    void greenRouteDrawingAppendsIndependentRoutes() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        CellCoordinate third = new CellCoordinate(2, 1);
        CellCoordinate fourth = new CellCoordinate(2, 2);
        model.createCell(first);
        model.createCell(second);
        model.createCell(third);
        model.createCell(fourth);

        PathStroke firstStroke = model.beginGreenRoute(first).orElseThrow();
        model.continueGreenRoute(firstStroke, second);
        PathStroke secondStroke = model.beginGreenRoute(third).orElseThrow();
        model.continueGreenRoute(secondStroke, fourth);

        assertEquals(List.of(
                List.of(first, second),
                List.of(third, fourth)
        ), model.greenRoutes());
        assertEquals(second, model.greenSuccessors().get(first));
        assertEquals(fourth, model.greenSuccessors().get(third));
        assertFalse(model.cell(first).orElseThrow().connection(Direction.EAST).isPresent());
        assertFalse(model.cell(third).orElseThrow().connection(Direction.EAST).isPresent());
    }

    @Test
    void removingGreenAtSourceRemovesSelectedSuccessorAndPreservesOtherSegments() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        CellCoordinate third = new CellCoordinate(1, 3);
        CellCoordinate fourth = new CellCoordinate(1, 4);
        model.createCell(first);
        model.createCell(second);
        model.createCell(third);
        model.createCell(fourth);
        PathStroke stroke = model.beginGreenRoute(first).orElseThrow();
        model.continueGreenRoute(stroke, second);
        model.continueGreenRoute(stroke, third);
        model.continueGreenRoute(stroke, fourth);

        assertTrue(model.removeGreenAt(second));

        assertEquals(List.of(
                List.of(first, second),
                List.of(third, fourth)
        ), model.greenRoutes());
        assertEquals(second, model.greenSuccessors().get(first));
        assertFalse(model.greenSuccessors().containsKey(second));
        assertEquals(fourth, model.greenSuccessors().get(third));
    }

    @Test
    void removingGreenAtTailRemovesIncomingSuccessor() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        model.createCell(first);
        model.createCell(second);
        PathStroke stroke = model.beginGreenRoute(first).orElseThrow();
        model.continueGreenRoute(stroke, second);

        assertTrue(model.removeGreenAt(second));

        assertTrue(model.greenRoutes().isEmpty());
        assertTrue(model.greenSuccessors().isEmpty());
    }

    @Test
    void removingGreenAtCellWithoutSuccessorDoesNotConsumeNextRouteStart() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);
        CellCoordinate third = new CellCoordinate(2, 1);
        CellCoordinate fourth = new CellCoordinate(2, 2);
        model.createCell(first);
        model.createCell(second);
        model.createCell(third);
        model.createCell(fourth);

        model.beginGreenRoute(first);

        assertFalse(model.removeGreenAt(third));
        PathStroke stroke = model.beginGreenRoute(third).orElseThrow();
        model.continueGreenRoute(stroke, fourth);

        assertEquals(List.of(List.of(third, fourth)), model.greenRoutes());
    }

    @Test
    void clearStartAndExitOnlyRemoveMatchingMarkers() {
        MazeModel model = MazeModel.blank(4, 4);
        CellCoordinate start = new CellCoordinate(1, 1);
        CellCoordinate exit = new CellCoordinate(1, 2);
        CellCoordinate other = new CellCoordinate(2, 1);
        model.createCell(start);
        model.createCell(exit);
        model.createCell(other);
        model.placeStart(start);
        model.placeExit(exit);

        assertFalse(model.clearStartAt(other));
        assertFalse(model.clearExitAt(other));
        assertEquals(start, model.startCell().orElseThrow());
        assertEquals(exit, model.exitSourceCell().orElseThrow());

        assertTrue(model.clearStartAt(start));
        assertTrue(model.clearExitAt(exit));
        assertTrue(model.startCell().isEmpty());
        assertTrue(model.exitSourceCell().isEmpty());
    }
}
