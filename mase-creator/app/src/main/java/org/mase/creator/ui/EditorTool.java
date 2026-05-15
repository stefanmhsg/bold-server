package org.mase.creator.ui;

public enum EditorTool {
    DRAW_PATH("Path"),
    DRAW_OPTIMAL_ROUTE("Optimal Route"),
    DRAW_GREEN_ROUTE("maze:green"),
    DRAW_WALL("Wall"),
    DELETE_CELL("Erase"),
    PLACE_START("Start"),
    PLACE_EXIT("Exit");

    private final String label;

    EditorTool(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
