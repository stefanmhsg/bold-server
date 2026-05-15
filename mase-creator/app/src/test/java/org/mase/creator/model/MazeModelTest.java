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
}
