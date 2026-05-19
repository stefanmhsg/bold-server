package org.mase.creator.ui;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.Dimension;

final class MazeToolbarFactory {

    private MazeToolbarFactory() {
    }

    static JToolBar create(
            Actions actions,
            MazeEditorPanel editorPanel,
            JSpinner xSpinner,
            JSpinner ySpinner
    ) {
        JToolBar toolbar = new JToolBar();
        toolbar.setName("maze-toolbar");
        toolbar.setFloatable(false);

        addGroupLabel(toolbar, "File", "toolbar-group-file");
        toolbar.add(actionButton("New", "toolbar-action-new", "Start a blank maze with the current grid size.", actions.newMaze()));
        toolbar.add(actionButton("Open", "toolbar-action-open", "Load an existing .trig maze file for editing.", actions.open()));
        toolbar.add(actionButton("Restore Auto-Save", "toolbar-action-restore-auto-save", "Restore the latest editor auto-save snapshot.", actions.restoreAutoSave()));
        toolbar.add(actionButton("Create Package", "toolbar-action-create-package", "Export a server-ready scenario package to the editor output directory.", actions.createPackage()));

        addGroupSeparator(toolbar);
        addGroupLabel(toolbar, "Reset", "toolbar-group-reset");
        toolbar.add(actionButton("Erase All", "toolbar-action-erase-all", "Clear the canvas while keeping the current grid size.", actions.eraseAll()));
        toolbar.add(actionButton("Clear Optimal", "toolbar-action-clear-optimal", "Remove the purple #Correct plan route from output.", actions.clearOptimalRoute()));
        toolbar.add(actionButton("Clear maze:green", "toolbar-action-clear-green", "Remove maze:green route predicates from output.", actions.clearGreenRoute()));

        addGroupSeparator(toolbar);
        addGroupLabel(toolbar, "Mode", "toolbar-group-mode");
        ButtonGroup modes = new ButtonGroup();
        for (EditMode mode : EditMode.values()) {
            JToggleButton button = new JToggleButton(mode.label());
            button.setName(modeButtonName(mode));
            button.setToolTipText(mode.tooltip());
            button.setFocusable(false);
            button.addActionListener(event -> editorPanel.setEditMode(mode));
            modes.add(button);
            toolbar.add(button);
            if (mode == EditMode.DRAW) {
                button.setSelected(true);
            }
        }

        addGroupSeparator(toolbar);
        addGroupLabel(toolbar, "Target", "toolbar-group-target");
        ButtonGroup tools = new ButtonGroup();
        for (EditorTool tool : EditorTool.values()) {
            JToggleButton button = new JToggleButton(tool.label());
            button.setName(toolButtonName(tool));
            button.setToolTipText(tool.tooltip());
            button.setFocusable(false);
            button.addActionListener(event -> editorPanel.setTool(tool));
            tools.add(button);
            toolbar.add(button);
            if (tool == EditorTool.CELL) {
                button.setSelected(true);
            }
        }

        addGroupSeparator(toolbar);
        addGroupLabel(toolbar, "Grid", "toolbar-group-grid");
        toolbar.add(axisLabel("X", "toolbar-grid-x-label", "Number of rows on the X axis."));
        xSpinner.setName("toolbar-grid-x");
        xSpinner.setToolTipText("Set the visible row count for the editor grid.");
        toolbar.add(xSpinner);
        toolbar.add(axisLabel("Y", "toolbar-grid-y-label", "Number of columns on the Y axis."));
        ySpinner.setName("toolbar-grid-y");
        ySpinner.setToolTipText("Set the visible column count for the editor grid.");
        toolbar.add(ySpinner);

        return toolbar;
    }

    static String toolButtonName(EditorTool tool) {
        return "toolbar-tool-" + tool.name();
    }

    static String modeButtonName(EditMode mode) {
        return "toolbar-mode-" + mode.name();
    }

    private static JButton actionButton(String label, String name, String tooltip, Runnable action) {
        JButton button = new JButton(label);
        button.setName(name);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JLabel axisLabel(String label, String name, String tooltip) {
        JLabel axisLabel = new JLabel(label);
        axisLabel.setName(name);
        axisLabel.setToolTipText(tooltip);
        return axisLabel;
    }

    private static void addGroupLabel(JToolBar toolbar, String label, String name) {
        JLabel groupLabel = new JLabel(label);
        groupLabel.setName(name);
        groupLabel.setToolTipText(label + " controls");
        toolbar.add(groupLabel);
    }

    private static void addGroupSeparator(JToolBar toolbar) {
        toolbar.addSeparator(new Dimension(14, 0));
    }

    record Actions(
            Runnable newMaze,
            Runnable open,
            Runnable restoreAutoSave,
            Runnable createPackage,
            Runnable eraseAll,
            Runnable clearOptimalRoute,
            Runnable clearGreenRoute
    ) {
    }
}
