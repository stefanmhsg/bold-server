package org.mase.creator.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MazeCell {

    private final CellCoordinate coordinate;
    private final EnumMap<Direction, CellCoordinate> connections = new EnumMap<>(Direction.class);
    private final List<String> customStatements = new ArrayList<>();

    public MazeCell(CellCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    public CellCoordinate coordinate() {
        return coordinate;
    }

    public Optional<CellCoordinate> connection(Direction direction) {
        return Optional.ofNullable(connections.get(direction));
    }

    public Map<Direction, CellCoordinate> connections() {
        return Map.copyOf(connections);
    }

    public List<String> customStatements() {
        return List.copyOf(customStatements);
    }

    public void addCustomStatement(String statement) {
        customStatements.add(statement);
    }

    boolean connect(Direction direction, CellCoordinate target) {
        CellCoordinate existing = connections.put(direction, target);
        return !target.equals(existing);
    }

    boolean disconnect(Direction direction) {
        return connections.remove(direction) != null;
    }

    boolean disconnectTarget(CellCoordinate target) {
        boolean changed = false;
        for (Direction direction : Direction.values()) {
            if (target.equals(connections.get(direction))) {
                connections.remove(direction);
                changed = true;
            }
        }
        return changed;
    }
}
