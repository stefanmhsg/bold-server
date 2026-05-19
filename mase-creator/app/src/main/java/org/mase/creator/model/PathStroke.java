package org.mase.creator.model;

public final class PathStroke {

    private CellCoordinate lastCoordinate;
    private final boolean startedInsideExistingCell;
    private final int routeIndex;

    PathStroke(CellCoordinate lastCoordinate, boolean startedInsideExistingCell) {
        this(lastCoordinate, startedInsideExistingCell, -1);
    }

    PathStroke(CellCoordinate lastCoordinate, boolean startedInsideExistingCell, int routeIndex) {
        this.lastCoordinate = lastCoordinate;
        this.startedInsideExistingCell = startedInsideExistingCell;
        this.routeIndex = routeIndex;
    }

    public CellCoordinate lastCoordinate() {
        return lastCoordinate;
    }

    public boolean startedInsideExistingCell() {
        return startedInsideExistingCell;
    }

    int routeIndex() {
        return routeIndex;
    }

    void setLastCoordinate(CellCoordinate lastCoordinate) {
        this.lastCoordinate = lastCoordinate;
    }
}
