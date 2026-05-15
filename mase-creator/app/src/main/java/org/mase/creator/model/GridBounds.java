package org.mase.creator.model;

public record GridBounds(int minX, int minY, int maxX, int maxY) {

    public GridBounds {
        if (minX < 0 || minY < 0) {
            throw new IllegalArgumentException("Grid bounds must not be negative");
        }
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("Grid max bounds must be greater than min bounds");
        }
    }

    public static GridBounds blank(int xCount, int yCount) {
        if (xCount < 1 || yCount < 1) {
            throw new IllegalArgumentException("Grid dimensions must be at least 1");
        }
        return new GridBounds(1, 1, xCount, yCount);
    }

    public GridBounds include(CellCoordinate coordinate) {
        return new GridBounds(
                Math.min(minX, coordinate.x()),
                Math.min(minY, coordinate.y()),
                Math.max(maxX, coordinate.x()),
                Math.max(maxY, coordinate.y())
        );
    }

    public GridBounds withCountsFromMinimum(int xCount, int yCount) {
        if (xCount < 1 || yCount < 1) {
            throw new IllegalArgumentException("Grid dimensions must be at least 1");
        }
        return new GridBounds(minX, minY, minX + xCount - 1, minY + yCount - 1);
    }

    public boolean contains(CellCoordinate coordinate) {
        return coordinate.x() >= minX && coordinate.x() <= maxX
                && coordinate.y() >= minY && coordinate.y() <= maxY;
    }

    public int xCount() {
        return maxX - minX + 1;
    }

    public int yCount() {
        return maxY - minY + 1;
    }
}
