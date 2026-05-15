package org.mase.creator.model;

import java.util.Objects;
import java.util.Optional;

public record CellCoordinate(int x, int y) implements Comparable<CellCoordinate> {

    public CellCoordinate {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Cell coordinates must not be negative");
        }
    }

    public String iri() {
        return "</cells/" + x + "/" + y + ">";
    }

    public String path() {
        return "/cells/" + x + "/" + y;
    }

    public boolean isAdjacent(CellCoordinate other) {
        Objects.requireNonNull(other, "other");
        return Math.abs(x - other.x) + Math.abs(y - other.y) == 1;
    }

    public Optional<Direction> directionTo(CellCoordinate other) {
        return Direction.between(this, other);
    }

    public static Optional<CellCoordinate> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }

        String value = token.trim();
        if (value.startsWith("<") && value.endsWith(">")) {
            value = value.substring(1, value.length() - 1);
        }

        int cellsIndex = value.lastIndexOf("/cells/");
        if (cellsIndex < 0) {
            return Optional.empty();
        }

        String suffix = value.substring(cellsIndex + "/cells/".length());
        String[] parts = suffix.split("/");
        if (parts.length != 2 || !parts[0].matches("\\d+") || !parts[1].matches("\\d+")) {
            return Optional.empty();
        }

        return Optional.of(new CellCoordinate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
    }

    @Override
    public int compareTo(CellCoordinate other) {
        int byX = Integer.compare(x, other.x);
        if (byX != 0) {
            return byX;
        }
        return Integer.compare(y, other.y);
    }
}
