package org.mase.creator.ui;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;

public record BoundaryHit(CellCoordinate coordinate, Direction direction) {
}
