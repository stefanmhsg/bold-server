package org.mase.creator.ui;

import org.junit.jupiter.api.Test;
import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.GridBounds;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridHitTesterTest {

    @Test
    void detectsBoundaryOnlyInsideMargin() {
        GridHitTester hitTester = new GridHitTester();
        GridBounds bounds = GridBounds.blank(2, 2);

        BoundaryHit hit = hitTester.boundaryAt(new Point(19, 10), bounds, 20, 4).orElseThrow();

        assertEquals(new CellCoordinate(1, 1), hit.coordinate());
        assertEquals(Direction.EAST, hit.direction());
        assertTrue(hitTester.boundaryAt(new Point(10, 10), bounds, 20, 4).isEmpty());
    }

    @Test
    void ignoresOuterBoundaryBecauseThereIsNoNeighborCell() {
        GridHitTester hitTester = new GridHitTester();
        GridBounds bounds = GridBounds.blank(2, 2);

        assertTrue(hitTester.boundaryAt(new Point(1, 10), bounds, 20, 4).isEmpty());
    }
}
