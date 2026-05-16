package org.mase.creator.ui;

public enum EditorTool {
    DRAW_PATH("Draw Maze", "Draw maze cells and open connections while dragging."),
    DRAW_OPTIMAL_ROUTE("Optimal", "Draw the purple #Correct plan comment route across existing cells."),
    DRAW_GREEN_ROUTE("maze:green", "Draw maze:green successor predicates across existing cells."),
    DRAW_WALL("Wall", "Click near a cell boundary to turn a connection into a wall."),
    DELETE_CELL("Delete", "Erase cells from the maze canvas."),
    PLACE_START("Start", "Mark an existing cell as xhv:start."),
    PLACE_EXIT("Exit", "Mark an existing cell with maze:exit.");

    private final String label;
    private final String tooltip;

    EditorTool(String label, String tooltip) {
        this.label = label;
        this.tooltip = tooltip;
    }

    public String label() {
        return label;
    }

    public String tooltip() {
        return tooltip;
    }
}
