package org.mase.creator.ui;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.GridBounds;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class GridHitTester {

    public Optional<CellCoordinate> cellAt(Point point, GridBounds bounds, int cellSize) {
        if (point.x < 0 || point.y < 0) {
            return Optional.empty();
        }

        int column = point.x / cellSize;
        int row = point.y / cellSize;
        if (row >= bounds.xCount() || column >= bounds.yCount()) {
            return Optional.empty();
        }

        return Optional.of(new CellCoordinate(bounds.minX() + row, bounds.minY() + column));
    }

    public Optional<BoundaryHit> boundaryAt(Point point, GridBounds bounds, int cellSize, int margin) {
        Optional<CellCoordinate> coordinate = cellAt(point, bounds, cellSize);
        if (coordinate.isEmpty()) {
            return Optional.empty();
        }

        int localX = Math.floorMod(point.x, cellSize);
        int localY = Math.floorMod(point.y, cellSize);
        List<BoundaryCandidate> candidates = List.of(
                new BoundaryCandidate(Direction.NORTH, localY),
                new BoundaryCandidate(Direction.WEST, localX),
                new BoundaryCandidate(Direction.SOUTH, cellSize - 1 - localY),
                new BoundaryCandidate(Direction.EAST, cellSize - 1 - localX)
        );

        return candidates.stream()
                .filter(candidate -> candidate.distance() <= margin)
                .min(Comparator.comparingInt(BoundaryCandidate::distance))
                .filter(candidate -> neighborIsInsideBounds(coordinate.get(), candidate.direction(), bounds))
                .map(candidate -> new BoundaryHit(coordinate.get(), candidate.direction()));
    }

    private boolean neighborIsInsideBounds(CellCoordinate coordinate, Direction direction, GridBounds bounds) {
        int x = coordinate.x() + direction.deltaX();
        int y = coordinate.y() + direction.deltaY();
        if (x < 0 || y < 0) {
            return false;
        }
        return bounds.contains(new CellCoordinate(x, y));
    }

    private record BoundaryCandidate(Direction direction, int distance) {
    }
}
