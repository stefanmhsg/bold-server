package org.mase.creator.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum Direction {
    NORTH(-1, 0, "maze:north"),
    WEST(0, -1, "maze:west"),
    SOUTH(1, 0, "maze:south"),
    EAST(0, 1, "maze:east");

    public static final List<Direction> SERIALIZATION_ORDER = List.of(NORTH, WEST, SOUTH, EAST);

    private final int deltaX;
    private final int deltaY;
    private final String predicate;

    Direction(int deltaX, int deltaY, String predicate) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.predicate = predicate;
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaY() {
        return deltaY;
    }

    public String predicate() {
        return predicate;
    }

    public CellCoordinate move(CellCoordinate coordinate) {
        return new CellCoordinate(coordinate.x() + deltaX, coordinate.y() + deltaY);
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public static Optional<Direction> between(CellCoordinate from, CellCoordinate to) {
        return Arrays.stream(values())
                .filter(direction -> from.x() + direction.deltaX == to.x()
                        && from.y() + direction.deltaY == to.y())
                .findFirst();
    }
}
