package org.mase.creator.ui;

import org.junit.jupiter.api.Test;
import org.mase.creator.model.MazeModel;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeToolbarFactoryTest {

    @Test
    void groupsControlsByWorkflow() {
        JToolBar toolbar = createToolbar(new MazeEditorPanel(MazeModel.blank(4, 4)));

        assertTrue(indexOf(toolbar, "toolbar-group-file") < indexOf(toolbar, "toolbar-group-reset"));
        assertTrue(indexOf(toolbar, "toolbar-group-reset") < indexOf(toolbar, "toolbar-group-tools"));
        assertTrue(indexOf(toolbar, "toolbar-group-tools") < indexOf(toolbar, "toolbar-group-grid"));

        assertTrue(indexOf(toolbar, "toolbar-action-new") < indexOf(toolbar, "toolbar-action-open"));
        assertTrue(indexOf(toolbar, "toolbar-action-open") < indexOf(toolbar, "toolbar-action-restore-auto-save"));
        assertTrue(indexOf(toolbar, "toolbar-action-restore-auto-save") < indexOf(toolbar, "toolbar-action-create-maze"));

        assertTrue(indexOf(toolbar, "toolbar-action-erase-all") < indexOf(toolbar, "toolbar-action-clear-optimal"));
        assertTrue(indexOf(toolbar, "toolbar-action-clear-optimal") < indexOf(toolbar, "toolbar-action-clear-green"));
    }

    @Test
    void allToolbarButtonsAndGridControlsHaveTooltips() {
        JToolBar toolbar = createToolbar(new MazeEditorPanel(MazeModel.blank(4, 4)));

        for (java.awt.Component component : toolbar.getComponents()) {
            if (component instanceof AbstractButton button) {
                assertFalse(button.getToolTipText().isBlank(), button.getName());
                assertFalse(button.isFocusable(), button.getName());
            } else if (component instanceof JSpinner spinner) {
                assertFalse(spinner.getToolTipText().isBlank(), spinner.getName());
            }
        }
    }

    @Test
    void usesClearerToolLabels() {
        JToolBar toolbar = createToolbar(new MazeEditorPanel(MazeModel.blank(4, 4)));

        assertEquals("Draw Maze", findButton(toolbar, MazeToolbarFactory.toolButtonName(EditorTool.DRAW_PATH)).getText());
        assertEquals("Delete", findButton(toolbar, MazeToolbarFactory.toolButtonName(EditorTool.DELETE_CELL)).getText());
        assertEquals("Optimal", findButton(toolbar, MazeToolbarFactory.toolButtonName(EditorTool.DRAW_OPTIMAL_ROUTE)).getText());
    }

    @Test
    void actionButtonsDoNotChangeSelectedDrawingTool() {
        MazeEditorPanel editorPanel = new MazeEditorPanel(MazeModel.blank(4, 4));
        JToolBar toolbar = createToolbar(editorPanel);

        JToggleButton wallButton = (JToggleButton) findButton(toolbar, MazeToolbarFactory.toolButtonName(EditorTool.DRAW_WALL));
        wallButton.doClick();
        assertEquals(EditorTool.DRAW_WALL, editorPanel.tool());
        assertTrue(wallButton.isSelected());

        findButton(toolbar, "toolbar-action-erase-all").doClick();
        findButton(toolbar, "toolbar-action-new").doClick();

        assertEquals(EditorTool.DRAW_WALL, editorPanel.tool());
        assertTrue(wallButton.isSelected());
    }

    private JToolBar createToolbar(MazeEditorPanel editorPanel) {
        return MazeToolbarFactory.create(
                new MazeToolbarFactory.Actions(
                        () -> {
                        },
                        () -> {
                        },
                        () -> {
                        },
                        () -> {
                        },
                        () -> {
                        },
                        () -> {
                        },
                        () -> {
                        }
                ),
                editorPanel,
                new JSpinner(new SpinnerNumberModel(24, 1, 999, 1)),
                new JSpinner(new SpinnerNumberModel(24, 1, 999, 1))
        );
    }

    private int indexOf(JToolBar toolbar, String name) {
        for (int i = 0; i < toolbar.getComponentCount(); i++) {
            if (name.equals(toolbar.getComponent(i).getName())) {
                return i;
            }
        }
        throw new AssertionError("Missing toolbar component: " + name);
    }

    private AbstractButton findButton(JToolBar toolbar, String name) {
        java.awt.Component component = findComponent(toolbar, name);
        assertTrue(component instanceof AbstractButton, name);
        return (AbstractButton) component;
    }

    private java.awt.Component findComponent(JToolBar toolbar, String name) {
        for (java.awt.Component component : toolbar.getComponents()) {
            if (component instanceof JComponent jComponent && name.equals(jComponent.getName())) {
                return component;
            }
        }
        throw new AssertionError("Missing toolbar component: " + name);
    }
}
