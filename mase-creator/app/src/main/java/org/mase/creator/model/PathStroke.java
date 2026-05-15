package org.mase.creator.model;

public final class PathStroke {

    private CellCoordinate lastCoordinate;
    private final boolean startedInsideExistingCell;

    PathStroke(CellCoordinate lastCoordinate, boolean startedInsideExistingCell) {
        this.lastCoordinate = lastCoordinate;
        this.startedInsideExistingCell = startedInsideExistingCell;
    }

    public CellCoordinate lastCoordinate() {
        return lastCoordinate;
    }

    public boolean startedInsideExistingCell() {
        return startedInsideExistingCell;
    }

    void setLastCoordinate(CellCoordinate lastCoordinate) {
        this.lastCoordinate = lastCoordinate;
    }
}
