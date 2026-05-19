package org.mase.creator.ui;

public enum EditMode {
    DRAW("Draw", "Create or mark the selected maze content."),
    DELETE("Delete", "Remove the selected maze content.");

    private final String label;
    private final String tooltip;

    EditMode(String label, String tooltip) {
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
