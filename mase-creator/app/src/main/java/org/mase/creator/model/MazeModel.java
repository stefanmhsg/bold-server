package org.mase.creator.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

public final class MazeModel {

    private GridBounds bounds;
    private final NavigableMap<CellCoordinate, MazeCell> cells = new TreeMap<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private CellCoordinate startCell;
    private CellCoordinate exitSourceCell;

    public MazeModel(GridBounds bounds) {
        this.bounds = bounds;
    }

    public static MazeModel blank(int xCount, int yCount) {
        return new MazeModel(GridBounds.blank(xCount, yCount));
    }

    public GridBounds bounds() {
        return bounds;
    }

    public int xCount() {
        return bounds.xCount();
    }

    public int yCount() {
        return bounds.yCount();
    }

    public Collection<MazeCell> cells() {
        return List.copyOf(cells.values());
    }

    public Optional<MazeCell> cell(CellCoordinate coordinate) {
        return Optional.ofNullable(cells.get(coordinate));
    }

    public boolean hasCell(CellCoordinate coordinate) {
        return cells.containsKey(coordinate);
    }

    public Optional<CellCoordinate> startCell() {
        return Optional.ofNullable(startCell);
    }

    public Optional<CellCoordinate> exitSourceCell() {
        return Optional.ofNullable(exitSourceCell);
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    public PathStroke beginPath(CellCoordinate coordinate) {
        boolean existed = hasCell(coordinate);
        boolean changed = ensureCell(coordinate);
        notifyIfChanged(changed);
        return new PathStroke(coordinate, existed);
    }

    public void continuePath(PathStroke stroke, CellCoordinate target) {
        if (stroke.lastCoordinate().equals(target)) {
            return;
        }

        boolean changed = false;
        CellCoordinate current = stroke.lastCoordinate();

        while (current.x() != target.x()) {
            int step = Integer.compare(target.x(), current.x());
            CellCoordinate next = new CellCoordinate(current.x() + step, current.y());
            changed |= extendPathByOneCell(current, next);
            current = next;
        }

        while (current.y() != target.y()) {
            int step = Integer.compare(target.y(), current.y());
            CellCoordinate next = new CellCoordinate(current.x(), current.y() + step);
            changed |= extendPathByOneCell(current, next);
            current = next;
        }

        stroke.setLastCoordinate(current);
        notifyIfChanged(changed);
    }

    public boolean createCell(CellCoordinate coordinate) {
        boolean changed = ensureCell(coordinate);
        notifyIfChanged(changed);
        return changed;
    }

    public boolean connectIfAdjacent(CellCoordinate first, CellCoordinate second) {
        boolean changed = connectAdjacent(first, second);
        notifyIfChanged(changed);
        return changed;
    }

    public boolean drawWall(CellCoordinate coordinate, Direction direction) {
        boolean changed = disconnect(coordinate, direction);
        notifyIfChanged(changed);
        return changed;
    }

    public boolean deleteCell(CellCoordinate coordinate) {
        MazeCell removed = cells.remove(coordinate);
        if (removed == null) {
            return false;
        }

        boolean changed = true;
        for (MazeCell cell : cells.values()) {
            changed |= cell.disconnectTarget(coordinate);
        }

        if (coordinate.equals(startCell)) {
            startCell = null;
        }
        if (coordinate.equals(exitSourceCell)) {
            exitSourceCell = null;
        }

        notifyIfChanged(changed);
        return true;
    }

    public void placeStart(CellCoordinate coordinate) {
        if (!hasCell(coordinate)) {
            return;
        }
        if (!coordinate.equals(startCell)) {
            startCell = coordinate;
            notifyChanged();
        }
    }

    public void placeExit(CellCoordinate coordinate) {
        if (!hasCell(coordinate)) {
            return;
        }
        if (!coordinate.equals(exitSourceCell)) {
            exitSourceCell = coordinate;
            notifyChanged();
        }
    }

    public void setBoundsByCellCounts(int xCount, int yCount) {
        GridBounds nextBounds = bounds.withCountsFromMinimum(xCount, yCount);
        if (nextBounds.equals(bounds)) {
            return;
        }

        bounds = nextBounds;
        List<CellCoordinate> outside = cells.keySet().stream()
                .filter(coordinate -> !bounds.contains(coordinate))
                .toList();
        outside.forEach(this::deleteCellWithoutNotification);

        if (startCell != null && !bounds.contains(startCell)) {
            startCell = null;
        }
        if (exitSourceCell != null && !bounds.contains(exitSourceCell)) {
            exitSourceCell = null;
        }

        notifyChanged();
    }

    public void setStartFromParser(CellCoordinate coordinate) {
        if (hasCell(coordinate)) {
            startCell = coordinate;
        }
    }

    public void setExitFromParser(CellCoordinate coordinate) {
        if (hasCell(coordinate)) {
            exitSourceCell = coordinate;
        }
    }

    private boolean extendPathByOneCell(CellCoordinate current, CellCoordinate next) {
        boolean changed = ensureCell(next);
        changed |= connectAdjacent(current, next);
        return changed;
    }

    private boolean ensureCell(CellCoordinate coordinate) {
        bounds = bounds.include(coordinate);
        if (cells.containsKey(coordinate)) {
            return false;
        }
        cells.put(coordinate, new MazeCell(coordinate));
        return true;
    }

    private boolean connectAdjacent(CellCoordinate first, CellCoordinate second) {
        if (!hasCell(first) || !hasCell(second) || !first.isAdjacent(second)) {
            return false;
        }

        Direction firstDirection = first.directionTo(second).orElseThrow();
        Direction secondDirection = firstDirection.opposite();
        boolean changed = cells.get(first).connect(firstDirection, second);
        changed |= cells.get(second).connect(secondDirection, first);
        return changed;
    }

    private boolean disconnect(CellCoordinate coordinate, Direction direction) {
        MazeCell cell = cells.get(coordinate);
        if (cell == null) {
            return false;
        }

        boolean changed = false;
        Optional<CellCoordinate> target = cell.connection(direction);
        changed |= cell.disconnect(direction);
        if (target.isPresent()) {
            MazeCell targetCell = cells.get(target.get());
            if (targetCell != null) {
                changed |= targetCell.disconnect(direction.opposite());
            }
        } else {
            int neighborX = coordinate.x() + direction.deltaX();
            int neighborY = coordinate.y() + direction.deltaY();
            if (neighborX >= 0 && neighborY >= 0) {
                CellCoordinate neighbor = new CellCoordinate(neighborX, neighborY);
                MazeCell neighborCell = cells.get(neighbor);
                if (neighborCell != null) {
                    changed |= neighborCell.disconnect(direction.opposite());
                }
            }
        }
        return changed;
    }

    private void deleteCellWithoutNotification(CellCoordinate coordinate) {
        cells.remove(coordinate);
        for (MazeCell cell : cells.values()) {
            cell.disconnectTarget(coordinate);
        }
    }

    private void notifyIfChanged(boolean changed) {
        if (changed) {
            notifyChanged();
        }
    }

    private void notifyChanged() {
        List.copyOf(changeListeners).forEach(Runnable::run);
    }
}
