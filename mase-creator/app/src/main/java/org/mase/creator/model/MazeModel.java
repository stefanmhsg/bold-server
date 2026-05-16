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
    private final List<CellCoordinate> optimalRoute = new ArrayList<>();
    private final List<List<CellCoordinate>> greenRoutes = new ArrayList<>();
    private final List<String> preservedDocumentBlocks = new ArrayList<>();
    private final List<String> preservedCorrectPlanLines = new ArrayList<>();
    private CellCoordinate startCell;
    private String rawStartIri;
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

    public Optional<String> rawStartIri() {
        return Optional.ofNullable(rawStartIri);
    }

    public Optional<CellCoordinate> exitSourceCell() {
        return Optional.ofNullable(exitSourceCell);
    }

    public List<CellCoordinate> optimalRoute() {
        return List.copyOf(optimalRoute);
    }

    public List<String> preservedCorrectPlanLines() {
        return List.copyOf(preservedCorrectPlanLines);
    }

    public List<String> preservedDocumentBlocks() {
        return List.copyOf(preservedDocumentBlocks);
    }

    public boolean isOptimalRouteCell(CellCoordinate coordinate) {
        return optimalRoute.contains(coordinate);
    }

    public List<CellCoordinate> greenRoute() {
        return greenRoutes.stream()
                .findFirst()
                .map(List::copyOf)
                .orElse(List.of());
    }

    public List<List<CellCoordinate>> greenRoutes() {
        return greenRoutes.stream()
                .map(List::copyOf)
                .toList();
    }

    public boolean isGreenRouteCell(CellCoordinate coordinate) {
        return greenRoutes.stream().anyMatch(route -> route.contains(coordinate));
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

    public Optional<PathStroke> beginOptimalRoute(CellCoordinate coordinate) {
        Optional<PathStroke> stroke = beginExistingCellRoute(optimalRoute, coordinate);
        stroke.ifPresent(ignored -> preservedCorrectPlanLines.clear());
        return stroke;
    }

    public void continueOptimalRoute(PathStroke stroke, CellCoordinate target) {
        continueExistingCellRoute(optimalRoute, stroke, target);
    }

    public void clearOptimalRoute() {
        preservedCorrectPlanLines.clear();
        clearRoute(optimalRoute);
    }

    public Optional<PathStroke> beginGreenRoute(CellCoordinate coordinate) {
        if (!hasCell(coordinate)) {
            return Optional.empty();
        }

        boolean changed = greenRoutes.size() != 1 || !greenRoutes.get(0).equals(List.of(coordinate));
        greenRoutes.clear();
        greenRoutes.add(new ArrayList<>(List.of(coordinate)));
        notifyIfChanged(changed);
        return Optional.of(new PathStroke(coordinate, true));
    }

    public void continueGreenRoute(PathStroke stroke, CellCoordinate target) {
        if (greenRoutes.isEmpty()) {
            return;
        }
        continueExistingCellRoute(greenRoutes.get(0), stroke, target);
    }

    public void clearGreenRoute() {
        if (!greenRoutes.isEmpty()) {
            greenRoutes.clear();
            notifyChanged();
        }
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
        optimalRoute.remove(coordinate);
        removeCoordinateFromGreenRoutes(coordinate);

        notifyIfChanged(changed);
        return true;
    }

    public void placeStart(CellCoordinate coordinate) {
        if (!hasCell(coordinate)) {
            return;
        }
        if (!coordinate.equals(startCell)) {
            startCell = coordinate;
            rawStartIri = null;
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
        optimalRoute.removeIf(coordinate -> !bounds.contains(coordinate) || !hasCell(coordinate));
        splitGreenRoutesAround(coordinate -> !bounds.contains(coordinate));

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
            rawStartIri = null;
        }
    }

    public void setRawStartIriFromParser(String rawStartIri) {
        this.rawStartIri = rawStartIri;
    }

    public void setExitFromParser(CellCoordinate coordinate) {
        if (hasCell(coordinate)) {
            exitSourceCell = coordinate;
        }
    }

    public void setOptimalRouteFromParser(List<CellCoordinate> route) {
        setRouteFromParser(optimalRoute, route);
    }

    public void setPreservedCorrectPlanLinesFromParser(List<String> lines) {
        preservedCorrectPlanLines.clear();
        preservedCorrectPlanLines.addAll(lines);
    }

    public void setPreservedDocumentBlocksFromParser(List<String> blocks) {
        preservedDocumentBlocks.clear();
        preservedDocumentBlocks.addAll(blocks);
    }

    public void setGreenRouteFromParser(List<CellCoordinate> route) {
        setGreenRoutesFromParser(route.isEmpty() ? List.of() : List.of(route));
    }

    public void setGreenRoutesFromParser(List<List<CellCoordinate>> routes) {
        greenRoutes.clear();
        for (List<CellCoordinate> route : routes) {
            List<CellCoordinate> normalized = new ArrayList<>();
            for (CellCoordinate coordinate : route) {
                if (!normalized.isEmpty() && normalized.get(normalized.size() - 1).equals(coordinate)) {
                    continue;
                }
                normalized.add(coordinate);
            }
            if (!normalized.isEmpty() && hasCell(normalized.get(0))) {
                greenRoutes.add(normalized);
            }
        }
    }

    private boolean extendPathByOneCell(CellCoordinate current, CellCoordinate next) {
        boolean changed = ensureCell(next);
        changed |= connectAdjacent(current, next);
        return changed;
    }

    private Optional<PathStroke> beginExistingCellRoute(List<CellCoordinate> route, CellCoordinate coordinate) {
        if (!hasCell(coordinate)) {
            return Optional.empty();
        }

        boolean changed = !route.equals(List.of(coordinate));
        route.clear();
        route.add(coordinate);
        notifyIfChanged(changed);
        return Optional.of(new PathStroke(coordinate, true));
    }

    private void continueExistingCellRoute(List<CellCoordinate> route, PathStroke stroke, CellCoordinate target) {
        if (stroke.lastCoordinate().equals(target)) {
            return;
        }

        boolean changed = false;
        CellCoordinate current = stroke.lastCoordinate();

        while (current.x() != target.x()) {
            int step = Integer.compare(target.x(), current.x());
            CellCoordinate next = new CellCoordinate(current.x() + step, current.y());
            changed |= appendExistingCellRouteCell(route, next);
            current = next;
        }

        while (current.y() != target.y()) {
            int step = Integer.compare(target.y(), current.y());
            CellCoordinate next = new CellCoordinate(current.x(), current.y() + step);
            changed |= appendExistingCellRouteCell(route, next);
            current = next;
        }

        stroke.setLastCoordinate(current);
        notifyIfChanged(changed);
    }

    private boolean appendExistingCellRouteCell(List<CellCoordinate> route, CellCoordinate coordinate) {
        if (!hasCell(coordinate)) {
            return false;
        }

        if (!route.isEmpty() && route.get(route.size() - 1).equals(coordinate)) {
            return false;
        }

        int existingIndex = route.indexOf(coordinate);
        if (existingIndex >= 0) {
            if (existingIndex == route.size() - 1) {
                return false;
            }
            route.subList(existingIndex + 1, route.size()).clear();
            return true;
        }

        route.add(coordinate);
        return true;
    }

    private void clearRoute(List<CellCoordinate> route) {
        if (!route.isEmpty()) {
            route.clear();
            notifyChanged();
        }
    }

    private void setRouteFromParser(List<CellCoordinate> target, List<CellCoordinate> route) {
        target.clear();
        for (CellCoordinate coordinate : route) {
            if (hasCell(coordinate) && !target.contains(coordinate)) {
                target.add(coordinate);
            }
        }
    }

    private void removeCoordinateFromGreenRoutes(CellCoordinate coordinate) {
        splitGreenRoutesAround(coordinate::equals);
    }

    private void splitGreenRoutesAround(java.util.function.Predicate<CellCoordinate> shouldRemove) {
        List<List<CellCoordinate>> nextRoutes = new ArrayList<>();
        for (List<CellCoordinate> route : greenRoutes) {
            List<CellCoordinate> current = new ArrayList<>();
            for (CellCoordinate coordinate : route) {
                if (shouldRemove.test(coordinate)) {
                    if (!current.isEmpty()) {
                        nextRoutes.add(current);
                        current = new ArrayList<>();
                    }
                } else {
                    current.add(coordinate);
                }
            }
            if (!current.isEmpty()) {
                nextRoutes.add(current);
            }
        }
        greenRoutes.clear();
        greenRoutes.addAll(nextRoutes);
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
        optimalRoute.remove(coordinate);
        removeCoordinateFromGreenRoutes(coordinate);
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
