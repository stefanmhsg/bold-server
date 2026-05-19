package org.mase.creator.ui;

public enum EditorTool {
    CELL("Cell", "Edit maze cells and path connections."),
    WALL("Wall", "Edit walls between adjacent cells."),
    START("Start", "Edit the xhv:start marker."),
    EXIT("Exit", "Edit the maze:exit marker."),
    OPTIMAL_ROUTE("Optimal", "Edit the purple #Correct plan route."),
    GREEN_ROUTE("maze:green", "Edit maze:green successor predicates.");

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
